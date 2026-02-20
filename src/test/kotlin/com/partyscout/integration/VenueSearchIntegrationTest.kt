package com.partyscout.integration

import com.partyscout.dto.*
import com.partyscout.model.PartySearchRequest
import com.partyscout.service.*
import com.partyscout.unit.mocks.MockPlaceFactory.createMockPlace
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import reactor.core.publisher.Mono

@DisplayName("Venue Search Integration Tests")
class VenueSearchIntegrationTest {

    private lateinit var googlePlacesService: GooglePlacesService
    private lateinit var venueSearchService: VenueSearchService
    private lateinit var partyTypeService: PartyTypeService
    private lateinit var budgetEstimationService: BudgetEstimationService
    private lateinit var matchScoreService: MatchScoreService
    private lateinit var partyDetailsService: PartyDetailsService

    private val mockLocation = Location(lat = 37.7893, lng = -122.3932)

    @BeforeEach
    fun setUp() {
        googlePlacesService = mockk(relaxed = true)
        venueSearchService = VenueSearchService(googlePlacesService)
        partyTypeService = PartyTypeService()
        budgetEstimationService = BudgetEstimationService(partyTypeService)
        matchScoreService = MatchScoreService(partyTypeService, budgetEstimationService)
        partyDetailsService = PartyDetailsService(partyTypeService)
    }

    @Nested
    @DisplayName("VenueSearchService + MatchScoreService Integration")
    inner class VenueSearchWithMatchScore {

        @Test
        @DisplayName("should calculate match scores for searched venues")
        fun shouldCalculateMatchScoresForSearchedVenues() {
            // Given
            val places = listOf(
                createMockPlace(id = "v1", name = "High Rated Venue", rating = 4.8),
                createMockPlace(id = "v2", name = "Average Venue", rating = 3.5),
                createMockPlace(id = "v3", name = "Low Rated Venue", rating = 2.0)
            )

            every { googlePlacesService.geocodeZipCode("94105") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchNearbyPlaces(mockLocation, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = places))

            // When
            val venues = venueSearchService.searchVenues(7, "94105").block() ?: emptyList()

            // Then calculate match scores
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

            val scores = venues.map { venue ->
                matchScoreService.calculateMatchScore(
                    request = request,
                    venuePlaceTypes = listOf("amusement_center"),
                    venueRating = venue.rating,
                    venueUserRatingsTotal = 100,
                    venuePriceLevel = 2,
                    venueDistanceMiles = venue.distanceInMiles ?: 0.0,
                    venueMinCapacity = 10,
                    venueMaxCapacity = 50
                )
            }

            // Verify all scores are valid
            scores.forEach { result ->
                assertTrue(result.totalScore in 0..100, "Score should be between 0 and 100")
            }

            // Higher rated venue should generally score higher (due to rating component)
            assertTrue(scores[0].totalScore >= scores[2].totalScore,
                "Higher rated venue should score equal or higher")
        }

        @Test
        @DisplayName("should generate match reasons for venues")
        fun shouldGenerateMatchReasonsForVenues() {
            // Given
            val places = listOf(createMockPlace(rating = 4.7))

            every { googlePlacesService.geocodeZipCode("94105") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchNearbyPlaces(mockLocation, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = places))

            // When
            val venues = venueSearchService.searchVenues(7, "94105").block() ?: emptyList()
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

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = venues[0].rating,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 2,
                venueDistanceMiles = venues[0].distanceInMiles ?: 0.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            // Then
            assertFalse(result.reasons.isEmpty(), "Should generate at least one reason")
        }
    }

    @Nested
    @DisplayName("VenueSearchService + BudgetEstimationService Integration")
    inner class VenueSearchWithBudgetEstimation {

        @Test
        @DisplayName("should estimate costs for searched venues by party type")
        fun shouldEstimateCostsForVenuesByPartyType() {
            // Given
            val places = listOf(
                createMockPlace(priceLevel = "PRICE_LEVEL_INEXPENSIVE"),
                createMockPlace(priceLevel = "PRICE_LEVEL_MODERATE"),
                createMockPlace(priceLevel = "PRICE_LEVEL_EXPENSIVE")
            )

            every { googlePlacesService.geocodeZipCode("94105") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchNearbyPlaces(mockLocation, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = places))

            // When
            val venues = venueSearchService.searchVenues(7, "94105").block() ?: emptyList()

            // Estimate budget for each price level
            val priceLevels = listOf(1, 2, 3)
            val estimates = priceLevels.map { level ->
                budgetEstimationService.estimatePartyCost(
                    partyTypes = listOf("active_play"),
                    guestCount = 15,
                    priceLevel = level
                )
            }

            // Then
            assertTrue(estimates[0] < estimates[1], "Budget venue should cost less than moderate")
            assertTrue(estimates[1] < estimates[2], "Moderate venue should cost less than expensive")
        }

        @Test
        @DisplayName("should calculate per-person costs for venues")
        fun shouldCalculatePerPersonCostsForVenues() {
            // Given
            val partyTypes = listOf("active_play", "creative")

            // When
            val perPersonCosts = listOf(1, 2, 3, 4).map { priceLevel ->
                budgetEstimationService.estimateCostPerPerson(partyTypes, priceLevel)
            }

            // Then
            perPersonCosts.forEach { cost ->
                assertTrue(cost > 0, "Per-person cost should be positive")
            }

            // Higher price levels should have higher per-person costs
            assertTrue(perPersonCosts[0] < perPersonCosts[3],
                "Budget venue should have lower per-person cost than premium")
        }

        @Test
        @DisplayName("should check if venue is within budget")
        fun shouldCheckIfVenueIsWithinBudget() {
            // Given
            val estimatedCost = 300
            val lowBudget = 200
            val highBudget = 500

            // When & Then
            assertFalse(
                budgetEstimationService.isWithinBudget(estimatedCost, null, lowBudget),
                "Should be over budget for low budget max"
            )
            assertTrue(
                budgetEstimationService.isWithinBudget(estimatedCost, null, highBudget),
                "Should be within budget for high budget max"
            )
        }
    }

