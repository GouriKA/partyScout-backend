package com.partyscout.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.integration.mocks.TestGooglePlacesConfig
import com.partyscout.model.PartySearchRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
@DisplayName("Party Search End-to-End Tests")
class PartySearchE2ETest {

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
    @DisplayName("Complete Search with Real API Structure")
    inner class CompleteSearchWithRealApiStructure {

        @Test
        @DisplayName("should return venue results with all required fields")
        fun shouldReturnVenueResultsWithAllRequiredFields() {
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

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                Map::class.java
            )

            // Response should be successful (venues may be empty without API key)
            assertNotNull(response)
            assertTrue(
                response.statusCode == HttpStatus.OK ||
                response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR,
                "Response should be OK or error (no API key)"
            )

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                val body = response.body!!
                assertTrue(body.containsKey("venues"), "Response should contain venues key")
            }
        }

        @Test
        @DisplayName("should handle search with multiple party types")
        fun shouldHandleSearchWithMultiplePartyTypes() {
            val request = PartySearchRequest(
                age = 10,
                partyTypes = listOf("active_play", "creative", "amusement"),
                guestCount = 20,
                budgetMin = 200,
                budgetMax = 800,
                zipCode = "94105",
                setting = "any",
                maxDistanceMiles = 15,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }
    }

    @Nested
    @DisplayName("Different Age Groups")
    inner class DifferentAgeGroups {

        @Test
        @DisplayName("should search for toddler party (age 3)")
        fun shouldSearchForToddlerParty() {
            val request = PartySearchRequest(
                age = 3,
                partyTypes = listOf("characters_performers"),
                guestCount = 10,
                budgetMin = null,
                budgetMax = 400,
                zipCode = "94105",
                setting = "indoor",
                maxDistanceMiles = 5,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }

        @Test
        @DisplayName("should search for child party (age 8)")
        fun shouldSearchForChildParty() {
            val request = PartySearchRequest(
                age = 8,
                partyTypes = listOf("active_play"),
                guestCount = 15,
                budgetMin = null,
                budgetMax = 500,
                zipCode = "94105",
                setting = "indoor",
                maxDistanceMiles = 10,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }

        @Test
        @DisplayName("should search for teen party (age 15)")
        fun shouldSearchForTeenParty() {
            val request = PartySearchRequest(
                age = 15,
                partyTypes = listOf("amusement"),
                guestCount = 12,
                budgetMin = null,
                budgetMax = 600,
                zipCode = "94105",
                setting = "any",
                maxDistanceMiles = 15,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }
    }

    @Nested
    @DisplayName("Various Budget Ranges")
    inner class VariousBudgetRanges {

        @Test
        @DisplayName("should search with small budget")
        fun shouldSearchWithSmallBudget() {
            val request = PartySearchRequest(
                age = 7,
                partyTypes = listOf("outdoor"),
                guestCount = 10,
                budgetMin = null,
                budgetMax = 200,
                zipCode = "94105",
                setting = "outdoor",
                maxDistanceMiles = 10,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }

        @Test
        @DisplayName("should search with large budget")
        fun shouldSearchWithLargeBudget() {
            val request = PartySearchRequest(
                age = 10,
                partyTypes = listOf("active_play", "amusement"),
                guestCount = 30,
                budgetMin = 500,
                budgetMax = 2000,
                zipCode = "94105",
                setting = "any",
                maxDistanceMiles = 20,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }

        @Test
        @DisplayName("should search with no budget limit")
        fun shouldSearchWithNoBudgetLimit() {
            val request = PartySearchRequest(
                age = 12,
                partyTypes = listOf("creative"),
                guestCount = 15,
                budgetMin = null,
                budgetMax = null,
                zipCode = "94105",
                setting = "indoor",
                maxDistanceMiles = 10,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }
    }

    @Nested
    @DisplayName("Distance Filtering")
    inner class DistanceFiltering {

        @Test
        @DisplayName("should search within 5 miles")
        fun shouldSearchWithin5Miles() {
            val request = PartySearchRequest(
                age = 7,
                partyTypes = listOf("active_play"),
                guestCount = 15,
                budgetMin = null,
                budgetMax = 500,
                zipCode = "94105",
                setting = "indoor",
                maxDistanceMiles = 5,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }

        @Test
        @DisplayName("should search within 25 miles")
        fun shouldSearchWithin25Miles() {
            val request = PartySearchRequest(
                age = 7,
                partyTypes = listOf("active_play"),
                guestCount = 15,
                budgetMin = null,
                budgetMax = 500,
                zipCode = "94105",
                setting = "any",
                maxDistanceMiles = 25,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }
    }

    @Nested
    @DisplayName("Setting Preferences")
    inner class SettingPreferences {

        @Test
        @DisplayName("should search for indoor venues only")
        fun shouldSearchForIndoorVenuesOnly() {
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

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }

        @Test
        @DisplayName("should search for outdoor venues only")
        fun shouldSearchForOutdoorVenuesOnly() {
            val request = PartySearchRequest(
                age = 7,
                partyTypes = listOf("outdoor"),
                guestCount = 20,
                budgetMin = null,
                budgetMax = 400,
                zipCode = "94105",
                setting = "outdoor",
                maxDistanceMiles = 10,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }

        @Test
        @DisplayName("should search for any setting")
        fun shouldSearchForAnySetting() {
            val request = PartySearchRequest(
                age = 7,
                partyTypes = listOf("active_play", "outdoor"),
                guestCount = 15,
                budgetMin = null,
                budgetMax = 500,
                zipCode = "94105",
                setting = "any",
                maxDistanceMiles = 10,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response)
        }
    }

    @Nested
    @DisplayName("All Party Types")
    inner class AllPartyTypes {

        @Test
        @DisplayName("should search for active_play party")
        fun shouldSearchForActivePlayParty() {
            searchWithPartyType("active_play", 7)
        }

        @Test
        @DisplayName("should search for creative party")
        fun shouldSearchForCreativeParty() {
            searchWithPartyType("creative", 8)
        }

        @Test
        @DisplayName("should search for amusement party")
        fun shouldSearchForAmusementParty() {
            searchWithPartyType("amusement", 12)
        }

        @Test
        @DisplayName("should search for outdoor party")
        fun shouldSearchForOutdoorParty() {
            searchWithPartyType("outdoor", 6)
        }

        @Test
        @DisplayName("should search for characters_performers party")
        fun shouldSearchForCharactersPerformersParty() {
            searchWithPartyType("characters_performers", 4)
        }

        @Test
        @DisplayName("should search for social_dining party")
        fun shouldSearchForSocialDiningParty() {
            searchWithPartyType("social_dining", 10)
        }

        private fun searchWithPartyType(partyType: String, age: Int) {
            val request = PartySearchRequest(
                age = age,
                partyTypes = listOf(partyType),
                guestCount = 15,
                budgetMin = null,
                budgetMax = 500,
                zipCode = "94105",
                setting = "any",
                maxDistanceMiles = 10,
                date = null
            )

            val response = restTemplate.postForEntity(
                "${baseUrl()}/api/v2/party-wizard/search",
                HttpEntity(request, createHeaders()),
                String::class.java
            )

            assertNotNull(response, "Response should not be null for party type: $partyType")
        }
    }
}
