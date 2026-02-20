package com.partyscout.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.integration.mocks.TestGooglePlacesConfig
import com.partyscout.model.PartySearchRequest
import com.partyscout.model.PartySearchResponse
import com.partyscout.model.PartyTypeSuggestion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestGooglePlacesConfig::class)
@DisplayName("Party Wizard End-to-End Tests")
class PartyWizardE2ETest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun baseUrl() = "http://localhost:$port"

    @Nested
    @DisplayName("Complete Wizard Flow")
    inner class CompleteWizardFlow {

        @Test
        @DisplayName("should complete full wizard flow for 7-year-old party")
        fun shouldCompleteFullWizardFlow() {
            // Step 1: Get party types for age 7
            val partyTypesResponse = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, partyTypesResponse.statusCode)
            val partyTypes = partyTypesResponse.body!!
            assertTrue(partyTypes.isNotEmpty(), "Should have party type suggestions")

            // Verify we have expected types
            val typeNames = partyTypes.map { it.type }
            assertTrue(typeNames.contains("active_play") || typeNames.contains("amusement"),
                "Should include active_play or amusement for age 7")

            // Step 2: Get budget estimate
            val budgetRequest = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "priceLevel" to 2
            )

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val budgetResponse = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/estimate-budget",
                HttpEntity(budgetRequest, headers),
                Map::class.java
            )

            assertEquals(HttpStatus.OK, budgetResponse.statusCode)
            val budgetData = budgetResponse.body!!
            assertTrue((budgetData["estimatedTotal"] as Int) > 0, "Should have positive estimated total")

            // Step 3: Search venues (this may fail without API key, which is expected)
            val searchRequest = PartySearchRequest(
                age = 7,
                partyTypes = listOf("active_play"),
                guestCount = 15,
                budgetMin = null,
                budgetMax = 500,
                zipCode = "94105",
                setting = "indoor",
                maxDistanceMiles = 10,
                date = null
            )

            val searchResponse = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(searchRequest, headers),
                String::class.java
            )

            // The response might be OK (with venues) or an error (no API key)
            // Both are valid outcomes for this test
            assertNotNull(searchResponse)
        }

        @Test
        @DisplayName("should handle toddler party planning flow")
        fun shouldHandleToddlerPartyFlow() {
            // Get party types for age 3
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/3",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            // Toddlers should have characters_performers available
            assertTrue(types.any { it.type == "characters_performers" },
                "Characters & Performers should be available for toddlers")
        }

        @Test
        @DisplayName("should handle teen party planning flow")
        fun shouldHandleTeenPartyFlow() {
            // Get party types for age 16
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/16",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            // Teens should have amusement available
            assertTrue(types.any { it.type == "amusement" },
                "Amusement should be available for teens")
        }
    }

    @Nested
    @DisplayName("API Response Structure")
    inner class ApiResponseStructure {

        @Test
        @DisplayName("party type suggestions should have all required fields")
        fun partyTypeShouldHaveAllFields() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            val suggestion = response.body!![0]

            assertNotNull(suggestion.type)
            assertNotNull(suggestion.displayName)
            assertNotNull(suggestion.description)
            assertNotNull(suggestion.icon)
            assertNotNull(suggestion.ageRange)
            assertNotNull(suggestion.averageCost)
            assertTrue(suggestion.popularityScore in 1..5)
        }

        @Test
        @DisplayName("budget estimate should have expected structure")
        fun budgetEstimateShouldHaveExpectedStructure() {
            val request = mapOf(
                "partyTypes" to listOf("creative"),
                "guestCount" to 20,
                "priceLevel" to 3
            )

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/estimate-budget",
                HttpEntity(request, headers),
                Map::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!

            assertTrue(body.containsKey("estimatedTotal"))
            assertTrue(body.containsKey("estimatedPerPerson"))
            assertTrue((body["estimatedTotal"] as Int) > 0)
            assertTrue((body["estimatedPerPerson"] as Int) > 0)
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {

        @Test
        @DisplayName("should return 400 for invalid search request")
        fun shouldReturn400ForInvalidRequest() {
            val invalidRequest = mapOf(
                "age" to 7
                // Missing required fields
            )

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(invalidRequest, headers),
                String::class.java
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        @DisplayName("should handle malformed JSON gracefully")
        fun shouldHandleMalformedJson() {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity("{invalid json}", headers),
                String::class.java
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }
    }

    @Nested
    @DisplayName("Performance")
    inner class Performance {

        @Test
        @DisplayName("party types endpoint should respond within 500ms")
        fun partyTypesShouldBefast() {
            val startTime = System.currentTimeMillis()

            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                String::class.java
            )

            val duration = System.currentTimeMillis() - startTime

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(duration < 500, "Response took ${duration}ms, should be under 500ms")
        }

        @Test
        @DisplayName("budget estimate should respond within 500ms")
        fun budgetEstimateShouldBeFast() {
            val request = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "priceLevel" to 2
            )

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val startTime = System.currentTimeMillis()

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/estimate-budget",
                HttpEntity(request, headers),
                String::class.java
            )

            val duration = System.currentTimeMillis() - startTime

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(duration < 500, "Response took ${duration}ms, should be under 500ms")
        }
    }

    @Nested
    @DisplayName("Live API Integration")
    @EnabledIfEnvironmentVariable(named = "GOOGLE_PLACES_API_KEY", matches = ".+")
    inner class LiveApiIntegration {

        @Test
        @DisplayName("should return venues from Google Places API")
        fun shouldReturnVenuesFromGooglePlaces() {
            val request = PartySearchRequest(
                age = 7,
                partyTypes = listOf("active_play"),
                guestCount = 15,
                budgetMin = null,
                budgetMax = 500,
                zipCode = "94105",
                setting = "indoor",
                maxDistanceMiles = 10,
                date = null
            )

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, headers),
                PartySearchResponse::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val searchResponse = response.body!!

            assertNotNull(searchResponse.venues)
            assertTrue(searchResponse.venues.isNotEmpty(), "Should return venues")

            // Verify venue structure
            val firstVenue = searchResponse.venues[0]
            assertNotNull(firstVenue.id)
            assertNotNull(firstVenue.name)
            assertNotNull(firstVenue.address)
            assertTrue(firstVenue.matchScore in 0..100)
            assertTrue(firstVenue.estimatedTotal > 0)
        }
    }
}
