package com.partyscout.e2e

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.integration.mocks.TestGooglePlacesConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestGooglePlacesConfig::class)
@DisplayName("Budget Estimation End-to-End Tests")
class BudgetEstimationE2ETest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun baseUrl() = "http://localhost:$port"

    private fun createHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return headers
    }

    private fun estimateBudget(partyType: String, guestCount: Int, priceLevel: Int): Map<*, *>? {
        val request = mapOf(
            "partyTypes" to listOf(partyType),
            "guestCount" to guestCount,
            "priceLevel" to priceLevel
        )

        val response = restTemplate.postForEntity(
            "${baseUrl()}/api/v2/party-wizard/estimate-budget",
            HttpEntity(request, createHeaders()),
            Map::class.java
        )

        return if (response.statusCode == HttpStatus.OK) response.body else null
    }

    @Nested
    @DisplayName("Budget Estimation for Different Party Types")
    inner class DifferentPartyTypes {

        @Test
        @DisplayName("should estimate budget for active_play party")
        fun shouldEstimateBudgetForActivePlayParty() {
            val result = estimateBudget("active_play", 15, 2)

            assertNotNull(result)
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
            assertTrue((result["estimatedPerPerson"] as Int) > 0)
        }

        @Test
        @DisplayName("should estimate budget for creative party")
        fun shouldEstimateBudgetForCreativeParty() {
            val result = estimateBudget("creative", 15, 2)

            assertNotNull(result)
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
        }

        @Test
        @DisplayName("should estimate budget for amusement party")
        fun shouldEstimateBudgetForAmusementParty() {
            val result = estimateBudget("amusement", 15, 2)

            assertNotNull(result)
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
        }

        @Test
        @DisplayName("should estimate budget for outdoor party")
        fun shouldEstimateBudgetForOutdoorParty() {
            val result = estimateBudget("outdoor", 15, 2)

            assertNotNull(result)
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
        }

        @Test
        @DisplayName("should estimate budget for characters_performers party")
        fun shouldEstimateBudgetForCharactersPerformersParty() {
            val result = estimateBudget("characters_performers", 15, 2)

            assertNotNull(result)
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
        }

        @Test
        @DisplayName("should estimate budget for social_dining party")
        fun shouldEstimateBudgetForSocialDiningParty() {
            val result = estimateBudget("social_dining", 15, 2)

            assertNotNull(result)
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
        }
    }

    @Nested
    @DisplayName("Guest Count Scaling")
    inner class GuestCountScaling {

        @Test
        @DisplayName("should scale budget with guest count")
        fun shouldScaleBudgetWithGuestCount() {
            val small = estimateBudget("active_play", 10, 2)
            val medium = estimateBudget("active_play", 20, 2)
            val large = estimateBudget("active_play", 30, 2)

            assertNotNull(small)
            assertNotNull(medium)
            assertNotNull(large)

            val smallTotal = small!!["estimatedTotal"] as Int
            val mediumTotal = medium!!["estimatedTotal"] as Int
            val largeTotal = large!!["estimatedTotal"] as Int

            assertTrue(smallTotal < mediumTotal, "10 guests should cost less than 20 guests")
            assertTrue(mediumTotal < largeTotal, "20 guests should cost less than 30 guests")
        }

        @ParameterizedTest
        @CsvSource(
            "5, 100, 500",
            "10, 150, 800",
            "15, 200, 1000",
            "20, 250, 1200",
            "30, 350, 1800",
            "50, 500, 3000"
        )
        @DisplayName("should estimate reasonable budget for guest count")
        fun shouldEstimateReasonableBudgetForGuestCount(
            guestCount: Int,
            minExpected: Int,
            maxExpected: Int
        ) {
            val result = estimateBudget("active_play", guestCount, 2)

            assertNotNull(result)
            val total = result!!["estimatedTotal"] as Int

            assertTrue(
                total in minExpected..maxExpected,
                "Budget for $guestCount guests should be between $minExpected and $maxExpected, but was $total"
            )
        }
    }

    @Nested
    @DisplayName("Price Level Adjustments")
    inner class PriceLevelAdjustments {

        @Test
        @DisplayName("should increase budget with higher price level")
        fun shouldIncreaseBudgetWithHigherPriceLevel() {
            val budget = estimateBudget("active_play", 15, 1)
            val moderate = estimateBudget("active_play", 15, 2)
            val expensive = estimateBudget("active_play", 15, 3)
            val premium = estimateBudget("active_play", 15, 4)

            assertNotNull(budget)
            assertNotNull(moderate)
            assertNotNull(expensive)
            assertNotNull(premium)

            val budgetTotal = budget!!["estimatedTotal"] as Int
            val moderateTotal = moderate!!["estimatedTotal"] as Int
            val expensiveTotal = expensive!!["estimatedTotal"] as Int
            val premiumTotal = premium!!["estimatedTotal"] as Int

            assertTrue(budgetTotal < moderateTotal, "Budget should be less than moderate")
            assertTrue(moderateTotal < expensiveTotal, "Moderate should be less than expensive")
            assertTrue(expensiveTotal < premiumTotal, "Expensive should be less than premium")
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 2, 3, 4])
        @DisplayName("should handle all price levels")
        fun shouldHandleAllPriceLevels(priceLevel: Int) {
            val result = estimateBudget("active_play", 15, priceLevel)

            assertNotNull(result, "Should handle price level $priceLevel")
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
        }
    }

    @Nested
    @DisplayName("Per-Person Cost Calculations")
    inner class PerPersonCostCalculations {

        @Test
        @DisplayName("should calculate per-person cost correctly")
        fun shouldCalculatePerPersonCostCorrectly() {
            val result = estimateBudget("active_play", 15, 2)

            assertNotNull(result)

            val total = result!!["estimatedTotal"] as Int
            val perPerson = result["estimatedPerPerson"] as Int

            // Per-person cost should be approximately total / guest count
            // Allow some variance for fixed costs
            assertTrue(perPerson > 0, "Per-person cost should be positive")
            assertTrue(perPerson * 15 >= total * 0.5, "Per-person should be reasonable relative to total")
        }

        @Test
        @DisplayName("should keep per-person cost consistent across guest counts")
        fun shouldKeepPerPersonCostConsistentAcrossGuestCounts() {
            val small = estimateBudget("active_play", 10, 2)
            val large = estimateBudget("active_play", 30, 2)

            assertNotNull(small)
            assertNotNull(large)

            val smallPerPerson = small!!["estimatedPerPerson"] as Int
            val largePerPerson = large!!["estimatedPerPerson"] as Int

            // Per-person cost should be relatively similar regardless of group size
            // (within 50% variance due to fixed costs being spread)
            assertTrue(
                smallPerPerson in (largePerPerson / 2)..(largePerPerson * 2),
                "Per-person costs should be within 2x of each other"
            )
        }
    }

    @Nested
    @DisplayName("Budget Range Descriptions")
    inner class BudgetRangeDescriptions {

        @Test
        @DisplayName("should return response with budget category info")
        fun shouldReturnResponseWithBudgetCategoryInfo() {
            val result = estimateBudget("active_play", 15, 2)

            assertNotNull(result)
            // The response should contain useful budget information
            assertTrue(result!!.containsKey("estimatedTotal"))
            assertTrue(result.containsKey("estimatedPerPerson"))
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("should handle minimum guest count")
        fun shouldHandleMinimumGuestCount() {
            val result = estimateBudget("active_play", 1, 2)

            assertNotNull(result)
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
        }

        @Test
        @DisplayName("should handle large guest count")
        fun shouldHandleLargeGuestCount() {
            val result = estimateBudget("active_play", 100, 2)

            assertNotNull(result)
            assertTrue((result!!["estimatedTotal"] as Int) > 0)
        }

        @Test
        @DisplayName("should handle unknown party type gracefully")
        fun shouldHandleUnknownPartyTypeGracefully() {
            val result = estimateBudget("unknown_type", 15, 2)

            // Should still return a result (possibly with default values)
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("Realistic Cost Expectations")
    inner class RealisticCostExpectations {

        @Test
        @DisplayName("should estimate realistic costs for typical birthday party")
        fun shouldEstimateRealisticCostsForTypicalBirthdayParty() {
            // Typical 7-year-old birthday party: 15 kids, moderate venue
            val result = estimateBudget("active_play", 15, 2)

            assertNotNull(result)

            val total = result!!["estimatedTotal"] as Int
            val perPerson = result["estimatedPerPerson"] as Int

            // Realistic expectations for a moderate birthday party
            assertTrue(total in 150..1000, "Total should be realistic ($150-$1000), was $total")
            assertTrue(perPerson in 10..75, "Per-person should be realistic ($10-$75), was $perPerson")
        }

        @Test
        @DisplayName("should estimate higher costs for premium venues")
        fun shouldEstimateHigherCostsForPremiumVenues() {
            val result = estimateBudget("amusement", 20, 4)

            assertNotNull(result)

            val total = result!!["estimatedTotal"] as Int

            // Premium amusement venue for 20 kids should be expensive
            assertTrue(total >= 400, "Premium venue should cost at least $400, was $total")
        }

        @Test
        @DisplayName("should estimate lower costs for budget-friendly options")
        fun shouldEstimateLowerCostsForBudgetFriendlyOptions() {
            val result = estimateBudget("outdoor", 15, 1)

            assertNotNull(result)

            val total = result!!["estimatedTotal"] as Int

            // Budget outdoor party should be affordable
            assertTrue(total <= 500, "Budget outdoor party should cost at most $500, was $total")
        }
    }
}
