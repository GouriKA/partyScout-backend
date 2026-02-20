package com.partyscout.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.integration.mocks.TestGooglePlacesConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import com.partyscout.model.PartyTypeSuggestion

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestGooglePlacesConfig::class)
@DisplayName("Party Details End-to-End Tests")
class PartyDetailsE2ETest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun baseUrl() = "http://localhost:$port"

    private fun createHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return headers
    }

    @Nested
    @DisplayName("Party Details Retrieval for Each Party Type")
    inner class PartyDetailsRetrieval {

        @Test
        @DisplayName("should return details for active_play party type")
        fun shouldReturnDetailsForActivePlayPartyType() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            val activePlay = types.find { it.type == "active_play" }
            assertNotNull(activePlay, "Should include active_play type")
            assertNotNull(activePlay!!.displayName)
            assertNotNull(activePlay.description)
            assertNotNull(activePlay.ageRange)
        }

        @Test
        @DisplayName("should return details for creative party type")
        fun shouldReturnDetailsForCreativePartyType() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/8",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            val creative = types.find { it.type == "creative" }
            assertNotNull(creative, "Should include creative type")
        }

        @Test
        @DisplayName("should return details for amusement party type")
        fun shouldReturnDetailsForAmusementPartyType() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/12",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            val amusement = types.find { it.type == "amusement" }
            assertNotNull(amusement, "Should include amusement type")
        }

        @Test
        @DisplayName("should return details for outdoor party type")
        fun shouldReturnDetailsForOutdoorPartyType() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/6",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            val outdoor = types.find { it.type == "outdoor" }
            assertNotNull(outdoor, "Should include outdoor type")
        }

        @Test
        @DisplayName("should return details for characters_performers party type")
        fun shouldReturnDetailsForCharactersPerformersPartyType() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/4",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            val characters = types.find { it.type == "characters_performers" }
            assertNotNull(characters, "Should include characters_performers type")
        }

        @Test
        @DisplayName("should return details for social_dining party type")
        fun shouldReturnDetailsForSocialDiningPartyType() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/10",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            val socialDining = types.find { it.type == "social_dining" }
            assertNotNull(socialDining, "Should include social_dining type")
        }
    }

    @Nested
    @DisplayName("Included Items by Price Level")
    inner class IncludedItemsByPriceLevel {

        @Test
        @DisplayName("should return budget estimate with included info")
        fun shouldReturnBudgetEstimateWithIncludedInfo() {
            val request = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "priceLevel" to 2
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/estimate-budget",
                HttpEntity(request, createHeaders()),
                Map::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
            assertTrue(response.body!!.containsKey("estimatedTotal"))
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 2, 3, 4])
        @DisplayName("should handle all price levels")
        fun shouldHandleAllPriceLevels(priceLevel: Int) {
            val request = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "priceLevel" to priceLevel
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/estimate-budget",
                HttpEntity(request, createHeaders()),
                Map::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode, "Should handle price level $priceLevel")
        }
    }

    @Nested
    @DisplayName("Age-Appropriate Recommendations")
    inner class AgeAppropriateRecommendations {

        @Test
        @DisplayName("should return toddler-appropriate party types for age 3")
        fun shouldReturnToddlerAppropriatePartyTypesForAge3() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/3",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            // Should include characters_performers for toddlers
            assertTrue(
                types.any { it.type == "characters_performers" },
                "Should include characters_performers for toddlers"
            )

            // Verify age ranges are appropriate
            types.forEach { type ->
                val ageRange = type.ageRange
                assertTrue(
                    ageRange.contains("3") || ageRange.contains("2") || ageRange.contains("1"),
                    "Age range should include young children: $ageRange"
                )
            }
        }

        @Test
        @DisplayName("should return child-appropriate party types for age 8")
        fun shouldReturnChildAppropriatePartyTypesForAge8() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/8",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            // Should include active_play and creative for children
            assertTrue(
                types.any { it.type == "active_play" },
                "Should include active_play for children"
            )
        }

        @Test
        @DisplayName("should return teen-appropriate party types for age 16")
        fun shouldReturnTeenAppropriatePartyTypesForAge16() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/16",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            // Should include amusement for teens
            assertTrue(
                types.any { it.type == "amusement" },
                "Should include amusement for teens"
            )
        }

        @Test
        @DisplayName("should sort party types by popularity for age")
        fun shouldSortPartyTypesByPopularityForAge() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            // Verify types are sorted by popularity (highest first)
            for (i in 0 until types.size - 1) {
                assertTrue(
                    types[i].popularityScore >= types[i + 1].popularityScore,
                    "Types should be sorted by popularity score"
                )
            }
        }
    }

    @Nested
    @DisplayName("Party Type Suggestion Details")
    inner class PartyTypeSuggestionDetails {

        @Test
        @DisplayName("should include all required fields in party type suggestions")
        fun shouldIncludeAllRequiredFieldsInPartyTypeSuggestions() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            types.forEach { type ->
                assertNotNull(type.type, "Type should have type field")
                assertNotNull(type.displayName, "Type should have displayName")
                assertNotNull(type.description, "Type should have description")
                assertNotNull(type.icon, "Type should have icon")
                assertNotNull(type.ageRange, "Type should have ageRange")
                assertNotNull(type.averageCost, "Type should have averageCost")
                assertTrue(type.popularityScore in 1..5, "Popularity score should be 1-5")
            }
        }

        @Test
        @DisplayName("should include meaningful descriptions for party types")
        fun shouldIncludeMeaningfulDescriptionsForPartyTypes() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            types.forEach { type ->
                assertTrue(type.description.length > 10, "Description should be meaningful")
                assertFalse(type.description.isBlank(), "Description should not be blank")
            }
        }

        @Test
        @DisplayName("should include valid age ranges for party types")
        fun shouldIncludeValidAgeRangesForPartyTypes() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            types.forEach { type ->
                assertTrue(
                    type.ageRange.contains("-") || type.ageRange.contains("+"),
                    "Age range should be formatted correctly: ${type.ageRange}"
                )
            }
        }

        @Test
        @DisplayName("should include average cost estimates for party types")
        fun shouldIncludeAverageCostEstimatesForPartyTypes() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            val types = response.body!!

            types.forEach { type ->
                assertTrue(
                    type.averageCost.contains("$"),
                    "Average cost should include dollar sign: ${type.averageCost}"
                )
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    inner class EdgeCasesAndBoundaryConditions {

        @Test
        @DisplayName("should handle age 0 gracefully")
        fun shouldHandleAge0Gracefully() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/0",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            // Should return empty or minimal results for age 0
        }

        @Test
        @DisplayName("should handle very high age")
        fun shouldHandleVeryHighAge() {
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/100",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            // Should still return some results for adults
        }

        @Test
        @DisplayName("should return consistent results for same age")
        fun shouldReturnConsistentResultsForSameAge() {
            val response1 = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            val response2 = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                Array<PartyTypeSuggestion>::class.java
            )

            assertEquals(HttpStatus.OK, response1.statusCode)
            assertEquals(HttpStatus.OK, response2.statusCode)

            val types1 = response1.body!!.map { it.type }.sorted()
            val types2 = response2.body!!.map { it.type }.sorted()

            assertEquals(types1, types2, "Results should be consistent")
        }
    }

    @Nested
    @DisplayName("Performance")
    inner class Performance {

        @Test
        @DisplayName("should return party types quickly")
        fun shouldReturnPartyTypesQuickly() {
            val startTime = System.currentTimeMillis()

            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/party-wizard/party-types/7",
                String::class.java
            )

            val duration = System.currentTimeMillis() - startTime

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(duration < 500, "Should respond within 500ms, took ${duration}ms")
        }

        @Test
        @DisplayName("should return budget estimate quickly")
        fun shouldReturnBudgetEstimateQuickly() {
            val request = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "priceLevel" to 2
            )

            val startTime = System.currentTimeMillis()

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/estimate-budget",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            val duration = System.currentTimeMillis() - startTime

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(duration < 500, "Should respond within 500ms, took ${duration}ms")
        }
    }
}
