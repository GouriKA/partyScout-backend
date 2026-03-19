package com.partyscout.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.persistence.entity.VenueEnrichmentEntity
import com.partyscout.venue.dto.Place
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class LlmFilterService(
    @Value("\${ANTHROPIC_API_KEY:}") private val apiKey: String,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(LlmFilterService::class.java)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.anthropic.com")
        .build()

    /**
     * Score and filter venues using Claude.
     * Returns (filteredPlaces, llmFilterApplied).
     * On any failure returns (original places, false) — never breaks search.
     */
    fun filter(
        places: List<Place>,
        age: Int,
        persona: String,
        enrichmentMap: Map<String, VenueEnrichmentEntity> = emptyMap(),
    ): Pair<List<Place>, Boolean> {
        if (apiKey.isBlank()) {
            logger.warn("ANTHROPIC_API_KEY not configured — skipping LLM filter")
            return Pair(places, false)
        }
        if (places.isEmpty()) return Pair(places, false)

        return try {
            val prompt = buildPrompt(places, age, persona, enrichmentMap)
            val rawResponse = callClaude(prompt) ?: return Pair(places, false)
            val scores = parseScores(rawResponse) ?: return Pair(places, false)

            val passingIds = scores
                .filter { it.relevanceScore > 0.6 && it.ageAppropriate }
                .map { it.placeId }
                .toSet()

            val filtered = places.filter { (it.id ?: "") in passingIds }

            if (filtered.isEmpty()) {
                logger.warn("LLM filtered out all venues — returning unfiltered")
                return Pair(places, false)
            }

            logger.info("LLM filter: {} → {} venues (age={}, persona={})", places.size, filtered.size, age, persona)
            Pair(filtered, true)
        } catch (e: Exception) {
            logger.warn("LLM filter failed — returning unfiltered: {}", e.message)
            Pair(places, false)
        }
    }

    private fun buildPrompt(
        places: List<Place>,
        age: Int,
        persona: String,
        enrichmentMap: Map<String, VenueEnrichmentEntity>,
    ): String {
        val venueData = places.map { p ->
            buildMap {
                put("placeId", p.id ?: "")
                put("name", p.displayName?.text ?: "")
                put("types", p.types ?: emptyList<String>())
                put("vicinity", p.formattedAddress ?: "")
                put("rating", p.rating ?: 0.0)
                enrichmentMap[p.id]?.let { enr ->
                    enr.under18Welcome?.let { put("under18Welcome", it) }
                    enr.alcoholPremises?.let { put("alcoholPremises", it) }
                    enr.personaTags?.let { put("personaTags", it) }
                }
            }
        }
        val venueJson = objectMapper.writeValueAsString(venueData)

        return """You are a helpful activity recommendation engine.
The user has a profile: age $age, persona: $persona.

Score each of the following venues for relevance to this user.
Return ONLY a JSON array, no markdown, no explanation:
[{
  "placeId": "string",
  "relevanceScore": 0.0,
  "reason": "max 10 words",
  "ageAppropriate": true
}]

Venues:
$venueJson"""
    }

    private fun callClaude(prompt: String): String? {
        val requestBody = mapOf(
            "model" to "claude-sonnet-4-5-20250514",
            "max_tokens" to 600,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt))
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

            response?.content?.firstOrNull { it.type == "text" }?.text
        } catch (e: Exception) {
            logger.warn("Anthropic API call failed: {}", e.message)
            null
        }
    }

    private fun parseScores(json: String): List<VenueScore>? {
        return try {
            val cleaned = json.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val listType = objectMapper.typeFactory
                .constructCollectionType(List::class.java, VenueScore::class.java)
            objectMapper.readValue(cleaned, listType)
        } catch (e: Exception) {
            logger.warn("Failed to parse LLM scores: {}", e.message)
            null
        }
    }
}

// ── Anthropic API DTOs ────────────────────────────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
data class AnthropicResponse(
    val content: List<AnthropicContent> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AnthropicContent(
    val type: String = "",
    val text: String? = null,
)

// ── LLM scoring output ────────────────────────────────────────────────────────

@JsonIgnoreProperties(ignoreUnknown = true)
data class VenueScore(
    val placeId: String = "",
    val relevanceScore: Double = 0.0,
    val reason: String = "",
    val ageAppropriate: Boolean = false,
)
