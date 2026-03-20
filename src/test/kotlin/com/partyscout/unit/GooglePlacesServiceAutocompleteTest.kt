package com.partyscout.unit

import com.partyscout.venue.config.GooglePlacesConfig
import com.partyscout.venue.dto.AutocompleteSuggestion
import com.partyscout.venue.dto.LocalizedText
import com.partyscout.venue.dto.NewAutocompleteResponse
import com.partyscout.venue.dto.PlacePrediction
import com.partyscout.venue.service.GooglePlacesException
import com.partyscout.venue.service.GooglePlacesService
import com.partyscout.venue.dto.GeocodingResponse
import com.partyscout.venue.dto.GeocodingResult
import com.partyscout.venue.dto.Geometry
import com.partyscout.venue.dto.Location
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName as JUnit5DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@JUnit5DisplayName("GooglePlacesService — autocompleteCity & geocodeCity")
class GooglePlacesServiceAutocompleteTest {

    private lateinit var googlePlacesService: GooglePlacesService
    private lateinit var webClient: WebClient
    private lateinit var config: GooglePlacesConfig

    private lateinit var requestHeadersUriSpec: WebClient.RequestHeadersUriSpec<*>
    private lateinit var requestHeadersSpec: WebClient.RequestHeadersSpec<*>
    private lateinit var requestBodyUriSpec: WebClient.RequestBodyUriSpec
    private lateinit var requestBodySpec: WebClient.RequestBodySpec
    private lateinit var responseSpec: WebClient.ResponseSpec

    @BeforeEach
    fun setUp() {
        webClient = mockk(relaxed = true)
        config = GooglePlacesConfig().apply { apiKey = "test-api-key" }
        requestHeadersUriSpec = mockk(relaxed = true)
        requestHeadersSpec = mockk(relaxed = true)
        requestBodyUriSpec = mockk(relaxed = true)
        requestBodySpec = mockk(relaxed = true)
        responseSpec = mockk(relaxed = true)

        googlePlacesService = GooglePlacesService(webClient, config)
    }

    // ── Helper: build a fake autocomplete response ─────────────────────────

    private fun autocompleteResponse(vararg cityTexts: String) = NewAutocompleteResponse(
        suggestions = cityTexts.map { text ->
            AutocompleteSuggestion(
                placePrediction = PlacePrediction(
                    placeId = "place-${text.hashCode()}",
                    text = LocalizedText(text = text),
                )
            )
        }
    )

    private fun stubAutocompletePost(response: Mono<NewAutocompleteResponse>) {
        every { webClient.post() } returns requestBodyUriSpec
        every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
        every { requestBodySpec.header(any(), any()) } returns requestBodySpec
        every { requestBodySpec.bodyValue(any()) } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
        every { responseSpec.bodyToMono<NewAutocompleteResponse>() } returns response
    }

    // ── autocompleteCity ───────────────────────────────────────────────────

