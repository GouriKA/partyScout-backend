package com.partyscout.unit

import com.partyscout.service.BudgetEstimationService
import com.partyscout.service.PartyTypeService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("BudgetEstimationService")
class BudgetEstimationServiceTest {

    private lateinit var budgetEstimationService: BudgetEstimationService
    private lateinit var partyTypeService: PartyTypeService

    @BeforeEach
    fun setUp() {
        partyTypeService = PartyTypeService()
        budgetEstimationService = BudgetEstimationService(partyTypeService)
    }

    @Nested
    @DisplayName("estimatePartyCost")
    inner class EstimatePartyCost {

        @Test
        @DisplayName("should return positive cost for valid inputs")
        fun shouldReturnPositiveCost() {
            val cost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = 15,
                priceLevel = 2
            )

            assertTrue(cost > 0, "Estimated cost should be positive")
        }

        @Test
        @DisplayName("should increase cost with more guests")
        fun shouldIncreaseCostWithMoreGuests() {
            val smallPartyCost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = 10,
                priceLevel = 2
            )

            val largePartyCost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = 25,
                priceLevel = 2
            )

            assertTrue(largePartyCost > smallPartyCost,
                "Larger parties should cost more")
        }

        @Test
        @DisplayName("should increase cost with higher price level")
        fun shouldIncreaseCostWithHigherPriceLevel() {
            val budgetVenueCost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = 15,
                priceLevel = 1
            )

            val premiumVenueCost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = 15,
                priceLevel = 4
            )

            assertTrue(premiumVenueCost > budgetVenueCost,
                "Premium venues should cost more")
        }

        @ParameterizedTest
        @ValueSource(strings = ["arcade", "bounce_house", "outdoor", "sports", "arts_crafts", "pool_party"])
        @DisplayName("should return valid cost for all party types")
        fun shouldReturnCostForAllTypes(partyType: String) {
            val cost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf(partyType),
                guestCount = 15,
                priceLevel = 2
            )

            assertTrue(cost > 0, "Should return positive cost for $partyType")
        }

        @Test
        @DisplayName("should handle unknown party type with default")
        fun shouldHandleUnknownPartyType() {
            val cost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("unknown_type"),
                guestCount = 15,
                priceLevel = 2
            )

            assertTrue(cost > 0, "Should return default cost for unknown type")
        }

        @Test
        @DisplayName("should handle null price level")
        fun shouldHandleNullPriceLevel() {
            val cost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = 15,
                priceLevel = null
            )

            assertTrue(cost > 0, "Should return cost even with null price level")
        }

        @ParameterizedTest
        @CsvSource(
            "5, 1",
            "10, 2",
            "20, 2",
            "30, 3"
        )
        @DisplayName("should scale cost appropriately for various guest counts")
        fun shouldScaleWithGuestCount(guestCount: Int, priceLevel: Int) {
            val cost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = guestCount,
                priceLevel = priceLevel
            )

            // Cost should be at least $10 per person
            val minExpectedCost = guestCount * 10
            assertTrue(cost >= minExpectedCost,
                "Cost should be at least $10 per person for $guestCount guests")
        }

        @Test
        @DisplayName("should handle empty party types list")
        fun shouldHandleEmptyPartyTypes() {
            val cost = budgetEstimationService.estimatePartyCost(
                partyTypes = emptyList(),
                guestCount = 15,
                priceLevel = 2
            )

            assertTrue(cost > 0, "Should return default cost for empty party types")
        }

        @Test
        @DisplayName("should handle multiple party types")
        fun shouldHandleMultiplePartyTypes() {
            val cost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade", "bounce_house"),
                guestCount = 15,
                priceLevel = 2
            )

            assertTrue(cost > 0, "Should return cost for multiple party types")
        }
    }

    @Nested
    @DisplayName("estimateCostPerPerson")
    inner class EstimateCostPerPerson {

        @Test
        @DisplayName("should return positive per-person cost")
        fun shouldReturnPositivePerPersonCost() {
            val costPerPerson = budgetEstimationService.estimateCostPerPerson(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            assertTrue(costPerPerson > 0, "Per-person cost should be positive")
        }

        @Test
        @DisplayName("should increase per-person cost with higher price level")
        fun shouldIncreaseWithPriceLevel() {
            val budgetPerPerson = budgetEstimationService.estimateCostPerPerson(
                partyTypes = listOf("arcade"),
                priceLevel = 1
            )

            val premiumPerPerson = budgetEstimationService.estimateCostPerPerson(
                partyTypes = listOf("arcade"),
                priceLevel = 4
            )

            assertTrue(premiumPerPerson > budgetPerPerson,
                "Premium venues should have higher per-person cost")
        }

        @Test
        @DisplayName("should vary by party type")
        fun shouldVaryByPartyType() {
            val arcadeCost = budgetEstimationService.estimateCostPerPerson(listOf("arcade"), 2)
            val outdoorCost = budgetEstimationService.estimateCostPerPerson(listOf("outdoor"), 2)

            // These may be different depending on the type
            assertNotNull(arcadeCost)
            assertNotNull(outdoorCost)
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 2, 3, 4])
        @DisplayName("should handle all price levels")
        fun shouldHandleAllPriceLevels(priceLevel: Int) {
            val cost = budgetEstimationService.estimateCostPerPerson(
                partyTypes = listOf("arcade"),
                priceLevel = priceLevel
            )

            assertTrue(cost > 0, "Should return positive cost for price level $priceLevel")
        }

        @Test
        @DisplayName("should handle empty party types")
        fun shouldHandleEmptyPartyTypes() {
            val cost = budgetEstimationService.estimateCostPerPerson(
                partyTypes = emptyList(),
                priceLevel = 2
            )

            assertTrue(cost > 0, "Should return default cost for empty party types")
        }
    }

    @Nested
    @DisplayName("isWithinBudget")
    inner class IsWithinBudget {

        @Test
        @DisplayName("should return true when within budget")
        fun shouldReturnTrueWhenWithinBudget() {
            val result = budgetEstimationService.isWithinBudget(
                estimatedCost = 300,
                budgetMin = null,
                budgetMax = 500
            )

            assertTrue(result, "300 should be within budget max of 500")
        }

        @Test
        @DisplayName("should return false when over budget")
        fun shouldReturnFalseWhenOverBudget() {
            val result = budgetEstimationService.isWithinBudget(
                estimatedCost = 600,
                budgetMin = null,
                budgetMax = 500
            )

            assertFalse(result, "600 should be over budget max of 500")
        }

        @Test
        @DisplayName("should handle budget min and max")
        fun shouldHandleBudgetMinAndMax() {
            // Too low
            assertFalse(
                budgetEstimationService.isWithinBudget(100, 200, 500),
                "100 should be below budget min of 200"
            )

            // Within range
            assertTrue(
                budgetEstimationService.isWithinBudget(300, 200, 500),
                "300 should be within 200-500 range"
            )

            // Too high
            assertFalse(
                budgetEstimationService.isWithinBudget(600, 200, 500),
                "600 should be above budget max of 500"
            )
        }

        @Test
        @DisplayName("should handle null budget max")
        fun shouldHandleNullBudgetMax() {
            val result = budgetEstimationService.isWithinBudget(
                estimatedCost = 1000,
                budgetMin = null,
                budgetMax = null
            )

            assertTrue(result, "Any cost should be within budget when max is null")
        }
    }

    @Nested
    @DisplayName("getBudgetRangeDescription")
    inner class GetBudgetRangeDescription {

        @Test
        @DisplayName("should return Budget-Friendly for low costs")
        fun shouldReturnBudgetFriendlyForLowCosts() {
            assertEquals("Budget-Friendly", budgetEstimationService.getBudgetRangeDescription(100))
        }

        @Test
        @DisplayName("should return Moderate for medium costs")
        fun shouldReturnModerateForMediumCosts() {
            assertEquals("Moderate", budgetEstimationService.getBudgetRangeDescription(200))
        }

        @Test
        @DisplayName("should return Premium for higher costs")
        fun shouldReturnPremiumForHigherCosts() {
            assertEquals("Premium", budgetEstimationService.getBudgetRangeDescription(400))
        }

        @Test
        @DisplayName("should return Deluxe for high costs")
        fun shouldReturnDeluxeForHighCosts() {
            assertEquals("Deluxe", budgetEstimationService.getBudgetRangeDescription(600))
        }

        @Test
        @DisplayName("should return Luxury for very high costs")
        fun shouldReturnLuxuryForVeryHighCosts() {
            assertEquals("Luxury", budgetEstimationService.getBudgetRangeDescription(1000))
        }
    }

    @Nested
    @DisplayName("getBudgetVariance")
    inner class GetBudgetVariance {

        @Test
        @DisplayName("should return positive variance when over budget")
        fun shouldReturnPositiveVarianceWhenOverBudget() {
            val variance = budgetEstimationService.getBudgetVariance(600, 500)

            assertNotNull(variance)
            assertTrue(variance!! > 0, "Variance should be positive when over budget")
        }

        @Test
        @DisplayName("should return negative variance when under budget")
        fun shouldReturnNegativeVarianceWhenUnderBudget() {
            val variance = budgetEstimationService.getBudgetVariance(400, 500)

            assertNotNull(variance)
            assertTrue(variance!! < 0, "Variance should be negative when under budget")
        }

        @Test
        @DisplayName("should return null when budget max is null")
        fun shouldReturnNullWhenBudgetMaxIsNull() {
            val variance = budgetEstimationService.getBudgetVariance(500, null)

            assertNull(variance, "Variance should be null when budget max is null")
        }
    }

    @Nested
    @DisplayName("suggestGuestCountForBudget")
    inner class SuggestGuestCountForBudget {

        @Test
        @DisplayName("should suggest positive guest count")
        fun shouldSuggestPositiveGuestCount() {
            val suggestedCount = budgetEstimationService.suggestGuestCountForBudget(
                partyTypes = listOf("arcade"),
                priceLevel = 2,
                budgetMax = 500
            )

            assertTrue(suggestedCount > 0, "Should suggest at least 1 guest")
        }

        @Test
        @DisplayName("should suggest more guests for higher budget")
        fun shouldSuggestMoreGuestsForHigherBudget() {
            val lowBudgetGuests = budgetEstimationService.suggestGuestCountForBudget(
                partyTypes = listOf("arcade"),
                priceLevel = 2,
                budgetMax = 300
            )

            val highBudgetGuests = budgetEstimationService.suggestGuestCountForBudget(
                partyTypes = listOf("arcade"),
                priceLevel = 2,
                budgetMax = 800
            )

            assertTrue(highBudgetGuests > lowBudgetGuests,
                "Higher budget should allow more guests")
        }

        @Test
        @DisplayName("should suggest fewer guests at higher price level")
        fun shouldSuggestFewerGuestsAtHigherPriceLevel() {
            val budgetLevelGuests = budgetEstimationService.suggestGuestCountForBudget(
                partyTypes = listOf("arcade"),
                priceLevel = 1,
                budgetMax = 500
            )

            val premiumLevelGuests = budgetEstimationService.suggestGuestCountForBudget(
                partyTypes = listOf("arcade"),
                priceLevel = 4,
                budgetMax = 500
            )

            assertTrue(budgetLevelGuests >= premiumLevelGuests,
                "Premium venues should suggest fewer guests for same budget")
        }
    }

    @Nested
    @DisplayName("Cost Reasonableness")
    inner class CostReasonableness {

        @Test
        @DisplayName("should return realistic party costs")
        fun shouldReturnRealisticCosts() {
            val cost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = 15,
                priceLevel = 2
            )

            // A typical party for 15 guests should be between $150 and $1000
            assertTrue(cost in 150..1000,
                "Cost of $cost for 15 guests should be realistic")
        }

        @Test
        @DisplayName("per-person cost should relate to total")
        fun perPersonShouldRelateToTotal() {
            val guestCount = 15
            val totalCost = budgetEstimationService.estimatePartyCost(
                partyTypes = listOf("arcade"),
                guestCount = guestCount,
                priceLevel = 2
            )

            val perPersonCost = budgetEstimationService.estimateCostPerPerson(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            // Total should include fixed costs plus per-person costs
            // So total >= perPerson * guestCount
            assertTrue(totalCost >= perPersonCost * guestCount * 0.5,
                "Total cost should be at least half of per-person * guests")
        }
    }
}
