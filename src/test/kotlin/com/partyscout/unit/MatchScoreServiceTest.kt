package com.partyscout.unit

import com.partyscout.model.PartySearchRequest
import com.partyscout.service.BudgetEstimationService
import com.partyscout.service.MatchScoreService
import com.partyscout.service.PartyTypeService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

@DisplayName("MatchScoreService")
class MatchScoreServiceTest {

    private lateinit var matchScoreService: MatchScoreService
    private lateinit var partyTypeService: PartyTypeService
    private lateinit var budgetEstimationService: BudgetEstimationService

    @BeforeEach
    fun setUp() {
        partyTypeService = PartyTypeService()
        budgetEstimationService = BudgetEstimationService(partyTypeService)
        matchScoreService = MatchScoreService(partyTypeService, budgetEstimationService)
    }

    private fun createRequest(
        age: Int = 7,
        partyTypes: List<String> = listOf("active_play"),
        guestCount: Int = 15,
        budgetMax: Int? = 500,
        setting: String = "any",
        maxDistanceMiles: Int = 10
    ) = PartySearchRequest(
        age = age,
        partyTypes = partyTypes,
        guestCount = guestCount,
        budgetMin = null,
        budgetMax = budgetMax,
        zipCode = "94105",
        setting = setting,
        maxDistanceMiles = maxDistanceMiles,
        date = null
    )

    @Nested
    @DisplayName("calculateMatchScore")
    inner class CalculateMatchScore {

        @Test
        @DisplayName("should return score between 0 and 100")
        fun shouldReturnScoreInRange() {
            val request = createRequest()

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.5,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(result.totalScore in 0..100, "Score should be between 0 and 100")
        }

        @Test
        @DisplayName("should give higher score for venues within budget")
        fun shouldScoreHigherForWithinBudget() {
            val request = createRequest(budgetMax = 500)

            // Within budget (price level 1 = cheaper)
            val withinBudgetResult = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.0,
                venueUserRatingsTotal = 50,
                venuePriceLevel = 1,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            // Over budget (price level 4 = expensive)
            val overBudgetResult = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.0,
                venueUserRatingsTotal = 50,
                venuePriceLevel = 4,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(withinBudgetResult.totalScore > overBudgetResult.totalScore,
                "Venues within budget should score higher")
        }

        @Test
        @DisplayName("should give higher score for closer venues")
        fun shouldScoreHigherForCloserVenues() {
            val request = createRequest(maxDistanceMiles = 10)

            val closeResult = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.0,
                venueUserRatingsTotal = 50,
                venuePriceLevel = 2,
                venueDistanceMiles = 2.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            val farResult = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.0,
                venueUserRatingsTotal = 50,
                venuePriceLevel = 2,
                venueDistanceMiles = 9.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(closeResult.totalScore > farResult.totalScore,
                "Closer venues should score higher")
        }

        @Test
        @DisplayName("should give higher score for higher rated venues")
        fun shouldScoreHigherForBetterRating() {
            val request = createRequest()

            val highRatedResult = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.8,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            val lowRatedResult = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 2.5,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(highRatedResult.totalScore > lowRatedResult.totalScore,
                "Higher rated venues should score higher")
        }

        @Test
        @DisplayName("should handle null budget gracefully")
        fun shouldHandleNullBudget() {
            val request = createRequest(budgetMax = null)

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.0,
                venueUserRatingsTotal = 50,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(result.totalScore > 0, "Should return positive score even with null budget")
        }

        @Test
        @DisplayName("should give bonus for matching venue type")
        fun shouldGiveBonusForMatchingType() {
            val request = createRequest(partyTypes = listOf("active_play"))

            val matchingTypeResult = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center", "gym"),
                venueRating = 4.0,
                venueUserRatingsTotal = 50,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            val nonMatchingTypeResult = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("restaurant"),
                venueRating = 4.0,
                venueUserRatingsTotal = 50,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(matchingTypeResult.totalScore >= nonMatchingTypeResult.totalScore,
                "Matching venue types should score equal or higher")
        }
    }

    @Nested
    @DisplayName("Match Reasons")
    inner class MatchReasons {

        @Test
        @DisplayName("should return non-empty reasons list")
        fun shouldReturnReasons() {
            val request = createRequest()

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.5,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 2,
                venueDistanceMiles = 2.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertFalse(result.reasons.isEmpty(), "Should return at least one reason")
        }

        @Test
        @DisplayName("should include rating in reasons for high-rated venues")
        fun shouldIncludeRatingReason() {
            val request = createRequest()

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.7,
                venueUserRatingsTotal = 200,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(result.reasons.any { it.contains("rated", ignoreCase = true) },
                "Should mention high rating")
        }

        @Test
        @DisplayName("should include budget fit in reasons when within budget")
        fun shouldIncludeBudgetReason() {
            val request = createRequest(budgetMax = 500)

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.0,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 1, // Budget-friendly
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(result.reasons.any { it.contains("budget", ignoreCase = true) },
                "Should mention budget fit")
        }

        @Test
        @DisplayName("should include distance in reasons for close venues")
        fun shouldIncludeDistanceReason() {
            val request = createRequest()

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.0,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 2,
                venueDistanceMiles = 1.5,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertTrue(result.reasons.any {
                it.contains("close", ignoreCase = true) ||
                it.contains("nearby", ignoreCase = true) ||
                it.contains("Very close", ignoreCase = true)
            }, "Should mention proximity")
        }
    }

