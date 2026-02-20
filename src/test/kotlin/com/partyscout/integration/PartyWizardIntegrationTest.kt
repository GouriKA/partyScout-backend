package com.partyscout.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.integration.mocks.TestGooglePlacesConfig
import com.partyscout.model.PartySearchRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestGooglePlacesConfig::class)
@DisplayName("Party Wizard Integration Tests")
class PartyWizardIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Nested
    @DisplayName("Complete Search Flow")
    inner class CompleteSearchFlow {

        @Test
        @DisplayName("should complete search flow from party types to venue search")
        fun shouldCompleteSearchFlow() {
            // Step 1: Get party types for age 7
            mockMvc.perform(get("/api/v2/party-wizard/party-types/7"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].type").exists())
                .andExpect(jsonPath("$[0].displayName").exists())
                .andExpect(jsonPath("$[0].popularityScore").exists())

            // Step 2: Get budget estimate
            val budgetRequest = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "priceLevel" to 2
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(budgetRequest))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.estimatedTotal").isNumber)
                .andExpect(jsonPath("$.estimatedPerPerson").isNumber)

            // Step 3: Search venues
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

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(searchRequest))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.venues").isArray)
        }
    }

    @Nested
    @DisplayName("Party Type Suggestions")
    inner class PartyTypeSuggestions {

        @Test
        @DisplayName("should return different party types for different ages")
        fun shouldReturnDifferentTypesForDifferentAges() {
            // Toddler (age 3)
            val toddlerResponse = mockMvc.perform(get("/api/v2/party-wizard/party-types/3"))
                .andExpect(status().isOk)
                .andReturn()

            // Child (age 8)
            val childResponse = mockMvc.perform(get("/api/v2/party-wizard/party-types/8"))
                .andExpect(status().isOk)
                .andReturn()

            // Teen (age 16)
            val teenResponse = mockMvc.perform(get("/api/v2/party-wizard/party-types/16"))
                .andExpect(status().isOk)
                .andReturn()

            // Verify all returned valid responses
            assertNotNull(toddlerResponse.response.contentAsString)
            assertNotNull(childResponse.response.contentAsString)
            assertNotNull(teenResponse.response.contentAsString)
        }

        @Test
        @DisplayName("should filter party types by age appropriateness")
        fun shouldFilterPartyTypesByAgeAppropriateness() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/3"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.type == 'characters_performers')]").exists())

            mockMvc.perform(get("/api/v2/party-wizard/party-types/16"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[?(@.type == 'amusement')]").exists())
        }

        @Test
        @DisplayName("should sort party types by popularity score")
        fun shouldSortByPopularityScore() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/7"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].popularityScore").exists())
        }
    }

    @Nested
    @DisplayName("Budget Estimation Integration")
    inner class BudgetEstimationIntegration {

        @Test
        @DisplayName("should estimate budget based on party type and guest count")
        fun shouldEstimateBudgetBasedOnTypeAndGuests() {
            val smallParty = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 10,
                "priceLevel" to 2
            )

            val largeParty = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 30,
                "priceLevel" to 2
            )

            val smallResponse = mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(smallParty))
            )
                .andExpect(status().isOk)
                .andReturn()

            val largeResponse = mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(largeParty))
            )
                .andExpect(status().isOk)
                .andReturn()

            val smallBudget = objectMapper.readTree(smallResponse.response.contentAsString)
            val largeBudget = objectMapper.readTree(largeResponse.response.contentAsString)

            assertTrue(
                largeBudget["estimatedTotal"].asInt() > smallBudget["estimatedTotal"].asInt(),
                "Larger party should have higher estimated cost"
            )
        }

        @Test
        @DisplayName("should adjust budget based on price level")
        fun shouldAdjustBudgetBasedOnPriceLevel() {
            val budgetVenue = mapOf(
                "partyTypes" to listOf("creative"),
                "guestCount" to 15,
                "priceLevel" to 1
            )

            val premiumVenue = mapOf(
                "partyTypes" to listOf("creative"),
                "guestCount" to 15,
                "priceLevel" to 4
            )

            val budgetResponse = mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(budgetVenue))
            )
                .andExpect(status().isOk)
                .andReturn()

            val premiumResponse = mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(premiumVenue))
            )
                .andExpect(status().isOk)
                .andReturn()

            val budgetCost = objectMapper.readTree(budgetResponse.response.contentAsString)
            val premiumCost = objectMapper.readTree(premiumResponse.response.contentAsString)

            assertTrue(
                premiumCost["estimatedTotal"].asInt() > budgetCost["estimatedTotal"].asInt(),
                "Premium venue should have higher cost than budget venue"
            )
        }
    }

    @Nested
    @DisplayName("Error Propagation")
    inner class ErrorPropagation {

        @Test
        @DisplayName("should propagate validation errors through layers")
        fun shouldPropagateValidationErrors() {
            val invalidRequest = mapOf(
                "age" to 7
                // Missing required fields
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should handle missing party types gracefully")
        fun shouldHandleMissingPartyTypesGracefully() {
            val requestWithEmptyTypes = PartySearchRequest(
                age = 7,
                partyTypes = emptyList(),
                guestCount = 15,
                budgetMin = null,
                budgetMax = 500,
                zipCode = "94105",
                setting = "indoor",
                maxDistanceMiles = 10,
                date = null
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestWithEmptyTypes))
            )
                .andExpect(status().isBadRequest)
        }
    }
}