    @Nested
    @JUnit5DisplayName("autocompleteCity")
    inner class AutocompleteCity {

        @Test
        @JUnit5DisplayName("returns empty list for blank input without calling API")
        fun returnsEmptyListForBlankInput() {
            // Blank input must short-circuit before any HTTP call
            StepVerifier.create(googlePlacesService.autocompleteCity(""))
                .expectNext(emptyList())
                .verifyComplete()

            StepVerifier.create(googlePlacesService.autocompleteCity("   "))
                .expectNext(emptyList())
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("returns city names stripped of trailing ', USA'")
        fun stripsUsaSuffix() {
            stubAutocompletePost(
                Mono.just(
                    autocompleteResponse(
                        "Austin, TX, USA",
                        "Atlanta, GA, USA",
                    )
                )
            )

            StepVerifier.create(googlePlacesService.autocompleteCity("Au"))
                .assertNext { results ->
                    assertTrue(results.none { it.endsWith(", USA") },
                        "All results must have ', USA' stripped")
                    assertTrue(results.contains("Austin, TX"))
                    assertTrue(results.contains("Atlanta, GA"))
                }
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("strips ', USA' only from suffix, not from middle of name")
        fun stripsUsaSuffixOnlyFromEnd() {
            stubAutocompletePost(
                Mono.just(autocompleteResponse("Las Vegas, NV, USA"))
            )

            StepVerifier.create(googlePlacesService.autocompleteCity("Las"))
                .assertNext { results ->
                    assertEquals("Las Vegas, NV", results[0])
                }
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("returns at most 5 results even if API returns more")
        fun returnsAtMostFiveResults() {
            stubAutocompletePost(
                Mono.just(
                    autocompleteResponse(
                        "City A, CA, USA",
                        "City B, CA, USA",
                        "City C, CA, USA",
                        "City D, CA, USA",
                        "City E, CA, USA",
                        "City F, CA, USA", // 6th — must be dropped
                        "City G, CA, USA",
                    )
                )
            )

            StepVerifier.create(googlePlacesService.autocompleteCity("City"))
                .assertNext { results ->
                    assertEquals(5, results.size, "Should return at most 5 results")
                }
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("returns exactly the results when API returns fewer than 5")
        fun returnsFewerThan5WhenApiReturnsFewer() {
            stubAutocompletePost(
                Mono.just(autocompleteResponse("London, UK, USA", "Leeds, UK, USA"))
            )

            StepVerifier.create(googlePlacesService.autocompleteCity("Lo"))
                .assertNext { results ->
                    assertEquals(2, results.size)
                }
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("returns empty list when API response has no suggestions")
        fun returnsEmptyListWhenNoSuggestions() {
            stubAutocompletePost(
                Mono.just(NewAutocompleteResponse(suggestions = emptyList()))
            )

            StepVerifier.create(googlePlacesService.autocompleteCity("Zzz"))
                .expectNext(emptyList())
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("returns empty list when API response suggestions is null")
        fun returnsEmptyListWhenSuggestionsNull() {
            stubAutocompletePost(
                Mono.just(NewAutocompleteResponse(suggestions = null))
            )

            StepVerifier.create(googlePlacesService.autocompleteCity("Zzz"))
                .expectNext(emptyList())
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("returns empty list on API error")
        fun returnsEmptyListOnApiError() {
            stubAutocompletePost(Mono.error(RuntimeException("API error")))

            StepVerifier.create(googlePlacesService.autocompleteCity("Lo"))
                .expectNext(emptyList())
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("skips suggestions whose placePrediction.text is null")
        fun skipsSuggestionsWithNullText() {
            stubAutocompletePost(
                Mono.just(
                    NewAutocompleteResponse(
                        suggestions = listOf(
                            AutocompleteSuggestion(placePrediction = PlacePrediction(text = null)),
                            AutocompleteSuggestion(
                                placePrediction = PlacePrediction(
                                    text = LocalizedText(text = "Boston, MA, USA")
                                )
                            ),
                        )
                    )
                )
            )

            StepVerifier.create(googlePlacesService.autocompleteCity("Bo"))
                .assertNext { results ->
                    assertEquals(1, results.size)
                    assertEquals("Boston, MA", results[0])
                }
                .verifyComplete()
        }
    }

    // ── geocodeCity ────────────────────────────────────────────────────────

    @Nested
    @JUnit5DisplayName("geocodeCity")
    inner class GeocodeCity {

        private fun stubGeocode(response: Mono<GeocodingResponse>) {
            every { webClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri(any<String>(), any(), any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<GeocodingResponse>() } returns response
        }

        @Test
        @JUnit5DisplayName("constructs correct geocoding URL with city and API key")
        fun constructsCorrectUrl() {
            val capturedUri = slot<String>()
            val geocodingResponse = GeocodingResponse(
                results = listOf(
                    GeocodingResult(
                        formattedAddress = "London, UK",
                        geometry = Geometry(
                            location = Location(lat = 51.5074, lng = -0.1278),
                            locationType = "APPROXIMATE"
                        ),
                        placeId = "ChIJdd4hrwug2EcRmSrV3Vo6llI"
                    )
                ),
                status = "OK"
            )

            every { webClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri(capture(capturedUri), any(), any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<GeocodingResponse>() } returns Mono.just(geocodingResponse)

            googlePlacesService.geocodeCity("London").block()

            assertTrue(capturedUri.captured.contains("maps.googleapis.com/maps/api/geocode/json"),
                "URL must point to Google Geocoding API")
        }

        @Test
        @JUnit5DisplayName("returns Location for valid city")
        fun returnsLocationForValidCity() {
            val expectedLocation = Location(lat = 51.5074, lng = -0.1278)
            val geocodingResponse = GeocodingResponse(
                results = listOf(
                    GeocodingResult(
                        formattedAddress = "London, UK",
                        geometry = Geometry(location = expectedLocation, locationType = "APPROXIMATE"),
                        placeId = "abc123"
                    )
                ),
                status = "OK"
            )
            stubGeocode(Mono.just(geocodingResponse))

            StepVerifier.create(googlePlacesService.geocodeCity("London"))
                .expectNext(expectedLocation)
                .verifyComplete()
        }

        @Test
        @JUnit5DisplayName("throws GooglePlacesException on ZERO_RESULTS status")
        fun throwsExceptionOnZeroResults() {
            stubGeocode(
                Mono.just(GeocodingResponse(results = emptyList(), status = "ZERO_RESULTS"))
            )

            StepVerifier.create(googlePlacesService.geocodeCity("NonExistentCity"))
                .expectError(GooglePlacesException::class.java)
                .verify()
        }

        @Test
        @JUnit5DisplayName("throws GooglePlacesException on REQUEST_DENIED status")
        fun throwsExceptionOnRequestDenied() {
            stubGeocode(
                Mono.just(
                    GeocodingResponse(
                        results = emptyList(),
                        status = "REQUEST_DENIED",
                        error_message = "API key invalid"
                    )
                )
            )

            StepVerifier.create(googlePlacesService.geocodeCity("London"))
                .expectErrorMatches { it is GooglePlacesException && it.message!!.contains("REQUEST_DENIED") }
                .verify()
        }

        @Test
        @JUnit5DisplayName("exception message includes error_message from Google API")
        fun exceptionMessageIncludesGoogleErrorMessage() {
            stubGeocode(
                Mono.just(
                    GeocodingResponse(
                        results = emptyList(),
                        status = "REQUEST_DENIED",
                        error_message = "This API key is not authorised."
                    )
                )
            )

            StepVerifier.create(googlePlacesService.geocodeCity("London"))
                .expectErrorMatches { err ->
                    err is GooglePlacesException &&
                        err.message!!.contains("This API key is not authorised.")
                }
                .verify()
        }

        @Test
        @JUnit5DisplayName("propagates network error as-is")
        fun propagatesNetworkError() {
            stubGeocode(Mono.error(RuntimeException("Connection refused")))

            StepVerifier.create(googlePlacesService.geocodeCity("London"))
                .expectError(RuntimeException::class.java)
                .verify()
        }
    }
}
