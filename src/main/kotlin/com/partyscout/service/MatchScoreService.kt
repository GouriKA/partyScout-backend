package com.partyscout.service

import com.partyscout.model.PartySearchRequest
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min

/**
 * Service for calculating venue match scores based on party requirements.
 * Score breakdown:
 * - Age Appropriateness: 25 points
 * - Budget Match: 25 points
 * - Capacity Match: 20 points
 * - Distance: 15 points
 * - Rating Quality: 10 points
 * - Venue Type Match: 5 points
 */
@Service
class MatchScoreService(
    private val partyTypeService: PartyTypeService,
    private val budgetEstimationService: BudgetEstimationService
) {

    data class MatchScoreResult(
        val totalScore: Int,
        val reasons: List<String>,
        val breakdown: ScoreBreakdown
    )

    data class ScoreBreakdown(
        val ageScore: Int,
        val budgetScore: Int,
        val capacityScore: Int,
        val distanceScore: Int,
        val ratingScore: Int,
        val typeMatchScore: Int
    )

    /**
     * Calculate comprehensive match score for a venue
     */
    fun calculateMatchScore(
        request: PartySearchRequest,
        venuePlaceTypes: List<String>,
        venueRating: Double?,
        venueUserRatingsTotal: Int?,
        venuePriceLevel: Int?,
        venueDistanceMiles: Double,
        venueMinCapacity: Int,
        venueMaxCapacity: Int
    ): MatchScoreResult {
        val reasons = mutableListOf<String>()

        // 1. Age Appropriateness (25 points)
        val ageScore = calculateAgeScore(request.age, request.partyTypes, venuePlaceTypes)
        if (ageScore >= 20) reasons.add("Perfect for ${request.age}-year-olds")
        else if (ageScore >= 15) reasons.add("Good for this age group")

        // 2. Budget Match (25 points)
        val estimatedCost = budgetEstimationService.estimatePartyCost(
            partyTypes = request.partyTypes,
            guestCount = request.guestCount,
            priceLevel = venuePriceLevel
        )
        val budgetScore = calculateBudgetScore(estimatedCost, request.budgetMin, request.budgetMax)
        if (budgetScore >= 20) reasons.add("Within your budget")
        else if (budgetScore >= 10) reasons.add("Close to budget")
        else if (budgetScore < 10 && request.budgetMax != null) reasons.add("May exceed budget")

        // 3. Capacity Match (20 points)
        val capacityScore = calculateCapacityScore(request.guestCount, venueMinCapacity, venueMaxCapacity)
        if (capacityScore >= 18) reasons.add("Ideal for ${request.guestCount} guests")
        else if (capacityScore >= 12) reasons.add("Can accommodate your group")
        else if (capacityScore < 10) reasons.add("Group size may be tight fit")

        // 4. Distance (15 points)
        val distanceScore = calculateDistanceScore(venueDistanceMiles, request.maxDistanceMiles)
        if (distanceScore >= 12) reasons.add("Very close by")
        else if (venueDistanceMiles <= 5) reasons.add("Convenient location")

        // 5. Rating Quality (10 points)
        val ratingScore = calculateRatingScore(venueRating, venueUserRatingsTotal)
        if (ratingScore >= 8) reasons.add("Highly rated")
        else if (ratingScore >= 6) reasons.add("Good reviews")

        // 6. Venue Type Match (5 points)
        val typeMatchScore = calculateTypeMatchScore(request.partyTypes, venuePlaceTypes)
        if (typeMatchScore == 5) reasons.add("Matches your party style")

        val totalScore = minOf(100, ageScore + budgetScore + capacityScore + distanceScore + ratingScore + typeMatchScore)

        return MatchScoreResult(
            totalScore = totalScore,
            reasons = reasons,
            breakdown = ScoreBreakdown(
                ageScore = ageScore,
                budgetScore = budgetScore,
                capacityScore = capacityScore,
                distanceScore = distanceScore,
                ratingScore = ratingScore,
                typeMatchScore = typeMatchScore
            )
        )
    }

    /**
     * Age Appropriateness Score (0-25 points)
     * Based on whether the venue type is suitable for the child's age
     */
    private fun calculateAgeScore(age: Int, requestedPartyTypes: List<String>, venuePlaceTypes: List<String>): Int {
        // Get all party types appropriate for this age
        val ageAppropriateTypes = partyTypeService.getPartyTypesForAge(age)
        val ageAppropriateTypeIds = ageAppropriateTypes.map { it.type }

        // Check if any requested party types match age-appropriate ones
        val matchingTypes = requestedPartyTypes.filter { it in ageAppropriateTypeIds }

        // Also check venue place types against what we expect for these party types
        val expectedPlaceTypes = requestedPartyTypes.flatMap {
            partyTypeService.getGooglePlacesTypesForPartyType(it)
        }.distinct()

        val placeTypeMatch = venuePlaceTypes.any { it in expectedPlaceTypes }

        return when {
            matchingTypes.isNotEmpty() && placeTypeMatch -> 25 // Perfect match
            matchingTypes.isNotEmpty() -> 20 // Good age match, venue type may vary
            placeTypeMatch -> 15 // Venue matches but not age-optimal
            else -> 10 // Generic venue
        }
    }

    /**
     * Budget Match Score (0-25 points)
     * Based on whether estimated cost fits within user's budget
     */
    private fun calculateBudgetScore(estimatedCost: Int, budgetMin: Int?, budgetMax: Int?): Int {
        // If no budget specified, give moderate score
        if (budgetMin == null && budgetMax == null) return 15

        val min = budgetMin ?: 0
        val max = budgetMax ?: Int.MAX_VALUE

        return when {
            estimatedCost in min..max -> 25 // Within budget
            estimatedCost < min -> {
                // Under budget - might be missing features
                val underBy = ((min - estimatedCost).toDouble() / min * 100).toInt()
                if (underBy <= 20) 22 else 18
            }
            else -> {
                // Over budget
                val overBy = ((estimatedCost - max).toDouble() / max * 100).toInt()
                when {
                    overBy <= 10 -> 18 // Slightly over
                    overBy <= 25 -> 12 // Moderately over
                    overBy <= 50 -> 6  // Significantly over
                    else -> 2          // Way over budget
                }
            }
        }
    }

    /**
     * Capacity Match Score (0-20 points)
     * Based on whether venue can handle guest count comfortably
     */
    private fun calculateCapacityScore(guestCount: Int, minCapacity: Int, maxCapacity: Int): Int {
        // Ideal is when guest count is between 50-80% of max capacity
        // (not too empty, not too cramped)
        val idealLow = maxCapacity * 0.5
        val idealHigh = maxCapacity * 0.8

        return when {
            guestCount < minCapacity -> {
                // Too few guests for venue
                val ratio = guestCount.toDouble() / minCapacity
                (ratio * 10).toInt().coerceIn(2, 10)
            }
            guestCount > maxCapacity -> {
                // Too many guests
                val overBy = ((guestCount - maxCapacity).toDouble() / maxCapacity * 100).toInt()
                when {
                    overBy <= 10 -> 10 // Slightly over
                    overBy <= 20 -> 5  // Moderately over
                    else -> 0          // Way over
                }
            }
            guestCount in idealLow.toInt()..idealHigh.toInt() -> 20 // Ideal range
            guestCount < idealLow -> 16 // Under-utilizing
            else -> 14 // Near capacity but ok
        }
    }

    /**
     * Distance Score (0-15 points)
     * Based on how close venue is relative to max distance preference
     */
    private fun calculateDistanceScore(distanceMiles: Double, maxDistanceMiles: Int): Int {
        val ratio = distanceMiles / maxDistanceMiles

        return when {
            ratio <= 0.2 -> 15  // Very close (within 20% of max)
            ratio <= 0.4 -> 13
            ratio <= 0.6 -> 11
            ratio <= 0.8 -> 9
            ratio <= 1.0 -> 7   // At edge of range
            ratio <= 1.2 -> 4   // Slightly beyond
            else -> 1           // Too far
        }
    }

    /**
     * Rating Quality Score (0-10 points)
     * Based on Google rating and number of reviews
     */
    private fun calculateRatingScore(rating: Double?, userRatingsTotal: Int?): Int {
        val effectiveRating = rating ?: 3.5
        val reviewCount = userRatingsTotal ?: 0

        // Base score from rating (0-7)
        val ratingPoints = when {
            effectiveRating >= 4.5 -> 7
            effectiveRating >= 4.0 -> 6
            effectiveRating >= 3.5 -> 4
            effectiveRating >= 3.0 -> 2
            else -> 1
        }

        // Bonus for review volume (0-3)
        val volumeBonus = when {
            reviewCount >= 500 -> 3
            reviewCount >= 200 -> 2
            reviewCount >= 50 -> 1
            else -> 0
        }

        return minOf(10, ratingPoints + volumeBonus)
    }

    /**
     * Venue Type Match Score (0-5 points)
     * Based on direct match between requested party types and venue types
     */
    private fun calculateTypeMatchScore(requestedPartyTypes: List<String>, venuePlaceTypes: List<String>): Int {
        val expectedPlaceTypes = requestedPartyTypes.flatMap {
            partyTypeService.getGooglePlacesTypesForPartyType(it)
        }.distinct()

        val matchCount = venuePlaceTypes.count { it in expectedPlaceTypes }

        return when {
            matchCount >= 2 -> 5  // Strong match
            matchCount == 1 -> 3  // Partial match
            else -> 1             // No direct match
        }
    }

    /**
     * Get a human-readable match quality label
     */
    fun getMatchQualityLabel(score: Int): String {
        return when {
            score >= 85 -> "Excellent Match"
            score >= 70 -> "Great Match"
            score >= 55 -> "Good Match"
            score >= 40 -> "Possible Match"
            else -> "Limited Match"
        }
    }
}