    @Nested
    @DisplayName("VenueSearchService + PartyDetailsService Integration")
    inner class VenueSearchWithPartyDetails {

        @Test
        @DisplayName("should get included items for venues by party type")
        fun shouldGetIncludedItemsForVenuesByPartyType() {
            // Given
            val partyTypes = listOf("active_play")

            // When
            val includedAtBudget = partyDetailsService.getIncludedItems(partyTypes, 1)
            val includedAtPremium = partyDetailsService.getIncludedItems(partyTypes, 4)

            // Then
            // Both should return items (may be empty list for unknown types, but shouldn't throw)
            assertNotNull(includedAtBudget)
            assertNotNull(includedAtPremium)
        }

        @Test
        @DisplayName("should get suggested add-ons based on guest count")
        fun shouldGetSuggestedAddOnsBasedOnGuestCount() {
            // Given
            val partyTypes = listOf("arcade") // Use a type that has add-ons

            // When
            val addOnsSmall = partyDetailsService.getSuggestedAddOns(partyTypes, 10)
            val addOnsLarge = partyDetailsService.getSuggestedAddOns(partyTypes, 30)

            // Then
            assertNotNull(addOnsSmall)
            assertNotNull(addOnsLarge)

            // If there are add-ons, larger party should cost at least as much
            if (addOnsSmall.isNotEmpty() && addOnsLarge.isNotEmpty()) {
                val smallTotal = addOnsSmall.sumOf { it.estimatedCost }
                val largeTotal = addOnsLarge.sumOf { it.estimatedCost }
                assertTrue(largeTotal >= smallTotal, "Larger party add-ons should cost at least as much")
            }
        }

        @Test
        @DisplayName("should get what to bring list for party")
        fun shouldGetWhatToBringListForParty() {
            // Given
            val partyTypes = listOf("outdoor")

            // When
            val whatToBring = partyDetailsService.getWhatToBring(partyTypes, 2)

            // Then
            assertFalse(whatToBring.isEmpty(), "Should have what to bring list")
        }

        @Test
        @DisplayName("should get typical duration for party types")
        fun shouldGetTypicalDurationForPartyTypes() {
            // Given
            val partyTypes = listOf("active_play")

            // When
            val duration = partyDetailsService.getTypicalDuration(partyTypes)

            // Then
            assertNotNull(duration)
            assertTrue(duration.isNotEmpty(), "Duration should not be empty")
        }
    }

    @Nested
    @DisplayName("Full Venue Enrichment Pipeline")
    inner class FullVenueEnrichmentPipeline {

        @Test
        @DisplayName("should enrich venues with all services")
        fun shouldEnrichVenuesWithAllServices() {
            // Given
            val places = listOf(
                createMockPlace(id = "v1", name = "Premium Venue", rating = 4.8, priceLevel = "PRICE_LEVEL_EXPENSIVE"),
                createMockPlace(id = "v2", name = "Budget Venue", rating = 4.0, priceLevel = "PRICE_LEVEL_INEXPENSIVE")
            )

            every { googlePlacesService.geocodeZipCode("94105") } returns Mono.just(mockLocation)
            every { googlePlacesService.searchNearbyPlaces(mockLocation, any(), any()) } returns
                Mono.just(SearchNearbyResponse(places = places))

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

            // When - Full enrichment pipeline
            val venues = venueSearchService.searchVenues(7, "94105").block() ?: emptyList()

            val enrichedVenues = venues.map { venue ->
                val priceRangeStr = venue.priceRange ?: ""
                val priceLevel = when {
                    priceRangeStr.contains("100-300") -> 1
                    priceRangeStr.contains("300-600") -> 2
                    priceRangeStr.contains("600-1200") -> 3
                    priceRangeStr.contains("1200-2500") -> 4
                    else -> 2
                }

                val estimatedTotal = budgetEstimationService.estimatePartyCost(
                    request.partyTypes, request.guestCount, priceLevel
                )

                val matchResult = matchScoreService.calculateMatchScore(
                    request = request,
                    venuePlaceTypes = listOf("amusement_center"),
                    venueRating = venue.rating,
                    venueUserRatingsTotal = 100,
                    venuePriceLevel = priceLevel,
                    venueDistanceMiles = venue.distanceInMiles ?: 0.0,
                    venueMinCapacity = 10,
                    venueMaxCapacity = 50
                )

                val includedItems = partyDetailsService.getIncludedItems(request.partyTypes, priceLevel)
                val notIncluded = partyDetailsService.getNotIncludedItems(request.partyTypes, priceLevel)
                val addOns = partyDetailsService.getSuggestedAddOns(request.partyTypes, request.guestCount)

                mapOf(
                    "venue" to venue,
                    "matchScore" to matchResult.totalScore,
                    "matchReasons" to matchResult.reasons,
                    "estimatedTotal" to estimatedTotal,
                    "includedItems" to includedItems,
                    "notIncluded" to notIncluded,
                    "addOns" to addOns
                )
            }

            // Then - Verify all enrichment data
            assertEquals(2, enrichedVenues.size)

            enrichedVenues.forEach { enriched ->
                assertTrue((enriched["matchScore"] as Int) in 0..100, "Match score should be valid")
                assertTrue((enriched["estimatedTotal"] as Int) > 0, "Should have positive estimated total")
            }
        }
    }
}
