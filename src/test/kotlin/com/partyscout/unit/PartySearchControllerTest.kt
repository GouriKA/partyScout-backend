package com.partyscout.unit

import com.partyscout.llm.LlmFilterService
import com.partyscout.party.model.PartySearchRequest
import com.partyscout.party.service.BudgetEstimationService
import com.partyscout.party.service.MatchScoreService
import com.partyscout.party.service.PartyDetailsService
import com.partyscout.party.service.PartyTypeService
import com.partyscout.persistence.service.SearchPersistenceService
import com.partyscout.persistence.service.VenueEnrichmentService
import com.partyscout.persona.PersonaService
import com.partyscout.search.controller.PartySearchController
import com.partyscout.shared.event.DomainEventPublisher
import com.partyscout.venue.config.GooglePlacesConfig
import com.partyscout.venue.dto.DisplayName as PlaceDisplayName
import com.partyscout.venue.dto.LatLng
import com.partyscout.venue.dto.Location
import com.partyscout.venue.dto.Place
import com.partyscout.venue.dto.SearchNearbyResponse
import com.partyscout.venue.service.GooglePlacesService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import reactor.core.publisher.Mono

@DisplayName("PartySearchController Unit Tests")
class PartySearchControllerTest {

    private lateinit var controller: PartySearchController
    private lateinit var googlePlacesService: GooglePlacesService
    private lateinit var googlePlacesConfig: GooglePlacesConfig
    private lateinit var partyTypeService: PartyTypeService
    private lateinit var matchScoreService: MatchScoreService
    private lateinit var budgetEstimationService: BudgetEstimationService
    private lateinit var partyDetailsService: PartyDetailsService
    private lateinit var domainEventPublisher: DomainEventPublisher
    private lateinit var searchPersistenceService: SearchPersistenceService
    private lateinit var personaService: PersonaService
    private lateinit var llmFilterService: LlmFilterService
    private lateinit var venueEnrichmentService: VenueEnrichmentService

    private val mockLocation = Location(lat = 37.7893, lng = -122.3932)

    /** A fully-valid amusement-centre place near the mock location */
    private fun amusementPlace(id: String = "place-1", name: String = "Fun Zone") = Place(
        id = id,
        displayName = PlaceDisplayName(text = name),
        formattedAddress = "1 Fun St, San Francisco, CA 94105",
        location = LatLng(latitude = 37.7900, longitude = -122.3900),
        rating = 4.5,
        userRatingCount = 200,
        priceLevel = "PRICE_LEVEL_MODERATE",
        types = listOf("amusement_center"),
    )

    @BeforeEach
    fun setUp() {
        googlePlacesService = mockk(relaxed = true)
        googlePlacesConfig = GooglePlacesConfig().apply { apiKey = "test-api-key" }
        partyTypeService = PartyTypeService()
        budgetEstimationService = BudgetEstimationService(partyTypeService)
        matchScoreService = MatchScoreService(partyTypeService, budgetEstimationService)
        partyDetailsService = PartyDetailsService(partyTypeService)
        domainEventPublisher = mockk(relaxed = true)
        searchPersistenceService = mockk(relaxed = true)
        personaService = PersonaService()
        llmFilterService = mockk(relaxed = true)
        venueEnrichmentService = mockk(relaxed = true)

        // Default LLM passthrough — returns all venues unfiltered
        every { llmFilterService.filter(any(), any(), any(), any()) } answers {
            Pair(firstArg<List<Place>>(), false)
        }

        // Default enrichment — empty map
        every { venueEnrichmentService.batchLookup(any()) } returns emptyMap()

        controller = PartySearchController(
            googlePlacesService = googlePlacesService,
            googlePlacesConfig = googlePlacesConfig,
            partyTypeService = partyTypeService,
            matchScoreService = matchScoreService,
            budgetEstimationService = budgetEstimationService,
            partyDetailsService = partyDetailsService,
            domainEventPublisher = domainEventPublisher,
            searchPersistenceService = searchPersistenceService,
            personaService = personaService,
            llmFilterService = llmFilterService,
            venueEnrichmentService = venueEnrichmentService,
        )
    }

    // ── textQuery behaviour ────────────────────────────────────────────────

