package com.partyscout.party.service

import com.partyscout.party.model.PartySearchRequest
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min

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

        val ageScore = calculateAgeScore(request.age, request.partyTypes, venuePlaceTypes)
        if (ageScore >= 20) reasons.add("Perfect for ${request.age}-year-olds")
        else if (ageScore >= 15) reasons.add("Good for this age group")

        val estimatedCost = budgetEstimationService.estimatePartyCost(
            partyTypes = request.partyTypes, guestCount = request.guestCount, priceLevel = venuePriceLevel
        )
        val budgetScore = calculateBudgetScore(estimatedCost, request.budgetMin, request.budgetMax)
        if (budgetScore >= 20) reasons.add("Within your budget")
        else if (budgetScore >= 10) reasons.add("Close to budget")
        else if (budgetScore < 10 && request.budgetMax != null) reasons.add("May exceed budget")

        val capacityScore = calculateCapacityScore(request.guestCount, venueMinCapacity, venueMaxCapacity)
        if (capacityScore >= 18) reasons.add("Ideal for ${request.guestCount} guests")
        else if (capacityScore >= 12) reasons.add("Can accommodate your group")
        else if (capacityScore < 10) reasons.add("Group size may be tight fit")

        val distanceScore = calculateDistanceScore(venueDistanceMiles, request.maxDistanceMiles)
        if (distanceScore >= 12) reasons.add("Very close by")
        else if (venueDistanceMiles <= 5) reasons.add("Convenient location")

        val ratingScore = calculateRatingScore(venueRating, venueUserRatingsTotal)
        if (ratingScore >= 8) reasons.add("Highly rated")
        else if (ratingScore >= 6) reasons.add("Good reviews")

        val typeMatchScore = calculateTypeMatchScore(request.partyTypes, venuePlaceTypes)
        if (typeMatchScore == 5) reasons.add("Matches your party style")

        val totalScore = minOf(100, ageScore + budgetScore + capacityScore + distanceScore + ratingScore + typeMatchScore)

        return MatchScoreResult(
            totalScore = totalScore,
            reasons = reasons,
            breakdown = ScoreBreakdown(
                ageScore = ageScore, budgetScore = budgetScore, capacityScore = capacityScore,
                distanceScore = distanceScore, ratingScore = ratingScore, typeMatchScore = typeMatchScore
            )
        )
    }

    private fun calculateAgeScore(age: Int, requestedPartyTypes: List<String>, venuePlaceTypes: List<String>): Int {
        val ageAppropriateTypes = partyTypeService.getPartyTypesForAge(age)
        val ageAppropriateTypeIds = ageAppropriateTypes.map { it.type }
        val matchingTypes = requestedPartyTypes.filter { it in ageAppropriateTypeIds }
        val expectedPlaceTypes = requestedPartyTypes.flatMap { partyTypeService.getGooglePlacesTypesForPartyType(it) }.distinct()
        val placeTypeMatch = venuePlaceTypes.any { it in expectedPlaceTypes }
        return when {
            matchingTypes.isNotEmpty() && placeTypeMatch -> 25
            matchingTypes.isNotEmpty() -> 20
            placeTypeMatch -> 15
            else -> 10
        }
    }

    private fun calculateBudgetScore(estimatedCost: Int, budgetMin: Int?, budgetMax: Int?): Int {
        if (budgetMin == null && budgetMax == null) return 15
        val min = budgetMin ?: 0
        val max = budgetMax ?: Int.MAX_VALUE
        return when {
            estimatedCost in min..max -> 25
            estimatedCost < min -> {
                val underBy = ((min - estimatedCost).toDouble() / min * 100).toInt()
                if (underBy <= 20) 22 else 18
            }
            else -> {
                val overBy = ((estimatedCost - max).toDouble() / max * 100).toInt()
                when {
                    overBy <= 10 -> 18; overBy <= 25 -> 12; overBy <= 50 -> 6; else -> 2
                }
            }
        }
    }

    private fun calculateCapacityScore(guestCount: Int, minCapacity: Int, maxCapacity: Int): Int {
        val idealLow = maxCapacity * 0.5
        val idealHigh = maxCapacity * 0.8
        return when {
            guestCount < minCapacity -> {
                val ratio = guestCount.toDouble() / minCapacity
                (ratio * 10).toInt().coerceIn(2, 10)
            }
            guestCount > maxCapacity -> {
                val overBy = ((guestCount - maxCapacity).toDouble() / maxCapacity * 100).toInt()
                when { overBy <= 10 -> 10; overBy <= 20 -> 5; else -> 0 }
            }
            guestCount in idealLow.toInt()..idealHigh.toInt() -> 20
            guestCount < idealLow -> 16
            else -> 14
        }
    }

    private fun calculateDistanceScore(distanceMiles: Double, maxDistanceMiles: Int): Int {
        val ratio = distanceMiles / maxDistanceMiles
        return when {
            ratio <= 0.2 -> 15; ratio <= 0.4 -> 13; ratio <= 0.6 -> 11
            ratio <= 0.8 -> 9; ratio <= 1.0 -> 7; ratio <= 1.2 -> 4; else -> 1
        }
    }

    private fun calculateRatingScore(rating: Double?, userRatingsTotal: Int?): Int {
        val effectiveRating = rating ?: 3.5
        val reviewCount = userRatingsTotal ?: 0
        val ratingPoints = when {
            effectiveRating >= 4.5 -> 7; effectiveRating >= 4.0 -> 6; effectiveRating >= 3.5 -> 4
            effectiveRating >= 3.0 -> 2; else -> 1
        }
        val volumeBonus = when {
            reviewCount >= 500 -> 3; reviewCount >= 200 -> 2; reviewCount >= 50 -> 1; else -> 0
        }
        return minOf(10, ratingPoints + volumeBonus)
    }

    private fun calculateTypeMatchScore(requestedPartyTypes: List<String>, venuePlaceTypes: List<String>): Int {
        val expectedPlaceTypes = requestedPartyTypes.flatMap { partyTypeService.getGooglePlacesTypesForPartyType(it) }.distinct()
        val matchCount = venuePlaceTypes.count { it in expectedPlaceTypes }
        return when { matchCount >= 2 -> 5; matchCount == 1 -> 3; else -> 1 }
    }

    fun getMatchQualityLabel(score: Int): String {
        return when {
            score >= 85 -> "Excellent Match"; score >= 70 -> "Great Match"; score >= 55 -> "Good Match"
            score >= 40 -> "Possible Match"; else -> "Limited Match"
        }
    }
}
