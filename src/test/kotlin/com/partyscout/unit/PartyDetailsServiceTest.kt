package com.partyscout.unit

import com.partyscout.service.PartyDetailsService
import com.partyscout.service.PartyTypeService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("PartyDetailsService")
class PartyDetailsServiceTest {

    private lateinit var partyDetailsService: PartyDetailsService
    private lateinit var partyTypeService: PartyTypeService

    @BeforeEach
    fun setUp() {
        partyTypeService = PartyTypeService()
        partyDetailsService = PartyDetailsService(partyTypeService)
    }

    @Nested
    @DisplayName("getIncludedItems")
    inner class GetIncludedItems {

        @Test
        @DisplayName("should return list of included items for known party type")
        fun shouldReturnIncludedItems() {
            val items = partyDetailsService.getIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            // arcade type has defined inclusions
            assertNotNull(items)
        }

        @ParameterizedTest
        @ValueSource(strings = ["arcade", "bounce_house", "outdoor", "sports", "arts_crafts"])
        @DisplayName("should return items for known party types")
        fun shouldReturnItemsForKnownTypes(partyType: String) {
            val items = partyDetailsService.getIncludedItems(
                partyTypes = listOf(partyType),
                priceLevel = 2
            )

            assertFalse(items.isEmpty(), "Should return items for $partyType")
        }

        @Test
        @DisplayName("should include more items at higher price levels")
        fun shouldIncludeMoreAtHigherPriceLevel() {
            val budgetItems = partyDetailsService.getIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = 1
            )

            val premiumItems = partyDetailsService.getIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = 4
            )