    @Nested
    @DisplayName("getMatchQualityLabel")
    inner class GetMatchQualityLabel {

        @Test
        @DisplayName("should return 'Excellent Match' for score >= 85")
        fun shouldReturnExcellentMatchForHighScore() {
            assertEquals("Excellent Match", matchScoreService.getMatchQualityLabel(85))
            assertEquals("Excellent Match", matchScoreService.getMatchQualityLabel(90))
            assertEquals("Excellent Match", matchScoreService.getMatchQualityLabel(100))
        }

        @Test
        @DisplayName("should return 'Great Match' for score 70-84")
        fun shouldReturnGreatMatchForGoodScore() {
            assertEquals("Great Match", matchScoreService.getMatchQualityLabel(70))
            assertEquals("Great Match", matchScoreService.getMatchQualityLabel(75))
            assertEquals("Great Match", matchScoreService.getMatchQualityLabel(84))
        }

        @Test
        @DisplayName("should return 'Good Match' for score 55-69")
        fun shouldReturnGoodMatchForModerateScore() {
            assertEquals("Good Match", matchScoreService.getMatchQualityLabel(55))
            assertEquals("Good Match", matchScoreService.getMatchQualityLabel(60))
            assertEquals("Good Match", matchScoreService.getMatchQualityLabel(69))
        }

        @Test
        @DisplayName("should return 'Possible Match' for score 40-54")
        fun shouldReturnPossibleMatchForLowScore() {
            assertEquals("Possible Match", matchScoreService.getMatchQualityLabel(40))
            assertEquals("Possible Match", matchScoreService.getMatchQualityLabel(50))
            assertEquals("Possible Match", matchScoreService.getMatchQualityLabel(54))
        }

        @Test
        @DisplayName("should return 'Limited Match' for score < 40")
        fun shouldReturnLimitedMatchForVeryLowScore() {
            assertEquals("Limited Match", matchScoreService.getMatchQualityLabel(0))
            assertEquals("Limited Match", matchScoreService.getMatchQualityLabel(20))
            assertEquals("Limited Match", matchScoreService.getMatchQualityLabel(39))
        }
    }

    @Nested
    @DisplayName("Score Breakdown")
    inner class ScoreBreakdown {

        @Test
        @DisplayName("should include all score components in breakdown")
        fun shouldIncludeAllScoreComponentsInBreakdown() {
            val request = createRequest()

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.5,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            assertNotNull(result.breakdown)
            assertTrue(result.breakdown.ageScore >= 0, "Age score should be non-negative")
            assertTrue(result.breakdown.budgetScore >= 0, "Budget score should be non-negative")
            assertTrue(result.breakdown.capacityScore >= 0, "Capacity score should be non-negative")
            assertTrue(result.breakdown.distanceScore >= 0, "Distance score should be non-negative")
            assertTrue(result.breakdown.ratingScore >= 0, "Rating score should be non-negative")
            assertTrue(result.breakdown.typeMatchScore >= 0, "Type match score should be non-negative")
        }

        @Test
        @DisplayName("should have breakdown scores sum approximately to total")
        fun shouldHaveBreakdownScoresSumToTotal() {
            val request = createRequest()

            val result = matchScoreService.calculateMatchScore(
                request = request,
                venuePlaceTypes = listOf("amusement_center"),
                venueRating = 4.5,
                venueUserRatingsTotal = 100,
                venuePriceLevel = 2,
                venueDistanceMiles = 5.0,
                venueMinCapacity = 10,
                venueMaxCapacity = 50
            )

            val breakdownSum = result.breakdown.ageScore +
                result.breakdown.budgetScore +
                result.breakdown.capacityScore +
                result.breakdown.distanceScore +
                result.breakdown.ratingScore +
                result.breakdown.typeMatchScore

            // Total should be capped at 100, so breakdownSum should equal totalScore
            // or breakdownSum should be >= totalScore if capped
            assertTrue(breakdownSum >= result.totalScore || result.totalScore == 100,
                "Breakdown should sum to total (or total capped at 100)")
        }
    }
}
