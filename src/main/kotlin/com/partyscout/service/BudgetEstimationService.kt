package com.partyscout.service

import org.springframework.stereotype.Service

/**
 * Service for estimating party costs based on venue type, guest count, and price level
 */
@Service
class BudgetEstimationService(
    private val partyTypeService: PartyTypeService
) {

    /**
     * Base cost per person by party type (at price level 2 - moderate)
     */
    private val baseCostPerPerson: Map<String, Int> = mapOf(
        "toddler_play" to 20,
        "character_party" to 35,
        "bounce_house" to 25,
        "arcade" to 30,
        "sports" to 28,
        "arts_crafts" to 32,
        "outdoor" to 15,
        "escape_room" to 38,
        "movies" to 22,
        "pool_party" to 28,
        "go_karts" to 40,
        "adventure_park" to 45
    )

    /**
     * Price level multipliers (Google Places price level 0-4)
     */
    private val priceLevelMultipliers: Map<Int?, Double> = mapOf(
        0 to 0.6,    // Free/very cheap
        1 to 0.8,    // Inexpensive
        2 to 1.0,    // Moderate
        3 to 1.3,    // Expensive
        4 to 1.6,    // Very expensive
        null to 1.0  // Unknown - assume moderate
    )

    /**
     * Fixed costs that don't scale with guest count
     */
    private val fixedCosts: Map<String, Int> = mapOf(
        "toddler_play" to 50,      // Room rental
        "character_party" to 150,  // Character fee
        "bounce_house" to 75,      // Party room
        "arcade" to 100,           // Party package fee
        "sports" to 80,            // Lane/court rental
        "arts_crafts" to 60,       // Materials setup
        "outdoor" to 25,           // Pavilion rental
        "escape_room" to 100,      // Room booking
        "movies" to 200,           // Theater rental
        "pool_party" to 100,       // Pool access
        "go_karts" to 50,          // Group booking fee
        "adventure_park" to 75     // Group rate
    )

    /**
     * Estimate total party cost based on parameters
     */
    fun estimatePartyCost(
        partyTypes: List<String>,
        guestCount: Int,
        priceLevel: Int?
    ): Int {
        if (partyTypes.isEmpty()) {
            // Default estimate for unknown party type
            return (25 * guestCount * (priceLevelMultipliers[priceLevel] ?: 1.0) + 75).toInt()
        }

        // Use average of selected party types
        val avgBaseCost = partyTypes
            .mapNotNull { baseCostPerPerson[it] }
            .average()
            .let { if (it.isNaN()) 25.0 else it }

        val avgFixedCost = partyTypes
            .mapNotNull { fixedCosts[it] }
            .average()
            .let { if (it.isNaN()) 75.0 else it }

        val multiplier = priceLevelMultipliers[priceLevel] ?: 1.0

        val perPersonCost = (avgBaseCost * multiplier).toInt()
        val totalVariableCost = perPersonCost * guestCount
        val totalFixedCost = (avgFixedCost * multiplier).toInt()

        return totalVariableCost + totalFixedCost
    }

    /**
     * Estimate cost per person
     */
    fun estimateCostPerPerson(
        partyTypes: List<String>,
        priceLevel: Int?
    ): Int {
        if (partyTypes.isEmpty()) {
            return (25 * (priceLevelMultipliers[priceLevel] ?: 1.0)).toInt()
        }

        val avgBaseCost = partyTypes
            .mapNotNull { baseCostPerPerson[it] }
            .average()
            .let { if (it.isNaN()) 25.0 else it }

        val multiplier = priceLevelMultipliers[priceLevel] ?: 1.0

        return (avgBaseCost * multiplier).toInt()
    }

    /**
     * Get budget range description
     */
    fun getBudgetRangeDescription(estimatedCost: Int): String {
        return when {
            estimatedCost < 150 -> "Budget-Friendly"
            estimatedCost < 300 -> "Moderate"
            estimatedCost < 500 -> "Premium"
            estimatedCost < 800 -> "Deluxe"
            else -> "Luxury"
        }
    }

    /**
     * Check if cost is within budget
     */
    fun isWithinBudget(estimatedCost: Int, budgetMin: Int?, budgetMax: Int?): Boolean {
        val min = budgetMin ?: 0
        val max = budgetMax ?: Int.MAX_VALUE
        return estimatedCost in min..max
    }

    /**
     * Get percentage over/under budget
     */
    fun getBudgetVariance(estimatedCost: Int, budgetMax: Int?): Int? {
        if (budgetMax == null) return null
        return ((estimatedCost - budgetMax).toDouble() / budgetMax * 100).toInt()
    }

    /**
     * Suggest guest count adjustment to fit budget
     */
    fun suggestGuestCountForBudget(
        partyTypes: List<String>,
        priceLevel: Int?,
        budgetMax: Int
    ): Int {
        val perPersonCost = estimateCostPerPerson(partyTypes, priceLevel)
        val avgFixedCost = partyTypes
            .mapNotNull { fixedCosts[it] }
            .average()
            .let { if (it.isNaN()) 75.0 else it }
            .let { (it * (priceLevelMultipliers[priceLevel] ?: 1.0)).toInt() }

        val availableForGuests = budgetMax - avgFixedCost
        return maxOf(1, availableForGuests / perPersonCost)
    }
}
