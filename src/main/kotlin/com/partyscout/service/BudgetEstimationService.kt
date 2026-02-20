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
        "active_play" to 25,
        "creative" to 32,
        "amusement" to 33,
        "outdoor" to 15,
        "characters_performers" to 35,
        "social_dining" to 22
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
        "active_play" to 75,
        "creative" to 60,
        "amusement" to 100,
        "outdoor" to 25,
        "characters_performers" to 150,
        "social_dining" to 50
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
