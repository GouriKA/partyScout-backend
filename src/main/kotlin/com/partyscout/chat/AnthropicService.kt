package com.partyscout.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.llm.AnthropicResponse
import com.partyscout.venue.config.GooglePlacesConfig
import com.partyscout.venue.dto.Place
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.atomic.AtomicBoolean

@Service
class AnthropicService(
    @Value("\${ANTHROPIC_API_KEY:}") private val apiKey: String,
    private val objectMapper: ObjectMapper,
    private val googlePlacesConfig: GooglePlacesConfig,
) {
    private val logger = LoggerFactory.getLogger(AnthropicService::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.anthropic.com")
        .build()

    private val MODEL = "claude-haiku-4-5-20251001"

    private val INTENT_SYSTEM_PROMPT = """
        You are a party and event planning intent extractor. Extract structured intent from the user's message and conversation history.

        Return ONLY valid JSON matching this exact structure (no markdown, no code fences, no explanation):
        {
          "city": "city name or null",
          "persona": "Baby & Toddler/Preschool/Kids/Tweens/Early Teens/Teens/Young Adults/Adults or null",
          "occasion": "birthday/graduation/baby shower/bridal shower/anniversary/retirement/corporate/quinceañera/bar mitzvah/bat mitzvah/holiday/reunion/engagement/farewell/housewarming/other or null",
          "age": integer or null,
          "groupSize": integer or null,
          "themes": ["theme1", "theme2"],
          "indoor": true (indoor preferred) or false (outdoor preferred) or null (no preference),
          "date": "YYYY-MM-DD or null",
          "readyToSearch": true or false
        }

        Rules:
        - Set indoor=false when user says "outdoor", "outside", "open air", "garden party", "park", "backyard", etc.
        - Set indoor=true when user says "indoor", "inside", "venue hall", "event space", etc.
        - persona should reflect the age group of the guest of honor or primary attendees.
        - occasion covers all party/event types, not just birthdays.
        - Set readyToSearch to true ONLY when a specific city is known AND at least one of age, persona, occasion, or themes is also known.
        - Set readyToSearch to false if city is unknown.
    """.trimIndent()

    // ── extractIntent ────────────────────────────────────────────────────────

    /**
     * Extract structured party planning intent from the user's message.
     *
     * Fix #2 — JSON parse failure: uses regex to extract the first {...} block
     * from Claude's response instead of fragile prefix/suffix stripping.
     * Any surrounding text, code fences, or markdown are tolerated.
     * Returns ChatIntent(readyToSearch=false) on any parse failure so the
     * controller falls back to a follow-up question stream.
     */
    fun extractIntent(message: String, history: List<ChatMessage>): ChatIntent {
        if (apiKey.isBlank()) {
            logger.warn("ANTHROPIC_API_KEY not configured — returning empty intent")
            return ChatIntent()
        }

        val messages = history.map { mapOf("role" to it.role, "content" to it.content) } +
                listOf(mapOf("role" to "user", "content" to message))

        val requestBody = mapOf(
            "model" to MODEL,
            "max_tokens" to 300,
            "system" to INTENT_SYSTEM_PROMPT,
            "messages" to messages,
        )

        return try {
            val response = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus({ it.isError }) { res ->
                    res.bodyToMono(String::class.java).doOnNext { body ->
                        logger.warn("Anthropic API error {}: {}", res.statusCode(), body)
                    }.then(reactor.core.publisher.Mono.error(RuntimeException("Anthropic ${res.statusCode()}")))
                }
                .bodyToMono<AnthropicResponse>()
                .block()

            val raw = response?.content?.firstOrNull { it.type == "text" }?.text
                ?: return ChatIntent()

            // Extract the first JSON object — tolerates code fences, leading/trailing text
            val jsonMatch = Regex("""\{[\s\S]*\}""").find(raw)
            if (jsonMatch == null) {
                logger.warn("No JSON object found in intent response: {}", raw.take(200))
                return ChatIntent()
            }

            objectMapper.readValue(jsonMatch.value, ChatIntent::class.java)
        } catch (e: Exception) {
            logger.warn("Intent extraction failed: {} — returning readyToSearch=false", e.message)
            ChatIntent()
        }
    }

    // ── streamResponse ───────────────────────────────────────────────────────

    /**
     * Stream a conversational response to the SseEmitter.
     *
     * Fix #1 — SSE connection drop: accepts [cancelled] AtomicBoolean set by the
     * controller when the client disconnects (onCompletion / onError callbacks).
     * Checks the flag before every emitter.send() so mobile disconnects stop
     * the stream immediately rather than writing to a dead socket.
     *
     * Text tokens arrive as individual SSE data events.
     * After the text stream ends, if venues were found, sends:
     *   data: [VENUES]<json array of up to 3 venues>\n\n
     * then completes the emitter.
     */
    fun streamResponse(
        userMessage: String,
        intent: ChatIntent,
        venues: List<Place>,
        knownVenues: List<KnownVenue> = emptyList(),
        history: List<ChatMessage>,
        emitter: SseEmitter,
        cancelled: AtomicBoolean,
    ) {
        if (apiKey.isBlank()) {
            emitter.send("I'm not able to assist right now — API key not configured.")
            emitter.complete()
            return
        }

        val systemPrompt = buildSystemPrompt(intent, venues, knownVenues)
        val messages = history.map { mapOf("role" to it.role, "content" to it.content) } +
                listOf(mapOf("role" to "user", "content" to userMessage))

        val requestBody = mapOf(
            "model" to MODEL,
            "max_tokens" to 600,
            "stream" to true,
            "system" to systemPrompt,
            "messages" to messages,
        )

        var completed = false
        try {
            webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux<String>()
                .toStream()
                .forEach { chunk ->
                    if (cancelled.get()) return@forEach

                    // bodyToFlux<String>() with text/event-stream strips "event:" and "data:" prefixes
                    // — each chunk is pure JSON for one SSE event
                    val json = chunk.trim()
                    if (json.isEmpty()) return@forEach

                    try {
                        val event = objectMapper.readTree(json)
                        when (event["type"]?.asText()) {
                            "content_block_delta" -> {
                                if (event["delta"]?.get("type")?.asText() == "text_delta") {
                                    val text = event["delta"]["text"]?.asText() ?: ""
                                    if (text.isNotEmpty() && !cancelled.get()) emitter.send(text)
                                }
                            }
                            "message_stop" -> {
                                if (!cancelled.get() && venues.isNotEmpty()) {
                                    val venuePayload = venues.take(3).map { p ->
                                        val photos = p.photos?.take(3)?.map { photo ->
                                            "https://places.googleapis.com/v1/${photo.name}/media?key=${googlePlacesConfig.apiKey}&maxWidthPx=400"
                                        } ?: emptyList()
                                        mapOf(
                                            "id" to p.id,
                                            "googlePlaceId" to p.id,
                                            "name" to (p.displayName?.text ?: ""),
                                            "address" to (p.formattedAddress ?: ""),
                                            "rating" to (p.rating ?: 0.0),
                                            "website" to p.websiteUri,
                                            "googleMapsUri" to p.googleMapsUri,
                                            "setting" to inferVenueSetting(p.types ?: emptyList(), p.displayName?.text ?: ""),
                                            "photos" to photos,
                                            "reason" to generateVenueReason(p),
                                        )
                                    }
                                    emitter.send("[VENUES]${objectMapper.writeValueAsString(venuePayload)}")
                                }
                                emitter.complete()
                                completed = true
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Error parsing SSE event from Anthropic: {}", e.message)
                    }
                }
        } catch (e: Exception) {
            if (!cancelled.get()) logger.error("Anthropic streaming error: {}", e.message)
        } finally {
            if (!completed) emitter.complete()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun inferVenueSetting(types: List<String>, name: String): String {
        val lowercaseTypes = types.map { it.lowercase() }
        val lowercaseName = name.lowercase()
        val outdoorTypes = setOf("park", "zoo", "botanical_garden", "campground", "natural_feature", "rv_park", "national_park", "state_park")
        val outdoorNameKeywords = listOf("outdoor", "farm", "garden", "pavilion", "ranch", "beach", "lake", "nature center", "forest", "trail", "reserve", "campground", "picnic area", "botanical")
        val nameHasPark = Regex("\\bpark\\b").containsMatchIn(lowercaseName) && !lowercaseName.contains("parking")
        val nameHasField = Regex("\\bfield\\b").containsMatchIn(lowercaseName)
        val nameHasYard = Regex("\\byard\\b").containsMatchIn(lowercaseName)
        val bothKeywords = listOf("pool", "aquatic", "splash pad", "water park")
        return when {
            lowercaseTypes.any { it in outdoorTypes } -> "outdoor"
            outdoorNameKeywords.any { lowercaseName.contains(it) } -> "outdoor"
            nameHasPark || nameHasField || nameHasYard -> "outdoor"
            lowercaseTypes.any { it.contains("swimming_pool") } -> "both"
            bothKeywords.any { lowercaseName.contains(it) } -> "both"
            else -> "indoor"
        }
    }

    private fun generateVenueReason(place: com.partyscout.venue.dto.Place): String {
        val types = place.types ?: emptyList()
        return when {
            types.any { it.contains("bowling") }                              -> "Great for group fun"
            types.any { it.contains("escape") }                               -> "Thrilling team challenge"
            types.any { it.contains("trampoline") }                           -> "High-energy jumping fun"
            types.any { it.contains("laser") }                                -> "Epic laser battles"
            types.any { it.contains("arcade") || it.contains("amusement") }  -> "Games for everyone"
            types.any { it.contains("art") || it.contains("studio") }        -> "Creative & hands-on"
            types.any { it.contains("park") || it.contains("nature") }       -> "Open-air adventure"
            types.any { it.contains("spa") || it.contains("beauty") }        -> "Relaxing & fun"
            types.any { it.contains("restaurant") || it.contains("food") }   -> "Great food & atmosphere"
            types.any { it.contains("sports") || it.contains("gym") }        -> "Active & energetic"
            place.rating != null && place.rating >= 4.8                       -> "Exceptional party venue"
            else                                                              -> "Top-rated party spot"
        }
    }

    private fun buildSystemPrompt(intent: ChatIntent, venues: List<Place>, knownVenues: List<KnownVenue> = emptyList()): String {

        val venueContext = when {
            venues.isNotEmpty() ->
                "Venues found:\n" + venues.take(3).mapIndexed { i, p ->
                    "${i + 1}. ${p.displayName?.text ?: "Unknown"} — ${p.formattedAddress ?: ""}${p.rating?.let { ", rated $it/5" } ?: ""}"
                }.joinToString("\n")
            knownVenues.isNotEmpty() ->
                "Venues shown to the user:\n" + knownVenues.joinToString("\n") { v ->
                    "${v.num}. ${v.name}${v.rating?.let { " — $it/5" } ?: ""}${v.address?.let { " — $it" } ?: ""}${v.setting?.let { " — $it" } ?: ""}${v.reason?.let { " — $it" } ?: ""}"
                }
            else -> ""
        }

        val task = when {
            venues.isNotEmpty() -> """
                Venues were just found. Write one punchy line (max 8 words) to hand them off.
                Example: "Here's what I found:" or "Three solid options for you."
                Do NOT describe the venues — the UI cards do that.
            """.trimIndent()
            knownVenues.isNotEmpty() -> """
                Answer the user's follow-up about the venues listed above.
                Reference venues by number and name. Be direct. Max 3 sentences.
            """.trimIndent()
            else -> """
                Gather what you need to find venues. Rules:
                - City unknown → ask for city only. One sentence.
                - City known, vibe/age/occasion unclear → ask exactly one follow-up question.
                - Never ask two things at once.
                - Never reference any UI element, form, or search bar.
            """.trimIndent()
        }

        return """
            You are Scout — a sharp, no-nonsense party planning local who has helped hundreds of people find the perfect venue.

            Your voice:
            - Talk like a knowledgeable, courteous friend — warm, genuine, never stiff.
            - Acknowledge what the user shares before moving on (e.g. "A birthday for twins — fun!").
            - Short sentences. Real words. Zero fluff.
            - Never say "Certainly!", "Of course!", "I'd be happy to", or "As an AI".
            - Never use hollow filler phrases. Every sentence should mean something.
            - Polite but efficient. One question at a time. Trust the user's intelligence.
            - If someone seems unsure, reassure them briefly — then guide them forward.
            - Use "I" naturally — you are Scout, a person, not a product.

            ${if (venueContext.isNotEmpty()) "Context:\n$venueContext\n" else ""}Party details: city=${intent.city ?: "unknown"}, for=${intent.persona ?: "unspecified"}, occasion=${intent.occasion ?: "birthday"}

            Your task for this response:
            $task
        """.trimIndent()
    }
}