            assertTrue(premiumItems.size >= budgetItems.size,
                "Premium venues should include at least as many items")
        }

        @Test
        @DisplayName("should handle empty party types list")
        fun shouldHandleEmptyPartyTypes() {
            val items = partyDetailsService.getIncludedItems(
                partyTypes = emptyList(),
                priceLevel = 2
            )

            // Should not throw, may return empty list
            assertNotNull(items)
        }

        @Test
        @DisplayName("should handle null price level")
        fun shouldHandleNullPriceLevel() {
            val items = partyDetailsService.getIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = null
            )

            assertNotNull(items)
        }

        @Test
        @DisplayName("should combine items from multiple party types")
        fun shouldCombineItemsFromMultiplePartyTypes() {
            val singleTypeItems = partyDetailsService.getIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            val multiTypeItems = partyDetailsService.getIncludedItems(
                partyTypes = listOf("arcade", "bounce_house"),
                priceLevel = 2
            )

            assertTrue(multiTypeItems.size >= singleTypeItems.size,
                "Multiple party types should have at least as many items")
        }
    }

    @Nested
    @DisplayName("getNotIncludedItems")
    inner class GetNotIncludedItems {

        @Test
        @DisplayName("should return list of not-included items")
        fun shouldReturnNotIncludedItems() {
            val items = partyDetailsService.getNotIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            assertFalse(items.isEmpty(), "Should return at least one not-included item")
        }

        @Test
        @DisplayName("should typically include cake in not-included")
        fun shouldIncludeCake() {
            val items = partyDetailsService.getNotIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            assertTrue(items.any { it.contains("cake", ignoreCase = true) },
                "Cake is typically not included")
        }

        @Test
        @DisplayName("should have fewer not-included items at higher price levels")
        fun shouldHaveFewerAtHigherPriceLevel() {
            val budgetNotIncluded = partyDetailsService.getNotIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = 1
            )

            val premiumNotIncluded = partyDetailsService.getNotIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = 4
            )

            assertTrue(premiumNotIncluded.size <= budgetNotIncluded.size,
                "Premium venues should have fewer not-included items")
        }

        @Test
        @DisplayName("should handle null price level")
        fun shouldHandleNullPriceLevel() {
            val items = partyDetailsService.getNotIncludedItems(
                partyTypes = listOf("arcade"),
                priceLevel = null
            )

            assertNotNull(items)
        }
    }

    @Nested
    @DisplayName("getSuggestedAddOns")
    inner class GetSuggestedAddOns {

        @Test
        @DisplayName("should return add-ons list")
        fun shouldReturnAddOns() {
            val addOns = partyDetailsService.getSuggestedAddOns(
                partyTypes = listOf("arcade"),
                guestCount = 15
            )

            assertNotNull(addOns)
            assertFalse(addOns.isEmpty(), "Should return add-ons for arcade type")
        }

        @Test
        @DisplayName("add-ons should have positive costs")
        fun addOnsShouldHavePositiveCosts() {
            val addOns = partyDetailsService.getSuggestedAddOns(
                partyTypes = listOf("arcade"),
                guestCount = 15
            )

            if (addOns.isNotEmpty()) {
                assertTrue(addOns.all { it.estimatedCost > 0 },
                    "All add-ons should have positive costs")
            }
        }

        @Test
        @DisplayName("should have at least one recommended add-on for known types")
        fun shouldHaveRecommendedAddOn() {
            val addOns = partyDetailsService.getSuggestedAddOns(
                partyTypes = listOf("arcade"),
                guestCount = 15
            )

            if (addOns.isNotEmpty()) {
                assertTrue(addOns.any { it.isRecommended },
                    "Should have at least one recommended add-on")
            }
        }

        @Test
        @DisplayName("add-on costs should scale with guest count for per-person items")
        fun addOnCostsShouldScaleWithGuests() {
            val smallPartyAddOns = partyDetailsService.getSuggestedAddOns(
                partyTypes = listOf("arcade"),
                guestCount = 10
            )

            val largePartyAddOns = partyDetailsService.getSuggestedAddOns(
                partyTypes = listOf("arcade"),
                guestCount = 30
            )

            if (smallPartyAddOns.isNotEmpty() && largePartyAddOns.isNotEmpty()) {
                val smallTotal = smallPartyAddOns.sumOf { it.estimatedCost }
                val largeTotal = largePartyAddOns.sumOf { it.estimatedCost }

                assertTrue(largeTotal >= smallTotal,
                    "Add-on costs should be higher for larger parties")
            }
        }

        @Test
        @DisplayName("should handle empty party types list")
        fun shouldHandleEmptyPartyTypes() {
            val addOns = partyDetailsService.getSuggestedAddOns(
                partyTypes = emptyList(),
                guestCount = 15
            )

            assertNotNull(addOns)
            assertTrue(addOns.isEmpty(), "Should return empty list for empty party types")
        }
    }

    @Nested
    @DisplayName("getTypicalDuration")
    inner class GetTypicalDuration {

        @Test
        @DisplayName("should return duration string")
        fun shouldReturnDuration() {
            val duration = partyDetailsService.getTypicalDuration(listOf("arcade"))

            assertNotNull(duration)
            assertTrue(duration.contains("hour", ignoreCase = true),
                "Should contain 'hour' in duration")
        }

        @Test
        @DisplayName("should return default duration for empty list")
        fun shouldReturnDefaultDurationForEmptyList() {
            val duration = partyDetailsService.getTypicalDuration(emptyList())

            assertEquals("2 hours", duration)
        }

        @ParameterizedTest
        @ValueSource(strings = ["arcade", "bounce_house", "outdoor", "sports"])
        @DisplayName("should return duration for known party types")
        fun shouldReturnDurationForKnownTypes(partyType: String) {
            val duration = partyDetailsService.getTypicalDuration(listOf(partyType))

            assertNotNull(duration)
            assertFalse(duration.isBlank())
        }
    }

    @Nested
    @DisplayName("getWhatToBring")
    inner class GetWhatToBring {

        @Test
        @DisplayName("should return what to bring list")
        fun shouldReturnWhatToBring() {
            val items = partyDetailsService.getWhatToBring(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            assertFalse(items.isEmpty(), "Should return what to bring items")
        }

        @Test
        @DisplayName("should include camera suggestion")
        fun shouldIncludeCamera() {
            val items = partyDetailsService.getWhatToBring(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            assertTrue(items.any { it.contains("camera", ignoreCase = true) },
                "Should include camera suggestion")
        }

        @Test
        @DisplayName("should include cake suggestion")
        fun shouldIncludeCake() {
            val items = partyDetailsService.getWhatToBring(
                partyTypes = listOf("arcade"),
                priceLevel = 2
            )

            assertTrue(items.any { it.contains("cake", ignoreCase = true) },
                "Should include cake suggestion")
        }

        @Test
        @DisplayName("should include extra items for outdoor parties")
        fun shouldIncludeExtraItemsForOutdoor() {
            val outdoorItems = partyDetailsService.getWhatToBring(
                partyTypes = listOf("outdoor"),
                priceLevel = 2
            )

            assertTrue(outdoorItems.any { it.contains("sunscreen", ignoreCase = true) },
                "Should include sunscreen for outdoor parties")
        }

        @Test
        @DisplayName("should include extra items for pool parties")
        fun shouldIncludeExtraItemsForPool() {
            val poolItems = partyDetailsService.getWhatToBring(
                partyTypes = listOf("pool_party"),
                priceLevel = 2
            )

            assertTrue(poolItems.any { it.contains("towel", ignoreCase = true) },
                "Should include towels for pool parties")
        }

        @Test
        @DisplayName("should include more items at budget price level")
        fun shouldIncludeMoreAtBudgetLevel() {
            val budgetItems = partyDetailsService.getWhatToBring(
                partyTypes = listOf("arcade"),
                priceLevel = 1
            )

            val premiumItems = partyDetailsService.getWhatToBring(
                partyTypes = listOf("arcade"),
                priceLevel = 4
            )

            assertTrue(budgetItems.size >= premiumItems.size,
                "Budget venues should require bringing more items")
        }
    }

    @Nested
    @DisplayName("getAgeAppropriatenessDescription")
    inner class GetAgeAppropriatenessDescription {

        @Test
        @DisplayName("should return age range description")
        fun shouldReturnAgeRangeDescription() {
            val description = partyDetailsService.getAgeAppropriatenessDescription(listOf("arcade"))

            assertNotNull(description)
            assertTrue(description.contains("age", ignoreCase = true) ||
                    description.contains("Best for", ignoreCase = true),
                "Should contain age-related text")
        }

        @Test
        @DisplayName("should return default for empty party types")
        fun shouldReturnDefaultForEmptyPartyTypes() {
            val description = partyDetailsService.getAgeAppropriatenessDescription(emptyList())

            assertEquals("Suitable for various ages", description)
        }

        @Test
        @DisplayName("should handle unknown party types")
        fun shouldHandleUnknownPartyTypes() {
            val description = partyDetailsService.getAgeAppropriatenessDescription(listOf("unknown_type"))

            assertNotNull(description)
        }
    }
}
