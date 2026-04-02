package com.partyscout.chat

import com.partyscout.persona.PersonaService
import com.partyscout.venue.dto.Place
import com.partyscout.venue.service.GooglePlacesService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@RestController
class ChatController(
    private val anthropicService: AnthropicService,
    private val googlePlacesService: GooglePlacesService,
    private val personaService: PersonaService,
) {
    private val logger = LoggerFactory.getLogger(ChatController::class.java)
    private val executor = Executors.newCachedThreadPool()

    @PostMapping("/api/chat")
    fun chat(@RequestBody request: ChatRequest): SseEmitter {
        val emitter = SseEmitter(120_000L)

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

                val venues: List<Place> = if (mergedIntent.readyToSearch &&
                    mergedIntent.city != null &&
                    (request.knownVenues.isEmpty() || isRequestingNewVenues(request.message))) {
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

    // ── Follow-up detection ──────────────────────────────────────────────────

    /**
     * Returns true only when the user explicitly asks for new or different venues.
     * Used to suppress a repeat venue search when the user is asking follow-up
     * questions about venues they have already been shown.
     */
    private fun isRequestingNewVenues(message: String): Boolean {
        val lc = message.lowercase()
        return listOf(
            "more venues", "different venues", "other venues", "other options",
            "find more", "show more", "other places", "different places",
            "other suggestions", "more options", "new venues", "search again"
        ).any { lc.contains(it) }
    }

    // ── Venue search ─────────────────────────────────────────────────────────

    /**
     * Runs multiple queries in parallel (same strategy as the wizard endpoint)
     * so chat results are as comprehensive as the wizard flow.
     * Falls back to a broad event-space query on empty results.
     */
    private fun searchVenues(intent: ChatIntent): List<Place> {
        val city = intent.city ?: return emptyList()
        return try {
            val location = googlePlacesService.geocodeCity(city).block() ?: return emptyList()
            val radius = 16_000

            val queries = buildSearchQueries(intent)
            val allPlaces: List<Place> = Flux.fromIterable(queries)
                .flatMap({ query ->
                    googlePlacesService.searchText(query, location, radius)
                        .map { it.places ?: emptyList() }
                        .onErrorResume { err ->
                            logger.warn("Chat search failed for '{}': {}", query, err.message)
                            Mono.just(emptyList())
                        }
                }, 5)
                .flatMapIterable { it }
                .collectList()
                .block()
                ?.distinctBy { it.id }
                ?: emptyList()

            if (allPlaces.isNotEmpty()) return allPlaces.take(20)

            // Fallback: broader query, wider radius
            logger.info("All queries returned empty for city={}, trying fallback", city)
            googlePlacesService.searchText(buildFallbackQuery(intent), location, 32_000)
                .block()?.places.orEmpty().take(20)
        } catch (e: Exception) {
            logger.warn("Venue search failed for city={}: {}", city, e.message)
            emptyList()
        }
    }

    /**
     * Builds search queries from intent.
     * When outdoor is requested, uses outdoor-specific venue queries instead of
     * activity-based persona queries (which return indoor venues regardless of prefix).
     * When indoor/unspecified, uses persona queries for comprehensive coverage.
     */
    private fun buildSearchQueries(intent: ChatIntent): List<String> {
        val occasion = intent.occasion ?: "birthday party"

        // Outdoor: use venue-type queries that actually return parks/farms/gardens
        if (intent.indoor == false) {
            return buildList {
                add("outdoor $occasion venue")
                add("park $occasion")
                add("farm party venue")
                add("outdoor event space")
                add("garden party venue")
                if (intent.themes.isNotEmpty()) add("outdoor ${intent.themes.first()} party")
            }
        }

        // Indoor or unspecified: use persona-based queries for broad coverage
        val personaQueries: List<String> = when {
            intent.age != null -> personaService.getSearchQueries(intent.age).take(10)
            intent.persona != null -> {
                val approxAge = personaToApproxAge(intent.persona)
                if (approxAge != null) personaService.getSearchQueries(approxAge).take(10) else emptyList()
            }
            else -> emptyList()
        }

        val intentQueries = buildList {
            add("$occasion venue event space")
            if (intent.themes.isNotEmpty()) add("${intent.themes.joinToString(" ")} $occasion")
            if (intent.groupSize != null && intent.groupSize > 50) add("large event venue $occasion")
        }

        return (intentQueries + personaQueries).distinct().take(12)
    }

    private fun buildFallbackQuery(intent: ChatIntent): String {
        val setting = when (intent.indoor) {
            false -> "outdoor "
            true  -> "indoor "
            null  -> ""
        }
        return "${setting}party venue event space ${intent.occasion ?: "birthday"}"
    }

    private fun personaToApproxAge(persona: String): Int? = when (persona.lowercase()) {
        "baby", "toddler", "baby & toddler"   -> 1
        "preschool", "preschooler"             -> 4
        "kids", "children", "kid"              -> 7
        "tweens", "tween"                      -> 11
        "early teens"                          -> 14
        "teens", "teen", "teenager"            -> 16
        "young adults", "young adult"          -> 20
        "adults", "adult"                      -> 30
        else                                   -> null
    }
}
