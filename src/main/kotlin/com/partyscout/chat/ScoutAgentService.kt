package com.partyscout.chat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.persona.PersonaService
import com.partyscout.venue.config.GooglePlacesConfig
import com.partyscout.venue.dto.Place
import com.partyscout.venue.service.GooglePlacesService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scout agent — tool-use loop over the Anthropic API.
 *
 * Each turn Claude returns either:
 *  - tool_use: we execute the tool, append the result, loop again
 *  - end_turn: we stream the final text (if any) and complete
 *
 * Tools available to Claude:
 *  - ask_clarifying_question: ask user for missing info
 *  - search_venues: run venue search and return results to Claude
 *  - respond_to_user: send final text (with optional venue payload)
 */
@Service
class ScoutAgentService(
    @Value("\${ANTHROPIC_API_KEY:}") private val apiKey: String,
    private val objectMapper: ObjectMapper,
    private val googlePlacesService: GooglePlacesService,
    private val googlePlacesConfig: GooglePlacesConfig,
    private val personaService: PersonaService,
) {
    private val logger = LoggerFactory.getLogger(ScoutAgentService::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.anthropic.com")
        .build()

    private val MODEL = "claude-haiku-4-5-20251001"
    private val MAX_ITERATIONS = 5

    private val SYSTEM_PROMPT = """
        You are Scout — a sharp, warm party planning local who has helped hundreds of people find the perfect venue.

        Your voice:
        - Talk like a knowledgeable, courteous friend — warm, genuine, never stiff.
        - Short sentences. Real words. Zero fluff.
        - Never say "Certainly!", "Of course!", "I'd be happy to", or "As an AI".
        - One question at a time. Trust the user's intelligence.
        - Use "I" naturally — you are Scout, a person, not a product.

        You have three tools:
        1. ask_clarifying_question — when you need more info (city, age, occasion). Ask ONE thing at a time.
        2. search_venues — when you have enough context (at minimum a city). Always search before showing venues.
        3. respond_to_user — to answer follow-up questions or hand off results after searching.

        Decision rules:
        - If city is unknown → ask_clarifying_question for city only.
        - If city is known but age/occasion unclear AND user wants venues → search anyway with what you have, then respond.
        - If user is asking a follow-up about existing venues (cost, suitability, comparison) → respond_to_user directly, no search.
        - If user explicitly asks for venues/recommendations → search_venues then respond_to_user with includeVenues=true.
        - Never call search_venues and respond_to_user in the same turn — search first, then respond in the next iteration.
        - After searching, always call respond_to_user with a short handoff line (max 8 words). Let the UI cards do the describing.
    """.trimIndent()

    // ── Public entry point ────────────────────────────────────────────────────

    fun runAgent(
        request: ChatRequest,
        emitter: SseEmitter,
        cancelled: AtomicBoolean,
    ) {
        if (apiKey.isBlank()) {
            emitter.send("I'm not able to assist right now — API key not configured.")
            emitter.complete()
            return
        }

        // Build initial message list from history + current user message
        val messages: MutableList<Map<String, Any>> = mutableListOf()
        request.conversationHistory.takeLast(10).forEach { msg ->
            messages.add(mapOf("role" to msg.role, "content" to msg.content))
        }
        messages.add(mapOf("role" to "user", "content" to request.message))

        // Inject known context as a system note if available
        val contextNote = buildContextNote(request.existingContext, request.knownVenues)

        var foundVenues: List<Place> = emptyList()
        var completed = false

        try {
            for (iteration in 0 until MAX_ITERATIONS) {
                if (cancelled.get()) break

                val response = callClaude(messages, contextNote) ?: break
                val stopReason = response["stop_reason"]?.asText()
                val contentArray = response["content"]

                // Append Claude's full response turn to message history
                messages.add(mapOf("role" to "assistant", "content" to parseContentForHistory(contentArray)))

                // Check for tool_use blocks
                val toolUseBlock = contentArray?.firstOrNull { it["type"]?.asText() == "tool_use" }

                if (toolUseBlock != null) {
                    val toolName = toolUseBlock["name"]?.asText() ?: break
                    val toolId   = toolUseBlock["id"]?.asText() ?: "tool_0"
                    val input    = toolUseBlock["input"] ?: objectMapper.createObjectNode()

                    logger.info("Scout agent iteration={} tool={}", iteration, toolName)

                    when (toolName) {
                        "ask_clarifying_question" -> {
                            val question = input["question"]?.asText() ?: break
                            if (!cancelled.get()) emitter.send(question)
                            emitter.complete()
                            completed = true
                            break
                        }

                        "search_venues" -> {
                            foundVenues = executeSearch(input, request.existingContext)
                            val venuesSummary = summariseVenuesForClaude(foundVenues)
                            // Feed result back so Claude can formulate its response
                            messages.add(buildToolResult(toolId, venuesSummary))
                        }

                        "respond_to_user" -> {
                            val message = input["message"]?.asText() ?: ""
                            val includeVenues = input["includeVenues"]?.asBoolean() ?: false

                            if (!cancelled.get() && message.isNotBlank()) emitter.send(message)

                            if (!cancelled.get() && includeVenues && foundVenues.isNotEmpty()) {
                                val payload = buildVenuePayload(foundVenues, input)
                                emitter.send("[VENUES]${objectMapper.writeValueAsString(payload)}")
                            }
                            emitter.complete()
                            completed = true
                            break
                        }

                        else -> {
                            logger.warn("Unknown tool: {}", toolName)
                            break
                        }
                    }
                } else if (stopReason == "end_turn") {
                    // Claude responded with plain text (no tool call)
                    val text = contentArray?.firstOrNull { it["type"]?.asText() == "text" }?.get("text")?.asText()
                    if (!text.isNullOrBlank() && !cancelled.get()) emitter.send(text)
                    emitter.complete()
                    completed = true
                    break
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            if (!cancelled.get()) logger.error("Scout agent error: {}", e.message)
        } finally {
            if (!completed) emitter.complete()
        }
    }

    // ── Claude API call ───────────────────────────────────────────────────────

    private fun callClaude(
        messages: List<Map<String, Any>>,
        contextNote: String,
    ): JsonNode? {
        val system = if (contextNote.isBlank()) SYSTEM_PROMPT
                     else "$SYSTEM_PROMPT\n\nContext: $contextNote"

        val body = mapOf(
            "model"      to MODEL,
            "max_tokens" to 1024,
            "system"     to system,
            "tools"      to ScoutTools.definitions,
            "messages"   to messages,
        )

        return try {
            webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .bodyValue(body)
                .retrieve()
                .onStatus({ it.isError }) { res ->
                    res.bodyToMono(String::class.java).doOnNext { b ->
                        logger.warn("Anthropic API error {}: {}", res.statusCode(), b)
                    }.then(Mono.error(RuntimeException("Anthropic ${res.statusCode()}")))
                }
                .bodyToMono<JsonNode>()
                .block()
        } catch (e: Exception) {
            logger.error("Claude API call failed: {}", e.message)
            null
        }
    }

    // ── Venue search ──────────────────────────────────────────────────────────

    private fun executeSearch(input: JsonNode, existingContext: LandingPageContext): List<Place> {
        val city = input["city"]?.asText() ?: existingContext.city ?: return emptyList()
        val age = input["age"]?.asInt()
        val indoor = if (input.has("indoor")) input["indoor"]?.asBoolean() else null
        val groupSize = input["groupSize"]?.asInt()
        val themes = input["themes"]?.map { it.asText() } ?: emptyList()
        val occasion = input["occasion"]?.asText() ?: existingContext.occasion ?: "birthday party"

        return try {
            val location = googlePlacesService.geocodeCity(city).block() ?: return emptyList()
            val radius = 16_000

            val queries = buildQueries(city, age, indoor, themes, occasion, groupSize)

            val allPlaces: List<Place> = Flux.fromIterable(queries)
                .flatMap({ query ->
                    googlePlacesService.searchText(query, location, radius)
                        .map { it.places ?: emptyList() }
                        .onErrorResume { err ->
                            logger.warn("Agent search query '{}' failed: {}", query, err.message)
                            Mono.just(emptyList())
                        }
                }, 5)
                .flatMapIterable { it }
                .collectList()
                .block()
                ?.distinctBy { it.id }
                ?: emptyList()

            if (allPlaces.isNotEmpty()) allPlaces.take(20)
            else {
                googlePlacesService.searchText("party venue event space $occasion $city", location, 32_000)
                    .block()?.places.orEmpty().take(20)
            }
        } catch (e: Exception) {
            logger.warn("Agent venue search failed for city={}: {}", city, e.message)
            emptyList()
        }
    }

    private fun buildQueries(
        city: String,
        age: Int?,
        indoor: Boolean?,
        themes: List<String>,
        occasion: String,
        groupSize: Int?,
    ): List<String> {
        if (indoor == false) {
            return buildList {
                add("outdoor $occasion venue $city")
                add("park $occasion $city")
                add("outdoor event space $city")
                add("garden party venue $city")
                if (themes.isNotEmpty()) add("outdoor ${themes.first()} party $city")
            }
        }
        val personaQueries = age?.let { personaService.getSearchQueries(it).take(8) } ?: emptyList()
        val base = buildList {
            add("$occasion venue event space $city")
            if (themes.isNotEmpty()) add("${themes.joinToString(" ")} $occasion $city")
            if (groupSize != null && groupSize > 50) add("large event venue $city")
        }
        return (base + personaQueries).distinct().take(12)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildContextNote(ctx: LandingPageContext, knownVenues: List<KnownVenue>): String {
        val parts = mutableListOf<String>()
        if (ctx.city != null) parts.add("city=${ctx.city}")
        if (ctx.persona != null) parts.add("persona=${ctx.persona}")
        if (ctx.occasion != null) parts.add("occasion=${ctx.occasion}")
        if (knownVenues.isNotEmpty()) {
            parts.add("Venues already shown: " + knownVenues.joinToString(", ") { "${it.num}. ${it.name}" })
        }
        return parts.joinToString(", ")
    }

    private fun summariseVenuesForClaude(venues: List<Place>): String {
        if (venues.isEmpty()) return "No venues found."
        return "Found ${venues.size} venues:\n" + venues.take(5).mapIndexed { i, p ->
            "${i + 1}. ${p.displayName?.text ?: "Unknown"} — ${p.formattedAddress ?: ""}${p.rating?.let { ", $it★" } ?: ""}"
        }.joinToString("\n")
    }

    private fun buildVenuePayload(venues: List<Place>, input: JsonNode): List<Map<String, Any?>> {
        val indoor = if (input.has("indoor")) input["indoor"]?.asBoolean() else null
        val filtered = when (indoor) {
            false -> venues.filter { inferSetting(it) != "indoor" }.ifEmpty { venues }
            true  -> venues.filter { inferSetting(it) != "outdoor" }.ifEmpty { venues }
            null  -> venues
        }
        return filtered.take(3).map { p ->
            val photos = p.photos?.take(3)?.map { photo ->
                "https://places.googleapis.com/v1/${photo.name}/media?key=${googlePlacesConfig.apiKey}&maxWidthPx=400"
            } ?: emptyList()
            mapOf(
                "id"            to p.id,
                "googlePlaceId" to p.id,
                "name"          to (p.displayName?.text ?: ""),
                "address"       to (p.formattedAddress ?: ""),
                "rating"        to (p.rating ?: 0.0),
                "website"       to p.websiteUri,
                "googleMapsUri" to p.googleMapsUri,
                "setting"       to inferSetting(p),
                "photos"        to photos,
                "reason"        to generateReason(p),
            )
        }
    }

    private fun inferSetting(p: Place): String {
        val types = p.types?.map { it.lowercase() } ?: emptyList()
        val name = p.displayName?.text?.lowercase() ?: ""
        val outdoorTypes = setOf("park", "zoo", "botanical_garden", "campground", "national_park", "state_park")
        val outdoorKeywords = listOf("outdoor", "farm", "garden", "pavilion", "ranch", "beach", "lake", "nature center", "botanical")
        val hasPark = Regex("\\bpark\\b").containsMatchIn(name) && !name.contains("parking")
        return when {
            types.any { it in outdoorTypes } -> "outdoor"
            outdoorKeywords.any { name.contains(it) } -> "outdoor"
            hasPark -> "outdoor"
            types.any { it.contains("swimming_pool") } -> "both"
            else -> "indoor"
        }
    }

    private fun generateReason(p: Place): String {
        val types = p.types ?: emptyList()
        return when {
            types.any { it.contains("bowling") }   -> "Great for group fun"
            types.any { it.contains("escape") }    -> "Thrilling team challenge"
            types.any { it.contains("trampoline") }-> "High-energy jumping fun"
            types.any { it.contains("laser") }     -> "Epic laser battles"
            types.any { it.contains("arcade") || it.contains("amusement") } -> "Games for everyone"
            types.any { it.contains("art") || it.contains("studio") }       -> "Creative & hands-on"
            types.any { it.contains("park") || it.contains("nature") }      -> "Open-air adventure"
            types.any { it.contains("restaurant") || it.contains("food") }  -> "Great food & atmosphere"
            p.rating != null && p.rating >= 4.8    -> "Exceptional party venue"
            else -> "Top-rated party spot"
        }
    }

    /** Convert Claude's content array to a format suitable for message history */
    private fun parseContentForHistory(contentArray: JsonNode?): Any {
        if (contentArray == null) return ""
        // Return the raw JsonNode list — Anthropic expects content as array when tool_use is present
        return objectMapper.convertValue(contentArray, List::class.java)
    }

    private fun buildToolResult(toolId: String, result: String): Map<String, Any> {
        return mapOf(
            "role" to "user",
            "content" to listOf(
                mapOf(
                    "type"        to "tool_result",
                    "tool_use_id" to toolId,
                    "content"     to result,
                )
            )
        )
    }
}