    @Nested
    @DisplayName("textQuery handling")
    inner class TextQueryHandling {

        @Test
        @DisplayName("textQuery overrides persona queries — only 1 search query sent to Google")
        fun textQueryOverridesPersonaQueries() {
            val queriesUsed = mutableListOf<String>()
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } answers {
                queriesUsed.add(firstArg())
                Mono.just(SearchNearbyResponse(listOf(amusementPlace())))
            }

            val request = PartySearchRequest(
                age = 10,
                guestCount = 15,
                city = "London",
                textQuery = "boba tea",
            )
            controller.searchPartyVenues(request)

            assertEquals(1, queriesUsed.size, "Only 1 query should be sent when textQuery is set")
            assertEquals("boba tea", queriesUsed[0])
        }

        @Test
        @DisplayName("textQuery=null falls back to persona-based search queries (more than 1)")
        fun noTextQueryFallsBackToPersonaQueries() {
            val queriesUsed = mutableListOf<String>()
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } answers {
                queriesUsed.add(firstArg())
                Mono.just(SearchNearbyResponse(emptyList()))
            }

            val request = PartySearchRequest(
                age = 10,
                guestCount = 15,
                city = "London",
                textQuery = null,
            )
            controller.searchPartyVenues(request)

            assertTrue(queriesUsed.size > 1, "Multiple persona queries should be sent when textQuery is null")
        }

        @Test
        @DisplayName("blank textQuery falls back to persona queries")
        fun blankTextQueryFallsBackToPersonaQueries() {
            val queriesUsed = mutableListOf<String>()
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } answers {
                queriesUsed.add(firstArg())
                Mono.just(SearchNearbyResponse(emptyList()))
            }

            val request = PartySearchRequest(
                age = 10,
                guestCount = 15,
                city = "London",
                textQuery = "   ", // blank — should behave like null
            )
            controller.searchPartyVenues(request)

            assertTrue(queriesUsed.size > 1)
        }
    }

    // ── Geocoding failure ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Geocoding failure")
    inner class GeocodingFailure {

        @Test
        @DisplayName("city geocoding failure returns 400 Bad Request")
        fun geocodingFailureReturns400() {
            // Mono.empty() → block() returns null → controller returns badRequest()
            every { googlePlacesService.geocodeCity("UnknownCity") } returns Mono.empty<Location>()

            val request = PartySearchRequest(
                age = 7,
                guestCount = 10,
                city = "UnknownCity",
            )

            val response = controller.searchPartyVenues(request)

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }
    }

    // ── Response fields ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Response fields")
    inner class ResponseFields {

        @Test
        @DisplayName("response includes persona field derived from age")
        fun responseIncludesPersonaField() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(amusementPlace())))

            val request = PartySearchRequest(age = 6, guestCount = 10, city = "London")
            val response = controller.searchPartyVenues(request)

            assertNotNull(response.body?.persona)
            assertTrue(response.body!!.persona.isNotBlank())
        }

        @Test
        @DisplayName("age=6 maps to Kids persona")
        fun age6MapsToKidsPersona() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(emptyList()))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 6, guestCount = 10, city = "London")
            )

            assertEquals("Kids", response.body?.persona)
        }

        @Test
        @DisplayName("age=15 maps to Early Teens persona")
        fun age15MapsToEarlyTeensPersona() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(emptyList()))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 15, guestCount = 10, city = "London")
            )

            assertEquals("Early Teens", response.body?.persona)
        }

        @Test
        @DisplayName("age=25 maps to Adults persona")
        fun age25MapsToAdultsPersona() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(emptyList()))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 25, guestCount = 10, city = "London")
            )

            assertEquals("Adults", response.body?.persona)
        }

        @Test
        @DisplayName("response includes llmFilterApplied field")
        fun responseIncludesLlmFilterApplied() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(emptyList()))

            val request = PartySearchRequest(age = 10, guestCount = 10, city = "London")
            val response = controller.searchPartyVenues(request)

            // llmFilterApplied is a non-null Boolean in the response model
            assertNotNull(response.body)
            // Our mock returns false
            assertFalse(response.body!!.llmFilterApplied)
        }

        @Test
        @DisplayName("llmFilterApplied is true when LLM filters successfully")
        fun llmFilterAppliedIsTrueWhenLlmFilters() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(amusementPlace())))
            every { llmFilterService.filter(any(), any(), any(), any()) } answers {
                Pair(firstArg<List<Place>>(), true)
            }

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 10, guestCount = 10, city = "London")
            )

            assertTrue(response.body!!.llmFilterApplied)
        }

        @Test
        @DisplayName("response body is 200 OK for a valid request")
        fun responseIs200ForValidRequest() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(emptyList()))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 10, guestCount = 10, city = "London")
            )

            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }

    // ── Deduplication ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deduplication")
    inner class Deduplication {

        @Test
        @DisplayName("duplicate place IDs in API response are deduplicated")
        fun duplicatePlaceIdsAreDeduped() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            // Return the same place twice from two different queries
            var callCount = 0
            every { googlePlacesService.searchText(any(), any(), any()) } answers {
                callCount++
                Mono.just(SearchNearbyResponse(listOf(amusementPlace("place-dup"))))
            }

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "London",
                textQuery = null, // triggers multiple queries
            )
            val response = controller.searchPartyVenues(request)

            // Despite the same place being returned from multiple queries, it should appear once
            val ids = response.body?.venues?.map { it.id } ?: emptyList()
            assertEquals(ids.distinct(), ids)
        }
    }

    // ── Distance filtering ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Distance filtering")
    inner class DistanceFiltering {

        @Test
        @DisplayName("venues beyond maxDistanceMiles are excluded")
        fun venuesBeyondMaxDistanceAreExcluded() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)

            // Place that is very far from the mock location (opposite side of Earth)
            val farPlace = Place(
                id = "far-place",
                displayName = PlaceDisplayName(text = "Far Away Venue"),
                formattedAddress = "1 Far Rd",
                location = LatLng(latitude = -37.7900, longitude = 57.6100), // ~10,000 miles away
                rating = 4.5,
                userRatingCount = 100,
                priceLevel = "PRICE_LEVEL_MODERATE",
                types = listOf("amusement_center"),
            )

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(farPlace)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "London",
                maxDistanceMiles = 5,
                textQuery = "far place",
            )
            val response = controller.searchPartyVenues(request)

            assertTrue(response.body?.venues?.isEmpty() ?: true,
                "Venues beyond maxDistanceMiles should be excluded")
        }

        @Test
        @DisplayName("venues within maxDistanceMiles are included")
        fun venuesWithinMaxDistanceAreIncluded() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(amusementPlace())))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "London",
                maxDistanceMiles = 50,
                textQuery = "fun zone",
            )
            val response = controller.searchPartyVenues(request)

            assertTrue((response.body?.venues?.size ?: 0) >= 1)
        }
    }

    // ── Setting filter ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Setting filter")
    inner class SettingFilter {

        @Test
        @DisplayName("indoor-only request excludes outdoor venues")
        fun indoorOnlyExcludesOutdoorVenues() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)

            // Park-type venue → infers "outdoor" setting
            val parkPlace = Place(
                id = "park-1",
                displayName = PlaceDisplayName(text = "Central Park"),
                formattedAddress = "1 Park Lane",
                location = LatLng(latitude = 37.7900, longitude = -122.3900),
                rating = 4.5,
                userRatingCount = 100,
                priceLevel = "PRICE_LEVEL_FREE",
                types = listOf("park"),
            )

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(parkPlace)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "London",
                setting = "indoor",
                textQuery = "park",
            )
            val response = controller.searchPartyVenues(request)

            val venues = response.body?.venues ?: emptyList()
            assertTrue(venues.none { it.setting == "outdoor" },
                "Outdoor venues must be excluded for indoor-only requests")
        }

        @Test
        @DisplayName("outdoor-only request excludes indoor venues")
        fun outdoorOnlyExcludesIndoorVenues() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(amusementPlace()))) // inferred indoor

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "London",
                setting = "outdoor",
                textQuery = "fun zone",
            )
            val response = controller.searchPartyVenues(request)

            val venues = response.body?.venues ?: emptyList()
            assertTrue(venues.none { it.setting == "indoor" },
                "Indoor venues must be excluded for outdoor-only requests")
        }

        @Test
        @DisplayName("setting=any includes all venues regardless of setting")
        fun settingAnyIncludesAll() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)

            val parkPlace = Place(
                id = "park-1",
                displayName = PlaceDisplayName(text = "Open Park"),
                formattedAddress = "1 Park Ln",
                location = LatLng(latitude = 37.7900, longitude = -122.3900),
                rating = 4.2,
                userRatingCount = 80,
                priceLevel = "PRICE_LEVEL_FREE",
                types = listOf("park"),
            )
            val indoorPlace = amusementPlace("indoor-1")

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(parkPlace, indoorPlace)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "London",
                setting = "any",
                textQuery = "party",
            )
            val response = controller.searchPartyVenues(request)

            val venues = response.body?.venues ?: emptyList()
            assertTrue(venues.size >= 1)
        }
    }

    // ── Sorting ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sorting")
    inner class Sorting {

        @Test
        @DisplayName("results are sorted by matchScore descending")
        fun resultsSortedByMatchScoreDescending() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)

            val highRated = amusementPlace("high", "High Rated").copy(rating = 4.9, userRatingCount = 500)
            val lowRated = amusementPlace("low", "Low Rated").copy(rating = 2.5, userRatingCount = 10)

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(lowRated, highRated))) // low first

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "London",
                textQuery = "fun",
            )
            val response = controller.searchPartyVenues(request)

            val venues = response.body?.venues ?: emptyList()
            if (venues.size >= 2) {
                for (i in 0 until venues.size - 1) {
                    assertTrue(
                        venues[i].matchScore >= venues[i + 1].matchScore,
                        "Venues should be sorted by matchScore descending"
                    )
                }
            }
        }
    }

    // ── Excluded types ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Excluded venue types")
    inner class ExcludedTypes {

        private fun placeWithType(id: String, name: String, vararg types: String) = Place(
            id = id,
            displayName = PlaceDisplayName(text = name),
            formattedAddress = "1 Test St",
            location = LatLng(latitude = 37.7900, longitude = -122.3900),
            rating = 4.0,
            userRatingCount = 100,
            priceLevel = "PRICE_LEVEL_MODERATE",
            types = types.toList(),
        )

        @Test
        @DisplayName("grocery_store type is filtered out")
        fun groceryStoreIsFiltered() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(placeWithType("g1", "Tesco", "grocery_store"))))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 10, guestCount = 10, city = "London", textQuery = "test")
            )
            assertTrue(response.body?.venues?.isEmpty() ?: true)
        }

        @Test
        @DisplayName("gas_station type is filtered out")
        fun gasStationIsFiltered() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(placeWithType("g2", "BP Station", "gas_station"))))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 10, guestCount = 10, city = "London", textQuery = "test")
            )
            assertTrue(response.body?.venues?.isEmpty() ?: true)
        }

        @Test
        @DisplayName("pharmacy type is filtered out")
        fun pharmacyIsFiltered() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(placeWithType("g3", "Boots", "pharmacy"))))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 10, guestCount = 10, city = "London", textQuery = "test")
            )
            assertTrue(response.body?.venues?.isEmpty() ?: true)
        }

        @Test
        @DisplayName("car_wash type is filtered out")
        fun carWashIsFiltered() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(placeWithType("g4", "AutoGleam", "car_wash"))))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 10, guestCount = 10, city = "London", textQuery = "test")
            )
            assertTrue(response.body?.venues?.isEmpty() ?: true)
        }

        @Test
        @DisplayName("amusement_center type is NOT filtered out")
        fun amusementCenterIsNotFiltered() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(amusementPlace())))

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 10, guestCount = 10, city = "London", textQuery = "fun")
            )
            assertEquals(1, response.body?.venues?.size)
        }
    }

    // ── LLM filter bypass ──────────────────────────────────────────────────

    @Nested
    @DisplayName("LLM filter bypass")
    inner class LlmFilterBypass {

        @Test
        @DisplayName("when LLM unavailable, llmFilterApplied=false and all venues still returned")
        fun llmUnavailableAllVenuesReturned() {
            every { googlePlacesService.geocodeCity("London") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(amusementPlace("p1"), amusementPlace("p2", "Zone 2"))))
            // LLM unavailable — returns all venues with llmFilterApplied=false
            every { llmFilterService.filter(any(), any(), any(), any()) } answers {
                Pair(firstArg<List<Place>>(), false)
            }

            val response = controller.searchPartyVenues(
                PartySearchRequest(age = 10, guestCount = 10, city = "London", textQuery = "fun")
            )

            assertFalse(response.body!!.llmFilterApplied)
            assertEquals(2, response.body!!.venues.size)
        }
    }
}
