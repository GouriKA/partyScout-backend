package com.partyscout.chat

import com.partyscout.venue.dto.Place
import com.partyscout.venue.service.GooglePlacesService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@RestController
class ChatController(
    private val anthropicService: AnthropicService,
    private val googlePlacesService: GooglePlacesService,
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    private val executor = Executors.newCachedThreadPool()

    @PostMapping("/api/chat")
    fun chat(@RequestBody request: ChatRequest): SseEmitter {
        val emitter = SseEmitter(120_000L)

        // Fix #1 — SSE connection drop: set cancelled on any disconnect so the
        // background thread stops writing to the dead socket immediately.
        val cancelled = AtomicBoolean(false)
        emitter.onCompletion { cancelled.set(true) }
        emitter.onError { _ -> cancelled.set(true) }
        emitter.onTimeout {
            cancelled.set(true)
            try {
                emitter.send("Sorry, that took too long. Please try again.")
                emitter.complete()
            } catch (_: Exception) {}
        }

        executor.submit {
            try {
                val cleanHistory = sanitizeHistory(request.conversationHistory.takeLast(10))

                val intent = anthropicService.extractIntent(
                    message = request.message,
                    history = cleanHistory,
                )

                val mergedIntent = intent.copy(
                    city = intent.city ?: request.existingContext.city,
                    persona = intent.persona ?: request.existingContext.persona,
                    occasion = intent.occasion ?: request.existingContext.occasion,
                )

                val venues: List<Place> = if (mergedIntent.readyToSearch && mergedIntent.city != null) {
                    searchVenues(mergedIntent)
                } else {
                    emptyList()
                }

                anthropicService.streamResponse(
                    userMessage = request.message,
                    intent = mergedIntent,
                    venues = venues,
                    knownVenues = request.knownVenues,
                    history = cleanHistory,
                    emitter = emitter,
                    cancelled = cancelled,
                )
            } catch (e: Exception) {
                logger.error("Chat request failed: {}", e.message)
                if (!cancelled.get()) {
                    try {
                        emitter.send("Something went wrong. Please try again.")
                        emitter.complete()
                    } catch (_: Exception) {}
                }
            }
        }

        return emitter
    }

    // ── History sanitization ─────────────────────────────────────────────────

    fun sanitizeHistory(history: List<ChatMessage>): List<ChatMessage> {
        return history.map { msg ->
            msg.copy(
                content = msg.content
                    .replace(Regex("\\[VENUES\\].*"), "[venue results shown]")
                    .trim()
            )
        }
    }

    // ── Venue search adapter ─────────────────────────────────────────────────

    /**
     * Fix #3 — Empty Places results: if the intent-specific query returns nothing
     * (common in less-covered cities), retry with a generic "birthday party venue
     * event space" query and a wider 32 km radius before giving up.
     *
     * Mismatch note: spec calls placesService.search(intent) which doesn't exist.
     * Bridges ChatIntent → GooglePlacesService via geocodeCity + searchText.
     */
    private fun searchVenues(intent: ChatIntent): List<Place> {
        val city = intent.city ?: return emptyList()
        return try {
            val location = googlePlacesService.geocodeCity(city).block()
                ?: return emptyList()

            val primaryQuery = buildSearchQuery(intent)
            val primary = googlePlacesService.searchText(primaryQuery, location, 16_000)
                .block()?.places.orEmpty()

            if (primary.isNotEmpty()) return primary

            // Fallback for less-covered cities: broader query, wider radius
            logger.info("Primary search empty for city={}, trying fallback query", city)
            googlePlacesService.searchText("birthday party venue event space", location, 32_000)
                .block()?.places.orEmpty()
        } catch (e: Exception) {
            logger.warn("Venue search failed for city={}: {}", city, e.message)
            emptyList()
        }
    }

    private fun buildSearchQuery(intent: ChatIntent): String {
        return buildList {
            addAll(intent.themes)
            add(intent.occasion ?: "birthday party")
            intent.persona?.lowercase()?.let { add(it) }
        }.joinToString(" ")
    }
}
