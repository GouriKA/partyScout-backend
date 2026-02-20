package com.partyscout.unit

import com.partyscout.config.GooglePlacesConfig
import com.partyscout.service.GooglePlacesException
import com.partyscout.service.GooglePlacesService
import com.partyscout.dto.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@DisplayName("GooglePlacesService")
class GooglePlacesServiceTest {

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
        config = GooglePlacesConfig().apply {
            apiKey = "test-api-key"
        }
        requestHeadersUriSpec = mockk(relaxed = true)
        requestHeadersSpec = mockk(relaxed = true)
        requestBodyUriSpec = mockk(relaxed = true)
        requestBodySpec = mockk(relaxed = true)
        responseSpec = mockk(relaxed = true)

        googlePlacesService = GooglePlacesService(webClient, config)
    }

    @Nested
    @DisplayName("geocodeZipCode")
    inner class GeocodeZipCode {

        @Test
        @DisplayName("should return Location for valid ZIP code")
        fun shouldReturnLocationForValidZipCode() {
            // Given
            val zipCode = "94105"
            val expectedLocation = Location(lat = 37.7893, lng = -122.3932)
            val geocodingResponse = GeocodingResponse(
                results = listOf(
                    GeocodingResult(
                        formattedAddress = "San Francisco, CA 94105, USA",
                        geometry = Geometry(location = expectedLocation, locationType = "APPROXIMATE"),
                        placeId = "ChIJIQBpAG2ahYAR_6128GcTUEo"
                    )
                ),
                status = "OK"
            )

            every { webClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri(any<String>(), any(), any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<GeocodingResponse>() } returns Mono.just(geocodingResponse)

            // When & Then
            StepVerifier.create(googlePlacesService.geocodeZipCode(zipCode))
                .expectNext(expectedLocation)
                .verifyComplete()
        }

        @Test
        @DisplayName("should throw exception for invalid ZIP code")
        fun shouldThrowExceptionForInvalidZipCode() {
            // Given
            val zipCode = "00000"
            val geocodingResponse = GeocodingResponse(
                results = emptyList(),
                status = "ZERO_RESULTS"
            )

            every { webClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri(any<String>(), any(), any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<GeocodingResponse>() } returns Mono.just(geocodingResponse)

            // When & Then
            StepVerifier.create(googlePlacesService.geocodeZipCode(zipCode))
                .expectError(GooglePlacesException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should include error message in exception when API returns error")
        fun shouldIncludeErrorMessageInException() {
            // Given
            val zipCode = "94105"
            val geocodingResponse = GeocodingResponse(
                results = emptyList(),
                status = "REQUEST_DENIED",
                error_message = "The provided API key is invalid."
            )

            every { webClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri(any<String>(), any(), any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<GeocodingResponse>() } returns Mono.just(geocodingResponse)

            // When & Then
            StepVerifier.create(googlePlacesService.geocodeZipCode(zipCode))
                .expectErrorMatches { error ->
                    error is GooglePlacesException &&
                    error.message?.contains("REQUEST_DENIED") == true &&
                    error.message?.contains("The provided API key is invalid.") == true
                }
                .verify()
        }

        @Test
        @DisplayName("should handle API network errors gracefully")
        fun shouldHandleApiNetworkErrors() {
            // Given
            val zipCode = "94105"

            every { webClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri(any<String>(), any(), any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<GeocodingResponse>() } returns
                Mono.error(RuntimeException("Network error"))

            // When & Then
            StepVerifier.create(googlePlacesService.geocodeZipCode(zipCode))
                .expectError(RuntimeException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("searchNearbyPlaces")
    inner class SearchNearbyPlaces {

        @Test
        @DisplayName("should return places for valid location and keywords")
        fun shouldReturnPlacesForValidLocationAndKeywords() {
            // Given
            val location = Location(lat = 37.7893, lng = -122.3932)
            val keywords = listOf("playground", "amusement_park")
            val expectedPlaces = listOf(
                Place(
                    id = "place-1",
                    displayName = DisplayName(text = "Sky Zone Trampoline Park"),
                    formattedAddress = "123 Jump St, San Francisco, CA",
                    location = LatLng(latitude = 37.7900, longitude = -122.3900),
                    rating = 4.5,
                    userRatingCount = 234,
                    priceLevel = "PRICE_LEVEL_MODERATE",
                    types = listOf("amusement_center", "gym")
                )
            )
            val searchResponse = SearchNearbyResponse(places = expectedPlaces)

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns Mono.just(searchResponse)

            // When & Then
            StepVerifier.create(googlePlacesService.searchNearbyPlaces(location, keywords))
                .assertNext { response ->
                    assertEquals(1, response.places?.size)
                    assertEquals("Sky Zone Trampoline Park", response.places?.get(0)?.displayName?.text)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty response when no places found")
        fun shouldReturnEmptyResponseWhenNoPlacesFound() {
            // Given
            val location = Location(lat = 0.0, lng = 0.0) // Middle of ocean
            val keywords = listOf("playground")
            val searchResponse = SearchNearbyResponse(places = emptyList())

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns Mono.just(searchResponse)

            // When & Then
            StepVerifier.create(googlePlacesService.searchNearbyPlaces(location, keywords))
                .assertNext { response ->
                    assertTrue(response.places?.isEmpty() ?: true)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should use default radius when not specified")
        fun shouldUseDefaultRadiusWhenNotSpecified() {
            // Given
            val location = Location(lat = 37.7893, lng = -122.3932)
            val keywords = listOf("playground")
            val searchResponse = SearchNearbyResponse(places = emptyList())

            val capturedRequest = slot<SearchNearbyRequest>()

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(capture(capturedRequest)) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns Mono.just(searchResponse)

            // When
            googlePlacesService.searchNearbyPlaces(location, keywords).block()

            // Then
            assertEquals(5000.0, capturedRequest.captured.locationRestriction.circle.radius)
        }

        @Test
        @DisplayName("should use custom radius when specified")
        fun shouldUseCustomRadiusWhenSpecified() {
            // Given
            val location = Location(lat = 37.7893, lng = -122.3932)
            val keywords = listOf("playground")
            val customRadius = 10000
            val searchResponse = SearchNearbyResponse(places = emptyList())

            val capturedRequest = slot<SearchNearbyRequest>()

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(capture(capturedRequest)) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns Mono.just(searchResponse)

            // When
            googlePlacesService.searchNearbyPlaces(location, keywords, customRadius).block()

            // Then
            assertEquals(customRadius.toDouble(), capturedRequest.captured.locationRestriction.circle.radius)
        }

        @Test
        @DisplayName("should handle API errors gracefully")
        fun shouldHandleApiErrors() {
            // Given
            val location = Location(lat = 37.7893, lng = -122.3932)
            val keywords = listOf("playground")

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns
                Mono.error(RuntimeException("API rate limit exceeded"))

            // When & Then
            StepVerifier.create(googlePlacesService.searchNearbyPlaces(location, keywords))
                .expectError(RuntimeException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("getPlaceDetails")
    inner class GetPlaceDetails {

        @Test
        @DisplayName("should return place details for valid place ID")
        fun shouldReturnPlaceDetailsForValidPlaceId() {
            // Given
            val placeId = "ChIJIQBpAG2ahYAR_6128GcTUEo"
            val expectedDetails = PlaceDetails(
                placeId = placeId,
                name = "Sky Zone Trampoline Park",
                formattedAddress = "123 Jump St, San Francisco, CA",
                formattedPhoneNumber = "(415) 555-0123",
                website = "https://skyzone.com",
                rating = 4.5,
                userRatingsTotal = 234,
                priceLevel = 2
            )
            val detailsResponse = PlaceDetailsResponse(
                result = expectedDetails,
                status = "OK"
            )

            every { webClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri(any<String>(), any(), any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<PlaceDetailsResponse>() } returns Mono.just(detailsResponse)

            // When & Then
            StepVerifier.create(googlePlacesService.getPlaceDetails(placeId))
                .assertNext { details ->
                    assertEquals("Sky Zone Trampoline Park", details.name)
                    assertEquals("(415) 555-0123", details.formattedPhoneNumber)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should throw exception for invalid place ID")
        fun shouldThrowExceptionForInvalidPlaceId() {
            // Given
            val placeId = "invalid-place-id"
            val detailsResponse = PlaceDetailsResponse(
                result = PlaceDetails(
                    placeId = "",
                    name = "",
                    formattedAddress = ""
                ),
                status = "INVALID_REQUEST"
            )

            every { webClient.get() } returns requestHeadersUriSpec
            every { requestHeadersUriSpec.uri(any<String>(), any(), any()) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<PlaceDetailsResponse>() } returns Mono.just(detailsResponse)

            // When & Then
            StepVerifier.create(googlePlacesService.getPlaceDetails(placeId))
                .expectError(GooglePlacesException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("mapKeywordsToTypes")
    inner class MapKeywordsToTypes {

        // Note: mapKeywordsToTypes is private, so we test it indirectly through searchNearbyPlaces

        @Test
        @DisplayName("should map playground to park and playground types")
        fun shouldMapPlaygroundToTypes() {
            // Given
            val location = Location(lat = 37.7893, lng = -122.3932)
            val keywords = listOf("playground")
            val searchResponse = SearchNearbyResponse(places = emptyList())

            val capturedRequest = slot<SearchNearbyRequest>()

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(capture(capturedRequest)) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns Mono.just(searchResponse)

            // When
            googlePlacesService.searchNearbyPlaces(location, keywords).block()

            // Then
            val includedTypes = capturedRequest.captured.includedTypes
            assertTrue(includedTypes.contains("park"))
            assertTrue(includedTypes.contains("playground"))
        }

        @Test
        @DisplayName("should map arcade to amusement_center")
        fun shouldMapArcadeToAmusementCenter() {
            // Given
            val location = Location(lat = 37.7893, lng = -122.3932)
            val keywords = listOf("arcade")
            val searchResponse = SearchNearbyResponse(places = emptyList())

            val capturedRequest = slot<SearchNearbyRequest>()

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(capture(capturedRequest)) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns Mono.just(searchResponse)

            // When
            googlePlacesService.searchNearbyPlaces(location, keywords).block()

            // Then
            assertTrue(capturedRequest.captured.includedTypes.contains("amusement_center"))
        }

        @Test
        @DisplayName("should deduplicate types when multiple keywords map to same type")
        fun shouldDeduplicateTypes() {
            // Given
            val location = Location(lat = 37.7893, lng = -122.3932)
            val keywords = listOf("arcade", "amusement_park") // Both map to amusement_center
            val searchResponse = SearchNearbyResponse(places = emptyList())

            val capturedRequest = slot<SearchNearbyRequest>()

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(capture(capturedRequest)) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns Mono.just(searchResponse)

            // When
            googlePlacesService.searchNearbyPlaces(location, keywords).block()

            // Then
            val includedTypes = capturedRequest.captured.includedTypes
            // amusement_center should appear only once
            assertEquals(1, includedTypes.count { it == "amusement_center" })
        }

        @Test
        @DisplayName("should pass through unknown keywords as-is")
        fun shouldPassThroughUnknownKeywords() {
            // Given
            val location = Location(lat = 37.7893, lng = -122.3932)
            val keywords = listOf("custom_venue_type")
            val searchResponse = SearchNearbyResponse(places = emptyList())

            val capturedRequest = slot<SearchNearbyRequest>()

            every { webClient.post() } returns requestBodyUriSpec
            every { requestBodyUriSpec.uri(any<String>()) } returns requestBodySpec
            every { requestBodySpec.header(any(), any()) } returns requestBodySpec
            every { requestBodySpec.bodyValue(capture(capturedRequest)) } returns requestHeadersSpec
            every { requestHeadersSpec.retrieve() } returns responseSpec
            every { responseSpec.bodyToMono<SearchNearbyResponse>() } returns Mono.just(searchResponse)

            // When
            googlePlacesService.searchNearbyPlaces(location, keywords).block()

            // Then
            assertTrue(capturedRequest.captured.includedTypes.contains("custom_venue_type"))
        }
    }
}
