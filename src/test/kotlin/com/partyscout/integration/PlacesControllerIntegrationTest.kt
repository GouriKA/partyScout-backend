package com.partyscout.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.integration.mocks.TestGooglePlacesConfig
import com.partyscout.venue.dto.AutocompleteSuggestion
import com.partyscout.venue.dto.LocalizedText
import com.partyscout.venue.dto.NewAutocompleteResponse
import com.partyscout.venue.dto.PlacePrediction
import com.partyscout.venue.service.GooglePlacesService
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import reactor.core.publisher.Mono

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestGooglePlacesConfig::class)
@DisplayName("PlacesController Integration Tests")
class PlacesControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var googlePlacesService: GooglePlacesService

    // ── Helper: build a fake autocomplete Mono ─────────────────────────────

    private fun autocompleteMonoOf(vararg cities: String): Mono<List<String>> =
        Mono.just(cities.toList())

    private fun autocompleteResponseOf(vararg cityTexts: String) = NewAutocompleteResponse(
        suggestions = cityTexts.map { text ->
            AutocompleteSuggestion(
                placePrediction = PlacePrediction(
                    placeId = "place-${text.hashCode()}",
                    text = LocalizedText(text = text),
                )
            )
        }
    )

    // ── GET /api/v2/places/autocomplete ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v2/places/autocomplete")
    inner class AutocompleteEndpoint {

        @Test
        @DisplayName("returns 200 with a list of city strings for a valid input")
        fun returnsListOfCitiesForValidInput() {
            every { googlePlacesService.autocompleteCity("Lon") } returns
                autocompleteMonoOf("London, UK", "Long Beach, CA", "Longview, TX")

            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "Lon"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0]").value("London, UK"))
                .andExpect(jsonPath("$[1]").value("Long Beach, CA"))
                .andExpect(jsonPath("$[2]").value("Longview, TX"))
        }

        @Test
        @DisplayName("returns 200 with an empty list for blank input")
        fun returnsEmptyListForBlankInput() {
            // Controller short-circuits and returns empty without calling the service
            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", ""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @DisplayName("returns 200 with an empty list for whitespace-only input")
        fun returnsEmptyListForWhitespaceInput() {
            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "   "))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @DisplayName("returns at most 5 city strings")
        fun returnsAtMostFiveCities() {
            every { googlePlacesService.autocompleteCity(any()) } returns
                autocompleteMonoOf(
                    "City A, CA",
                    "City B, CA",
                    "City C, CA",
                    "City D, CA",
                    "City E, CA",
                )

            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "City"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(5)))
        }

        @Test
        @DisplayName("response items are plain strings (not objects)")
        fun responseItemsAreStrings() {
            every { googlePlacesService.autocompleteCity("Au") } returns
                autocompleteMonoOf("Austin, TX", "Aurora, CO")

            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "Au"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0]").isString)
                .andExpect(jsonPath("$[1]").isString)
        }

        @Test
        @DisplayName("returns 200 with empty list when service returns no results")
        fun returns200WithEmptyListWhenServiceReturnsEmpty() {
            every { googlePlacesService.autocompleteCity(any()) } returns Mono.just(emptyList())

            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "ZzZz"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @DisplayName("mocked Google Places API response is correctly wired")
        fun mockedGooglePlacesApiResponseIsWired() {
            every { googlePlacesService.autocompleteCity("Bris") } returns
                autocompleteMonoOf("Bristol, UK", "Brisbane, QLD")

            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "Bris"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("Bristol, UK"))
                .andExpect(jsonPath("$[1]").value("Brisbane, QLD"))
        }

        @Test
        @DisplayName("passes the exact input string to the service")
        fun passesExactInputToService() {
            val capturedInput = mutableListOf<String>()
            every { googlePlacesService.autocompleteCity(capture(capturedInput)) } returns
                Mono.just(emptyList())

            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "Man"))
                .andExpect(status().isOk)

            org.junit.jupiter.api.Assertions.assertEquals(1, capturedInput.size)
            org.junit.jupiter.api.Assertions.assertEquals("Man", capturedInput[0])
        }

        @Test
        @DisplayName("returns 200 for single-character input (service decides whether to fetch)")
        fun returns200ForSingleCharInput() {
            every { googlePlacesService.autocompleteCity(any()) } returns Mono.just(emptyList())

            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "L"))
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("returns 200 for multi-word city query")
        fun returns200ForMultiWordQuery() {
            every { googlePlacesService.autocompleteCity("New York") } returns
                autocompleteMonoOf("New York, NY", "Newark, NJ")

            mockMvc.perform(get("/api/v2/places/autocomplete").param("input", "New York"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0]").value("New York, NY"))
        }
    }
}
