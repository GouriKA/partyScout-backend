package com.partyscout.model

import java.time.LocalDateTime

/**
 * Request model for the party wizard search endpoint
 */
data class PartySearchRequest(
    val age: Int,
    val partyTypes: List<String>,
    val guestCount: Int,
    val budgetMin: Int? = null,
    val budgetMax: Int? = null,
    val zipCode: String,
    val setting: String = "any", // indoor | outdoor | any
    val maxDistanceMiles: Int = 10,
    val date: LocalDateTime? = null // Optional party date
)

/**
 * Response model for party wizard search
 */
data class PartySearchResponse(
    val venues: List<EnhancedVenue>,
    val searchCriteria: PartySearchCriteria,
    val partyTypeSuggestions: List<PartyTypeSuggestion>
)

/**
 * Enhanced venue with match scoring and party details
 */
data class EnhancedVenue(
    val id: String,
    val name: String,
    val address: String,
    val rating: Double,
    val userRatingsTotal: Int,
    val phoneNumber: String?,
    val website: String?,
    val distanceInMiles: Double,
    val priceLevel: Int?, // 0-4 from Google Places
    val placeTypes: List<String>,
    val photos: List<String>,

    // Match scoring
    val matchScore: Int, // 0-100
    val matchReasons: List<String>,

    // Party-specific details
    val estimatedTotal: Int, // based on guest count
    val estimatedPricePerPerson: Int,
    val includedItems: List<String>,
    val notIncluded: List<String>,
    val suggestedAddOns: List<AddOn>,

    // Age appropriateness
    val popularForAges: String, // e.g., "Best for ages 5-10"
    val typicalPartyDuration: String, // e.g., "2 hours"

    // Capacity
    val minCapacity: Int,
    val maxCapacity: Int,

    // Setting
    val setting: String, // indoor | outdoor | both

    // Opening hours
    val isOpenOnDate: Boolean?,
    val openingHours: List<String>?
)

/**
 * Search criteria echoed back in response for party wizard
 */
data class PartySearchCriteria(
    val age: Int,
    val partyTypes: List<String>,
    val guestCount: Int,
    val budgetMin: Int?,
    val budgetMax: Int?,
    val zipCode: String,
    val setting: String,
    val maxDistanceMiles: Int,
    val date: LocalDateTime?
)

/**
 * Party type suggestion based on age
 */
data class PartyTypeSuggestion(
    val type: String,
    val displayName: String,
    val description: String,
    val icon: String, // emoji or icon name
    val ageRange: String, // e.g., "Ages 5-8"
    val averageCost: String, // e.g., "$200-400"
    val popularityScore: Int // 1-5
)

/**
 * Suggested add-on for a party
 */
data class AddOn(
    val name: String,
    val description: String,
    val estimatedCost: Int,
    val isRecommended: Boolean
)

/**
 * Party type taxonomy entry
 */
data class PartyTypeTaxonomy(
    val type: String,
    val displayName: String,
    val description: String,
    val icon: String,
    val minAge: Int,
    val maxAge: Int,
    val googlePlacesTypes: List<String>,
    val searchKeywords: List<String>,
    val typicalDuration: String,
    val averageCostPerPerson: IntRange,
    val setting: String // indoor | outdoor | both
)

/**
 * Venue capacity info
 */
data class CapacityInfo(
    val min: Int,
    val max: Int,
    val recommendedGroupSize: String
)
