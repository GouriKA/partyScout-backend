package com.partyscout.unit

import com.partyscout.dto.*
import com.partyscout.service.GooglePlacesException
import com.partyscout.service.GooglePlacesService
import com.partyscout.service.VenueSearchService
import com.partyscout.unit.mocks.MockPlaceFactory.createMockPlace
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@DisplayName("VenueSearchService")
class VenueSearchServiceTest {

    private lateinit var venueSearchService: VenueSearchService
    private lateinit var googlePlacesService: GooglePlacesService

    @BeforeEach
    fun setUp() {
        googlePlacesService = mockk(relaxed = true)
        venueSearchService = VenueSearchService(googlePlacesService)
    }

    @Nested
    @DisplayName("searchVenues")
    inner class SearchVenues {

        @Test
        @DisplayName("should return enriched venues for valid age and ZIP")
        fun shouldReturnEnrichedVenuesForValidAgeAndZip() {
            // Given
            val age = 7
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)
            val places = listOf(
                createMockPlace(id = "venue-1", name = "Sky Zone Trampoline Park"),
                createMockPlace(id = "venue-2", name = "Chuck E. Cheese")
            )

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = places))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(2, venues.size)
                    assertEquals("Sky Zone Trampoline Park", venues[0].name)
                    assertEquals("Chuck E. Cheese", venues[1].name)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty list when geocoding fails")
        fun shouldReturnEmptyListWhenGeocodingFails() {
            // Given
            val age = 7
            val zipCode = "00000"

            every { googlePlacesService.geocodeZipCode(zipCode) } returns
                Mono.error(GooglePlacesException("Invalid ZIP code"))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertTrue(venues.isEmpty())
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty list when no places found")
        fun shouldReturnEmptyListWhenNoPlacesFound() {
            // Given
            val age = 7
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = emptyList()))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertTrue(venues.isEmpty())
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should calculate distance correctly")
        fun shouldCalculateDistanceCorrectly() {
            // Given
            val age = 7
            val zipCode = "94105"
            val searchLocation = Location(lat = 37.7893, lng = -122.3932)
            val place = createMockPlace(lat = 37.8000, lng = -122.4000) // About 1 mile away

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(searchLocation)
            every { googlePlacesService.searchNearbyPlaces(searchLocation, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = listOf(place)))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(1, venues.size)
                    val distance = venues[0].distanceInMiles
                    assertNotNull(distance)
                    assertTrue(distance!! > 0)
                    assertTrue(distance < 5) // Should be relatively close
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle places with missing location")
        fun shouldHandlePlacesWithMissingLocation() {
            // Given
            val age = 7
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)
            val placeWithLocation = createMockPlace(id = "venue-1", name = "Valid Venue")
            val placeWithoutLocation = Place(
                id = "venue-2",
                displayName = DisplayName(text = "Invalid Venue"),
                formattedAddress = "123 Test St",
                location = null, // Missing location
                rating = 4.0
            )

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = listOf(placeWithLocation, placeWithoutLocation)))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(1, venues.size) // Only valid venue should be included
                    assertEquals("Valid Venue", venues[0].name)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("searchPartyOptions")
    inner class SearchPartyOptions {

        @Test
        @DisplayName("should return simplified venue options")
        fun shouldReturnSimplifiedVenueOptions() {
            // Given
            val age = 7
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)
            val places = listOf(createMockPlace())

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = places))

            // When & Then
            StepVerifier.create(venueSearchService.searchPartyOptions(age, zipCode))
                .assertNext { options ->
                    assertEquals(1, options.size)
                    assertNotNull(options[0].id)
                    assertNotNull(options[0].name)
                    assertNotNull(options[0].type)
                    assertTrue(options[0].distance >= 0)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getKeywordsForAge")
    inner class GetKeywordsForAge {

        // Note: getKeywordsForAge is private, so we test indirectly through searchVenues

        @Test
        @DisplayName("should use child-appropriate keywords for age 7")
        fun shouldUseChildKeywordsForAge7() {
            // Given
            val age = 7
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = emptyList()))

            val capturedKeywords = slot<List<String>>()

            every { googlePlacesService.searchNearbyPlaces(location, capture(capturedKeywords), any()) } returns
                Mono.just(SearchNearbyResponse(places = emptyList()))

            // When
            venueSearchService.searchVenues(age, zipCode).block()

            // Then
            val keywords = capturedKeywords.captured
            assertTrue(keywords.contains("playground"))
            assertTrue(keywords.contains("amusement_park"))
            assertTrue(keywords.contains("bowling_alley"))
            assertFalse(keywords.contains("bar"))
        }

        @Test
        @DisplayName("should use teen-appropriate keywords for age 15")
        fun shouldUseTeenKeywordsForAge15() {
            // Given
            val age = 15
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)

            val capturedKeywords = slot<List<String>>()

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, capture(capturedKeywords), any()) } returns
                Mono.just(SearchNearbyResponse(places = emptyList()))

            // When
            venueSearchService.searchVenues(age, zipCode).block()

            // Then
            val keywords = capturedKeywords.captured
            assertTrue(keywords.contains("arcade"))
            assertTrue(keywords.contains("movie_theater"))
            assertTrue(keywords.contains("sports_complex"))
            assertFalse(keywords.contains("bar"))
        }

        @Test
        @DisplayName("should use adult-appropriate keywords for age 25")
        fun shouldUseAdultKeywordsForAge25() {
            // Given
            val age = 25
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)

            val capturedKeywords = slot<List<String>>()

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, capture(capturedKeywords), any()) } returns
                Mono.just(SearchNearbyResponse(places = emptyList()))

            // When
            venueSearchService.searchVenues(age, zipCode).block()

            // Then
            val keywords = capturedKeywords.captured
            assertTrue(keywords.contains("restaurant"))
            assertTrue(keywords.contains("bar"))
            assertTrue(keywords.contains("banquet_hall"))
            assertFalse(keywords.contains("playground"))
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 5, 10, 12])
        @DisplayName("should use child keywords for ages 1-12")
        fun shouldUseChildKeywordsForAges1To12(age: Int) {
            // Given
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)

            val capturedKeywords = slot<List<String>>()

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, capture(capturedKeywords), any()) } returns
                Mono.just(SearchNearbyResponse(places = emptyList()))

            // When
            venueSearchService.searchVenues(age, zipCode).block()

            // Then
            val keywords = capturedKeywords.captured
            assertTrue(keywords.contains("playground"), "Age $age should include playground")
        }
    }

    @Nested
    @DisplayName("estimateCapacity")
    inner class EstimateCapacity {

        // Note: estimateCapacity is private, tested indirectly

        @Test
        @DisplayName("should estimate higher capacity for banquet halls")
        fun shouldEstimateHigherCapacityForBanquetHalls() {
            // Given
            val age = 25
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)
            val banquetHall = createMockPlace(types = listOf("banquet_hall"))

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = listOf(banquetHall)))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(1, venues.size)
                    assertTrue(venues[0].estimatedCapacity >= 100,
                        "Banquet hall should have high capacity")
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should estimate lower capacity for arcades")
        fun shouldEstimateLowerCapacityForArcades() {
            // Given
            val age = 15
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)
            val arcade = createMockPlace(types = listOf("arcade"))

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = listOf(arcade)))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(1, venues.size)
                    assertTrue(venues[0].estimatedCapacity <= 100,
                        "Arcade should have moderate capacity")
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("formatPriceRange")
    inner class FormatPriceRange {

        @Test
        @DisplayName("should format price levels correctly")
        fun shouldFormatPriceLevelsCorrectly() {
            // Given
            val age = 7
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)

            val inexpensiveVenue = createMockPlace(id = "v1", priceLevel = "PRICE_LEVEL_INEXPENSIVE")
            val moderateVenue = createMockPlace(id = "v2", priceLevel = "PRICE_LEVEL_MODERATE")
            val expensiveVenue = createMockPlace(id = "v3", priceLevel = "PRICE_LEVEL_EXPENSIVE")

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = listOf(inexpensiveVenue, moderateVenue, expensiveVenue)))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(3, venues.size)
                    venues.forEach { venue ->
                        val priceRange = venue.priceRange
                        assertNotNull(priceRange)
                        assertTrue(priceRange!!.contains("$"))
                    }
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("kidFriendlyFeatures")
    inner class KidFriendlyFeatures {

        @Test
        @DisplayName("should mark venues as kid-friendly for children under 12")
        fun shouldMarkVenuesAsKidFriendlyForChildren() {
            // Given
            val age = 7
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)
            val playground = createMockPlace(types = listOf("playground", "park"))

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = listOf(playground)))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(1, venues.size)
                    assertTrue(venues[0].kidFriendlyFeatures.isKidFriendly)
                    assertEquals("3-12", venues[0].kidFriendlyFeatures.ageRange)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should mark venues as teen-friendly for ages 13-18")
        fun shouldMarkVenuesAsTeenFriendly() {
            // Given
            val age = 15
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)
            val arcade = createMockPlace(types = listOf("arcade"))

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = listOf(arcade)))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(1, venues.size)
                    assertTrue(venues[0].kidFriendlyFeatures.isKidFriendly)
                    assertEquals("13-18", venues[0].kidFriendlyFeatures.ageRange)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should not mark venues as kid-friendly for adults")
        fun shouldNotMarkVenuesAsKidFriendlyForAdults() {
            // Given
            val age = 25
            val zipCode = "94105"
            val location = Location(lat = 37.7893, lng = -122.3932)
            val bar = createMockPlace(types = listOf("bar", "restaurant"))

            every { googlePlacesService.geocodeZipCode(zipCode) } returns Mono.just(location)
            every { googlePlacesService.searchNearbyPlaces(location, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = listOf(bar)))

            // When & Then
            StepVerifier.create(venueSearchService.searchVenues(age, zipCode))
                .assertNext { venues ->
                    assertEquals(1, venues.size)
                    assertFalse(venues[0].kidFriendlyFeatures.isKidFriendly)
                    assertEquals("18+", venues[0].kidFriendlyFeatures.ageRange)
                }
                .verifyComplete()
        }
    }
}
