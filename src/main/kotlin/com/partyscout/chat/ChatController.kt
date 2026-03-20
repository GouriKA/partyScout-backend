package com.partyscout.chat

import com.partyscout.venue.dto.Place
import com.partyscout.venue.service.GooglePlacesService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors

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

        emitter.onTimeout {
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
                    history = cleanHistory,
                    emitter = emitter,
                )
            } catch (e: Exception) {
                logger.error("Chat request failed: {}", e.message)
                try {
                    emitter.send("Something went wrong. Please try again.")
                    emitter.complete()
                } catch (_: Exception) {}
            }
        }

        return emitter
    }

    // ── History sanitization ─────────────────────────────────────────────────

    /**
     * Strip [VENUES] JSON blobs from history before sending to Claude.
     * Keeps conversation readable without leaking large JSON payloads.
     */
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
     * Adapter: ChatIntent → List<Place>
     *
     * Mismatch with spec: the spec pseudocode calls `placesService.search(intent)`,
     * but no such method exists. The existing search logic is embedded inside
     * PartySearchController (geocode + parallel text search + dedup + scoring).
     * Rather than duplicate or restructure that logic, this adapter calls the
     * two underlying GooglePlacesService methods directly:
     *   1. geocodeCity(city) → Location
     *   2. searchText(query, location, radius) → List<Place>
     *
     * Return type is List<Place> (raw Google Places objects), not List<EnhancedVenue>.
     * The chat context only needs name/address/rating/links, so this is sufficient.
     */
    private fun searchVenues(intent: ChatIntent): List<Place> {
        val city = intent.city ?: return emptyList()
        return try {
            val location = googlePlacesService.geocodeCity(city).block()
                ?: return emptyList()

            val query = buildSearchQuery(intent)
            val radiusMeters = 16_000 // ~10 miles

            googlePlacesService.searchText(query, location, radiusMeters)
                .block()?.places ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Venue search failed for city={}: {}", city, e.message)
            emptyList()
        }
    }

    /**
     * Build a free-text search query from ChatIntent fields.
     * Themes are most specific; occasion and persona provide context.
     */
    private fun buildSearchQuery(intent: ChatIntent): String {
        return buildList {
            addAll(intent.themes)
            add(intent.occasion ?: "birthday party")
            intent.persona?.lowercase()?.let { add(it) }
        }.joinToString(" ")
    }
}
