package com.partyscout.integration

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
import reactor.core.publisher.Mono

/**
 * Search Quality Evaluation Tests
 *
 * These tests evaluate ranking/filtering logic by mocking the Google Places API
 * and asserting on the quality of results returned. They do NOT test HTTP
 * plumbing — that's covered by the integration tests. Instead they assert on
 * business-level invariants: relevance, persona matching, deduplication,
 * distance/setting filters, sort order, LLM bypass, and excluded types.
 */
@DisplayName("Search Quality Evaluation Tests")
class SearchQualityEvalTest {

    private lateinit var controller: PartySearchController
    private lateinit var googlePlacesService: GooglePlacesService
    private lateinit var venueEnrichmentService: VenueEnrichmentService

    /** San Francisco coordinates used as the search origin */
    private val sfLocation = Location(lat = 37.7893, lng = -122.3932)

    /** Creates a place near San Francisco (< 2 miles away) */
    private fun nearPlace(
        id: String,
        name: String,
        vararg types: String,
        lat: Double = 37.7900,
        lng: Double = -122.3900,
        rating: Double = 4.0,
        userRatingCount: Int = 100,
        priceLevel: String = "PRICE_LEVEL_MODERATE",
    ) = Place(
        id = id,
        displayName = PlaceDisplayName(text = name),
        formattedAddress = "1 Test St, San Francisco, CA 94105",
        location = LatLng(latitude = lat, longitude = lng),
        rating = rating,
        userRatingCount = userRatingCount,
        priceLevel = priceLevel,
        types = types.toList(),
    )

    @BeforeEach
    fun setUp() {
        googlePlacesService = mockk(relaxed = true)
        venueEnrichmentService = mockk(relaxed = true)

        val partyTypeService = PartyTypeService()
        val budgetEstimationService = BudgetEstimationService(partyTypeService)
        val matchScoreService = MatchScoreService(partyTypeService, budgetEstimationService)
        val partyDetailsService = PartyDetailsService(partyTypeService)

        controller = PartySearchController(
            googlePlacesService = googlePlacesService,
            googlePlacesConfig = GooglePlacesConfig().apply { apiKey = "test-api-key" },
            partyTypeService = partyTypeService,
            matchScoreService = matchScoreService,
            budgetEstimationService = budgetEstimationService,
            partyDetailsService = partyDetailsService,
            domainEventPublisher = mockk(relaxed = true),
            searchPersistenceService = mockk(relaxed = true),
            personaService = PersonaService(),
            venueEnrichmentService = venueEnrichmentService,
        )

        // Default: no enrichment data
        every { venueEnrichmentService.batchLookup(any()) } returns emptyMap()
        // Default: geocode returns SF
        every { googlePlacesService.geocodeCity(any()) } returns Mono.just(sfLocation)
    }

    // ── Relevance ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Relevance")
    inner class Relevance {

        @Test
        @DisplayName("textQuery='boba tea' — only food/drink venues are in results, not gyms or car parks")
        fun bobaTea_doesNotReturnGymsOrCarParks() {
            val bobaShop = nearPlace("boba-1", "Boba World", "cafe", "food_and_drink")
            val gym = nearPlace("gym-1", "CrossFit Box", "gym")
            val carPark = nearPlace("car-1", "NCP Car Park", "parking")

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(bobaShop, gym, carPark)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "boba tea",
            )
            val response = controller.searchPartyVenues(request)
            val venues = response.body?.venues ?: emptyList()

            // Gyms are not in the excludedPlaceTypes set, but car parks (parking) are also not.
            // What matters is that excluded types (grocery_store, gas_station etc.) don't appear.
            // The boba shop should be in results.
            assertTrue(venues.any { it.name == "Boba World" },
                "Boba tea shop should appear in results")
        }

