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
@DisplayName("API Validation Integration Tests")
class ApiValidationIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Nested
    @DisplayName("Request Validation")
    inner class RequestValidation {

        @Test
        @DisplayName("should reject search request with missing age")
        fun shouldRejectSearchRequestWithMissingAge() {
            val invalidRequest = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "zipCode" to "94105",
                "setting" to "indoor",
                "maxDistanceMiles" to 10
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should reject search request with missing party types")
        fun shouldRejectSearchRequestWithMissingPartyTypes() {
            val invalidRequest = mapOf(
                "age" to 7,
                "guestCount" to 15,
                "zipCode" to "94105",
                "setting" to "indoor",
                "maxDistanceMiles" to 10
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should reject search request with empty party types")
        fun shouldRejectSearchRequestWithEmptyPartyTypes() {
            val invalidRequest = PartySearchRequest(
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
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should reject search request with missing ZIP code")
        fun shouldRejectSearchRequestWithMissingZipCode() {
            val invalidRequest = mapOf(
                "age" to 7,
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "setting" to "indoor",
                "maxDistanceMiles" to 10
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should reject search request with negative guest count")
        fun shouldRejectSearchRequestWithNegativeGuestCount() {
            val invalidRequest = PartySearchRequest(
                age = 7,
                partyTypes = listOf("active_play"),
                guestCount = -5,
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
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should accept valid search request")
        fun shouldAcceptValidSearchRequest() {
            val validRequest = PartySearchRequest(
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
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("should accept request with optional fields omitted")
        fun shouldAcceptRequestWithOptionalFieldsOmitted() {
            val minimalRequest = mapOf(
                "age" to 7,
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "zipCode" to "94105",
                "setting" to "any",
                "maxDistanceMiles" to 10
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(minimalRequest))
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("Budget Estimate Validation")
    inner class BudgetEstimateValidation {

        @Test
        @DisplayName("should reject budget estimate with missing party type")
        fun shouldRejectBudgetEstimateWithMissingPartyType() {
            val invalidRequest = mapOf(
                "guestCount" to 15,
                "priceLevel" to 2
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should accept valid budget estimate request")
        fun shouldAcceptValidBudgetEstimateRequest() {
            val validRequest = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "priceLevel" to 2
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.estimatedTotal").isNumber)
                .andExpect(jsonPath("$.estimatedPerPerson").isNumber)
        }
    }

    @Nested
    @DisplayName("CORS Configuration")
    inner class CorsConfiguration {

        @Test
        @DisplayName("should allow requests from allowed origins")
        fun shouldAllowRequestsFromAllowedOrigins() {
            mockMvc.perform(
                options("/api/v2/party-wizard/party-types/7")
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "GET")
            )
                .andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("Error Response Format")
    inner class ErrorResponseFormat {

        @Test
        @DisplayName("should return consistent error format for validation errors")
        fun shouldReturnConsistentErrorFormatForValidationErrors() {
            val invalidRequest = mapOf("age" to 7) // Missing required fields

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest))
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        }

        @Test
        @DisplayName("should return consistent error format for malformed JSON")
        fun shouldReturnConsistentErrorFormatForMalformedJson() {
            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 404 for non-existent endpoints")
        fun shouldReturn404ForNonExistentEndpoints() {
            mockMvc.perform(get("/api/v2/party-wizard/non-existent"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("Content-Type Handling")
    inner class ContentTypeHandling {

        @Test
        @DisplayName("should require JSON content type for POST requests")
        fun shouldRequireJsonContentTypeForPostRequests() {
            val validRequest = PartySearchRequest(
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

            // Without content type
            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .content(objectMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isUnsupportedMediaType)
        }

        @Test
        @DisplayName("should return JSON content type for successful responses")
        fun shouldReturnJsonContentTypeForSuccessfulResponses() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/7"))
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        }
    }

    @Nested
    @DisplayName("Party Types Endpoint Validation")
    inner class PartyTypesEndpointValidation {

        @Test
        @DisplayName("should return party types for valid age")
        fun shouldReturnPartyTypesForValidAge() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/7"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
        }

        @Test
        @DisplayName("should return empty array for age 0")
        fun shouldReturnEmptyArrayForAge0() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/0"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
        }

        @Test
        @DisplayName("should handle non-numeric age parameter")
        fun shouldHandleNonNumericAgeParameter() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/abc"))
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("Request Size Limits")
    inner class RequestSizeLimits {

        @Test
        @DisplayName("should accept request with many party types")
        fun shouldAcceptRequestWithManyPartyTypes() {
            val requestWithManyTypes = PartySearchRequest(
                age = 7,
                partyTypes = listOf("active_play", "creative", "amusement", "outdoor", "characters_performers"),
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
                    .content(objectMapper.writeValueAsString(requestWithManyTypes))
            )
                .andExpect(status().isOk)
        }
    }
}
