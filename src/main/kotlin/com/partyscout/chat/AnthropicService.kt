package com.partyscout.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.llm.AnthropicResponse
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
) {
    private val logger = LoggerFactory.getLogger(AnthropicService::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.anthropic.com")
        .build()

    private val MODEL = "claude-sonnet-4-5-20250514"

    private val INTENT_SYSTEM_PROMPT = """
        You are a party planning intent extractor. Extract the user's party planning intent from their message and conversation history.

        Return ONLY valid JSON matching this exact structure (no markdown, no code fences, no explanation):
        {
          "city": "city name or null",
          "persona": "Kids/Teens/Adults or null",
          "occasion": "birthday/graduation/etc or null",
          "age": integer or null,
          "groupSize": integer or null,
          "themes": ["theme1", "theme2"],
          "indoor": true or false or null,
          "date": "YYYY-MM-DD or null",
          "readyToSearch": true or false
        }

        Set readyToSearch to true when city is known (from message or context) and there is enough intent to usefully search for venues.
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
        history: List<ChatMessage>,
        emitter: SseEmitter,
        cancelled: AtomicBoolean,
    ) {
        if (apiKey.isBlank()) {
            emitter.send("I'm not able to assist right now — API key not configured.")
            emitter.complete()
            return
        }

        val systemPrompt = buildSystemPrompt(intent, venues)
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
                .forEach { line ->
                    if (cancelled.get()) return@forEach   // client disconnected — stop sending

                    if (!line.startsWith("data: ")) return@forEach
                    val json = line.removePrefix("data: ").trim()
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
                                        mapOf(
                                            "id" to p.id,
                                            "googlePlaceId" to p.id,
                                            "name" to (p.displayName?.text ?: ""),
                                            "address" to (p.formattedAddress ?: ""),
                                            "rating" to (p.rating ?: 0.0),
                                            "website" to p.websiteUri,
                                            "googleMapsUri" to p.googleMapsUri,
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

    private fun buildSystemPrompt(intent: ChatIntent, venues: List<Place>): String {
        val venueContext = if (venues.isEmpty()) {
            "No venues found yet."
        } else {
            "Venues found:\n" + venues.take(3).joinToString("\n") { p ->
                "- ${p.displayName?.text ?: "Unknown"} at ${p.formattedAddress ?: ""}" +
                        (p.rating?.let { " (rated $it)" } ?: "")
            }
        }

        val responseGuidance = if (venues.isNotEmpty()) {
            "Briefly highlight what makes each venue stand out and help the user choose. Be warm and concise (2-3 sentences)."
        } else {
            "Ask one friendly clarifying question to narrow down what the user is looking for."
        }

        return """
            You are PartyScout, a friendly AI assistant helping plan the perfect birthday party.

            $venueContext

            Context: city=${intent.city ?: "unknown"}, persona=${intent.persona ?: "unspecified"}, occasion=${intent.occasion ?: "birthday party"}

            $responseGuidance
        """.trimIndent()
    }
}