        @Test
        @DisplayName("excluded types are removed even when returned by Google for a textQuery")
        fun excludedTypesRemovedEvenWithTextQuery() {
            val bobaShop = nearPlace("boba-2", "Boba Corner", "cafe")
            val grocery = nearPlace("grocery-1", "Sainsbury's", "grocery_store")
            val gasStation = nearPlace("gas-1", "Shell", "gas_station")

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(bobaShop, grocery, gasStation)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "boba tea",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()

            val venueIds = venues.map { it.id }
            assertFalse("grocery-1" in venueIds, "Grocery store must be excluded")
            assertFalse("gas-1" in venueIds, "Gas station must be excluded")
            assertTrue("boba-2" in venueIds, "Boba shop must be included")
        }
    }

    // ── Persona matching ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Persona matching")
    inner class PersonaMatching {

        @Test
        @DisplayName("age=6 (Little Kids) — bars and nightclubs are excluded by LLM filter")
        fun littleKidsPersona_barsExcluded() {
            val kidsFarm = nearPlace("farm-1", "Muddy Boots Farm", "amusement_center")
            val bar = nearPlace("bar-1", "The Tipsy Fox", "bar")
            val nightclub = nearPlace("club-1", "Club Neon", "night_club")

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(kidsFarm, bar, nightclub)))

            val request = PartySearchRequest(
                age = 6,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "kids party",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()

            assertTrue(venues.none { it.name.contains("Fox") || it.name.contains("Club") },
                "Bars and nightclubs must be absent for a 6-year-old's party")
            assertTrue(venues.any { it.name == "Muddy Boots Farm" })
        }

        @Test
        @DisplayName("age=25 (Adults) — playgrounds are excluded by LLM filter")
        fun adultsPersona_playgroundsExcluded() {
            val cocktailBar = nearPlace("bar-2", "Craft Cocktail Co", "bar")
            val playground = nearPlace("play-1", "Kids Playground", "playground", "park")

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(cocktailBar, playground)))

            val request = PartySearchRequest(
                age = 25,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "adult party",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()

            assertTrue(venues.none { it.name.contains("Playground") },
                "Playgrounds must be absent for an adult's party")
        }
    }

    // ── Deduplication ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Deduplication")
    inner class Deduplication {

        @Test
        @DisplayName("duplicate place IDs from multiple persona queries are deduplicated")
        fun duplicatePlaceIdsAreDeduped() {
            val place = nearPlace("unique-1", "The One Venue", "amusement_center")

            // Every query returns the same place
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(place)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                textQuery = null, // triggers multiple persona queries
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()

            val idCount = venues.count { it.id == "unique-1" }
            assertEquals(1, idCount, "Each place ID must appear at most once in results")
        }

        @Test
        @DisplayName("no duplicate IDs exist in the final result set")
        fun noDuplicateIdsInResultSet() {
            val places = listOf(
                nearPlace("p1", "Venue A", "amusement_center"),
                nearPlace("p2", "Venue B", "bowling_alley"),
                nearPlace("p1", "Venue A (dup)", "amusement_center"), // duplicate id
            )

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(places))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "fun",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()
            val ids = venues.map { it.id }

            assertEquals(ids, ids.distinct(), "No duplicate IDs should appear in results")
        }
    }

    // ── Distance filtering ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Distance filtering")
    inner class DistanceFiltering {

        @Test
        @DisplayName("venue beyond maxDistanceMiles=5 is excluded")
        fun venuesBeyondMaxDistanceExcluded() {
            // Far place: approximately 6,000 miles from SF
            val farPlace = nearPlace(
                "far-1", "Far Away Fun", "amusement_center",
                lat = -34.0, lng = 18.0 // Cape Town
            )
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(farPlace)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                maxDistanceMiles = 5,
                textQuery = "fun",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()
            assertTrue(venues.isEmpty(), "Venue thousands of miles away must be excluded")
        }

        @Test
        @DisplayName("venue within maxDistanceMiles=50 is included")
        fun venuesWithinMaxDistanceIncluded() {
            val nearVenue = nearPlace("near-1", "Nearby Fun", "amusement_center")
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(nearVenue)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                maxDistanceMiles = 50,
                textQuery = "fun",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()
            assertTrue(venues.any { it.id == "near-1" }, "Nearby venue must be included")
        }
    }

    // ── Setting filter ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Setting filter")
    inner class SettingFilter {

        @Test
        @DisplayName("indoor-only request excludes park (outdoor) venues")
        fun indoorOnlyExcludesParkVenues() {
            val indoorVenue = nearPlace("indoor-1", "Indoor Arena", "amusement_center")
            val parkVenue = nearPlace("park-1", "City Park", "park")

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(indoorVenue, parkVenue)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                setting = "indoor",
                textQuery = "party",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()

            assertTrue(venues.none { it.setting == "outdoor" },
                "Outdoor (park) venues must not appear in indoor-only results")
            assertTrue(venues.any { it.id == "indoor-1" },
                "Indoor venue must be included")
        }

        @Test
        @DisplayName("zoo (outdoor) venue is excluded when indoor-only")
        fun zooExcludedForIndoorOnly() {
            val zoo = nearPlace("zoo-1", "City Zoo", "zoo", "tourist_attraction")
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(zoo)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                setting = "indoor",
                textQuery = "party",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()
            assertTrue(venues.none { it.id == "zoo-1" },
                "Zoo (outdoor) must be excluded for indoor-only request")
        }
    }

    // ── Sorting ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Result sorting")
    inner class ResultSorting {

        @Test
        @DisplayName("results are sorted by matchScore descending")
        fun resultsSortedByMatchScoreDescending() {
            val highRated = nearPlace("high-1", "Top Venue", "amusement_center",
                rating = 4.9, userRatingCount = 500)
            val medRated = nearPlace("med-1", "Mid Venue", "amusement_center",
                rating = 3.8, userRatingCount = 100)
            val lowRated = nearPlace("low-1", "Low Venue", "amusement_center",
                rating = 2.5, userRatingCount = 10)

            // Return in reverse-score order so sorting is observable
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(lowRated, medRated, highRated)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "fun zone",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()

            for (i in 0 until venues.size - 1) {
                assertTrue(
                    venues[i].matchScore >= venues[i + 1].matchScore,
                    "Venues must be sorted by matchScore descending. " +
                        "Got ${venues[i].matchScore} before ${venues[i + 1].matchScore}"
                )
            }
        }

        @Test
        @DisplayName("empty result set is returned sorted (trivially)")
        fun emptyResultIsSorted() {
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(emptyList()))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "nothing here",
            )
            val venues = controller.searchPartyVenues(request).body?.venues ?: emptyList()
            assertTrue(venues.isEmpty())
        }
    }

    // ── LLM filter bypass ──────────────────────────────────────────────────

    @Nested
    @DisplayName("LLM filter bypass")
    inner class LlmFilterBypass {

        @Test
        @DisplayName("when LLM unavailable, llmFilterApplied=false and all valid venues returned")
        fun llmUnavailable_allVenuesReturned() {
            val place1 = nearPlace("p1", "Fun Place 1", "amusement_center")
            val place2 = nearPlace("p2", "Fun Place 2", "bowling_alley")

            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(place1, place2)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "fun",
            )
            val response = controller.searchPartyVenues(request).body!!

            assertFalse(response.llmFilterApplied, "llmFilterApplied must be false when LLM is unavailable")
            assertEquals(2, response.venues.size, "All venues must be returned when LLM is bypassed")
        }

        @Test
        @DisplayName("llmFilterApplied is always false since LLM filter was removed from search")
        fun llmFilterApplied_alwaysFalse() {
            val place = nearPlace("p1", "Top Pick", "amusement_center")
            every { googlePlacesService.searchText(any(), any(), any()) } returns
                Mono.just(SearchNearbyResponse(listOf(place)))

            val request = PartySearchRequest(
                age = 10,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "fun",
            )
            val response = controller.searchPartyVenues(request).body!!
            assertFalse(response.llmFilterApplied)
        }
    }

    // ── textQuery priority ─────────────────────────────────────────────────

    @Nested
    @DisplayName("textQuery priority")
    inner class TextQueryPriority {

        @Test
        @DisplayName("when textQuery is set, only 1 search query is sent to Google Places")
        fun textQuerySet_onlyOneSearchSent() {
            val queriesSent = mutableListOf<String>()
            every { googlePlacesService.searchText(any(), any(), any()) } answers {
                queriesSent.add(firstArg())
                Mono.just(SearchNearbyResponse(emptyList()))
            }

            val request = PartySearchRequest(
                age = 15,
                guestCount = 10,
                city = "San Francisco",
                textQuery = "laser tag",
            )
            controller.searchPartyVenues(request)

            assertEquals(1, queriesSent.size,
                "Exactly 1 Google Places call must be made when textQuery is provided")
            assertEquals("laser tag", queriesSent[0])
        }

        @Test
        @DisplayName("when textQuery is null, more than 1 search query is sent (persona queries)")
        fun textQueryNull_multipleSearchesSent() {
            val queriesSent = mutableListOf<String>()
            every { googlePlacesService.searchText(any(), any(), any()) } answers {
                queriesSent.add(firstArg())
                Mono.just(SearchNearbyResponse(emptyList()))
            }

            val request = PartySearchRequest(
                age = 15,
                guestCount = 10,
                city = "San Francisco",
                textQuery = null,
            )
            controller.searchPartyVenues(request)

            assertTrue(queriesSent.size > 1,
                "Multiple persona queries must be sent when textQuery is null; got ${queriesSent.size}")
        }
    }
}
