package com.partyscout.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.integration.mocks.TestGooglePlacesConfig
import com.partyscout.party.model.PartySearchRequest
import com.partyscout.venue.dto.*
import com.partyscout.venue.service.GooglePlacesService
import io.mockk.every
import org.junit.jupiter.api.Test
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
import reactor.core.publisher.Mono

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestGooglePlacesConfig::class)
@DisplayName("PartySearchController Integration Tests")
class PartySearchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var googlePlacesService: GooglePlacesService

    @Nested
    @DisplayName("GET /api/v2/party-wizard/party-types/{age}")
    inner class GetPartyTypes {

        @Test
        @DisplayName("should return party types for valid age")
        fun shouldReturnPartyTypesForValidAge() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/7"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].type").exists())
                .andExpect(jsonPath("$[0].displayName").exists())
                .andExpect(jsonPath("$[0].description").exists())
        }

        @Test
        @DisplayName("should return party types for toddler age")
        fun shouldReturnPartyTypesForToddler() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/3"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
        }

        @Test
        @DisplayName("should return party types for teenager")
        fun shouldReturnPartyTypesForTeenager() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/15"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
        }

        @Test
        @DisplayName("should return empty array for age 0")
        fun shouldReturnEmptyForInvalidAge() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/0"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @DisplayName("should include popularity score in response")
        fun shouldIncludePopularityScore() {
            mockMvc.perform(get("/api/v2/party-wizard/party-types/7"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].popularityScore").isNumber)
        }
    }

    @Nested
    @DisplayName("POST /api/v2/party-wizard/search")
    inner class SearchVenues {

        @Test
        @DisplayName("should accept valid search request")
        fun shouldAcceptValidRequest() {
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

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        }

        @Test
        @DisplayName("should accept request with minimal fields")
        fun shouldAcceptMinimalRequest() {
            val request = mapOf(
                "age" to 7,
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "zipCode" to "94105"
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("should return 400 for missing required fields")
        fun shouldReturn400ForMissingFields() {
            val request = mapOf(
                "age" to 7
                // Missing partyTypes, guestCount, zipCode
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should accept multiple party types")
        fun shouldAcceptMultiplePartyTypes() {
            val request = mapOf(
                "age" to 10,
                "partyTypes" to listOf("active_play", "amusement"),
                "guestCount" to 20,
                "zipCode" to "94105"
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("should accept all setting options")
        fun shouldAcceptAllSettingOptions() {
            listOf("indoor", "outdoor", "any").forEach { setting ->
                val request = mapOf(
                    "age" to 7,
                    "partyTypes" to listOf("active_play"),
                    "guestCount" to 15,
                    "zipCode" to "94105",
                    "setting" to setting
                )

                mockMvc.perform(
                    post("/api/v2/party-wizard/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                )
                    .andExpect(status().isOk)
            }
        }
    }

    @Nested
    @DisplayName("POST /api/v2/party-wizard/estimate-budget")
    inner class EstimateBudget {

        @Test
        @DisplayName("should return budget estimate")
        fun shouldReturnBudgetEstimate() {
            val request = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 15,
                "priceLevel" to 2
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.estimatedTotal").isNumber)
                .andExpect(jsonPath("$.estimatedPerPerson").isNumber)
        }

        @Test
        @DisplayName("should return higher estimate for more guests")
        fun shouldReturnHigherEstimateForMoreGuests() {
            val smallPartyRequest = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 10,
                "priceLevel" to 2
            )

            val largePartyRequest = mapOf(
                "partyTypes" to listOf("active_play"),
                "guestCount" to 30,
                "priceLevel" to 2
            )

            // Both should succeed
            mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(smallPartyRequest))
            ).andExpect(status().isOk)

            mockMvc.perform(
                post("/api/v2/party-wizard/estimate-budget")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(largePartyRequest))
            ).andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("Venue Exclusion Filter")
    inner class VenueExclusionFilter {

        private val baseRequest = mapOf(
            "age" to 7,
            "partyTypes" to listOf("active_play"),
            "guestCount" to 15,
            "zipCode" to "94105",
            "setting" to "any",
            "maxDistanceMiles" to 25
        )

        private val mockLocation = Location(lat = 37.7893, lng = -122.3932)

        @Test
        @DisplayName("should exclude venue with grocery_store type from search results")
        fun shouldExcludeGroceryStoreFromResults() {
            every { googlePlacesService.geocodeZipCode(any()) } returns Mono.just(mockLocation)
            every { googlePlacesService.searchNearbyPlaces(any(), any(), any()) } returns Mono.just(
                SearchNearbyResponse(
                    places = listOf(
                        Place(
                            id = "excluded-grocery",
                            displayName = DisplayName(text = "Test Grocery Store"),
                            formattedAddress = "1 Main St, San Francisco, CA 94105",
                            location = LatLng(latitude = 37.7900, longitude = -122.3900),
                            rating = 4.0,
                            userRatingCount = 100,
                            priceLevel = "PRICE_LEVEL_MODERATE",
                            types = listOf("grocery_store")
                        )
                    )
                )
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(baseRequest))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.venues").isArray)
                .andExpect(jsonPath("$.venues.length()").value(0))
        }

        @Test
        @DisplayName("should exclude venue with supermarket type from search results")
        fun shouldExcludeSupermarketFromResults() {
            every { googlePlacesService.geocodeZipCode(any()) } returns Mono.just(mockLocation)
            every { googlePlacesService.searchNearbyPlaces(any(), any(), any()) } returns Mono.just(
                SearchNearbyResponse(
                    places = listOf(
                        Place(
                            id = "excluded-supermarket",
                            displayName = DisplayName(text = "Test Supermarket"),
                            formattedAddress = "2 Market St, San Francisco, CA 94105",
                            location = LatLng(latitude = 37.7900, longitude = -122.3900),
                            rating = 3.8,
                            userRatingCount = 200,
                            priceLevel = "PRICE_LEVEL_INEXPENSIVE",
                            types = listOf("supermarket")
                        )
                    )
                )
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(baseRequest))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.venues").isArray)
                .andExpect(jsonPath("$.venues.length()").value(0))
        }

        @Test
        @DisplayName("should exclude venue that has both restaurant and grocery_store types")
        fun shouldExcludeMixedTypeVenueContainingExcludedType() {
            every { googlePlacesService.geocodeZipCode(any()) } returns Mono.just(mockLocation)
            every { googlePlacesService.searchNearbyPlaces(any(), any(), any()) } returns Mono.just(
                SearchNearbyResponse(
                    places = listOf(
                        Place(
                            id = "excluded-mixed",
                            displayName = DisplayName(text = "Test Restaurant Grocery"),
                            formattedAddress = "3 Blend Ave, San Francisco, CA 94105",
                            location = LatLng(latitude = 37.7900, longitude = -122.3900),
                            rating = 4.2,
                            userRatingCount = 150,
                            priceLevel = "PRICE_LEVEL_MODERATE",
                            types = listOf("restaurant", "grocery_store")
                        )
                    )
                )
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(baseRequest))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.venues").isArray)
                .andExpect(jsonPath("$.venues.length()").value(0))
        }

        @Test
        @DisplayName("should include venue with amusement_center type in search results")
        fun shouldIncludeAmusementCenterInResults() {
            every { googlePlacesService.geocodeZipCode(any()) } returns Mono.just(mockLocation)
            every { googlePlacesService.searchNearbyPlaces(any(), any(), any()) } returns Mono.just(
                SearchNearbyResponse(
                    places = listOf(
                        Place(
                            id = "allowed-amusement",
                            displayName = DisplayName(text = "Test Amusement Center"),
                            formattedAddress = "4 Fun Blvd, San Francisco, CA 94105",
                            location = LatLng(latitude = 37.7900, longitude = -122.3900),
                            rating = 4.5,
                            userRatingCount = 300,
                            priceLevel = "PRICE_LEVEL_MODERATE",
                            types = listOf("amusement_center")
                        )
                    )
                )
            )

            mockMvc.perform(
                post("/api/v2/party-wizard/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(baseRequest))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.venues").isArray)
                .andExpect(jsonPath("$.venues.length()").value(1))
                .andExpect(jsonPath("$.venues[0].name").value("Test Amusement Center"))
        }
    }

    @Nested
    @DisplayName("CORS Configuration")
    inner class CorsTests {

        @Test
        @DisplayName("should allow requests from localhost:5173")
        fun shouldAllowLocalhost5173() {
            mockMvc.perform(
                options("/api/v2/party-wizard/party-types/7")
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "GET")
            )
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("should allow requests from Cloud Run")
        fun shouldAllowCloudRun() {
            mockMvc.perform(
                options("/api/v2/party-wizard/party-types/7")
                    .header("Origin", "https://partyscout-frontend.run.app")
                    .header("Access-Control-Request-Method", "GET")
            )
                .andExpect(status().isOk)
        }
    }
}
