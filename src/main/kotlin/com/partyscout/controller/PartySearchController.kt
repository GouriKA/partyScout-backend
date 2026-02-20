package com.partyscout.controller

import com.partyscout.dto.Location
import com.partyscout.dto.Place
import com.partyscout.model.*
import com.partyscout.service.*
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@RestController
@RequestMapping("/api/v2/party-wizard")
class PartySearchController(
    private val googlePlacesService: GooglePlacesService,
    private val partyTypeService: PartyTypeService,
    private val matchScoreService: MatchScoreService,
    private val budgetEstimationService: BudgetEstimationService,
    private val partyDetailsService: PartyDetailsService
) {
    private val logger = LoggerFactory.getLogger(PartySearchController::class.java)

    /**
     * Main party search endpoint for the wizard
     */
    @PostMapping("/search")
    fun searchPartyVenues(@Valid @RequestBody request: PartySearchRequest): ResponseEntity<PartySearchResponse> {
        logger.info("Party wizard search: age=${request.age}, types=${request.partyTypes}, guests=${request.guestCount}, zip=${request.zipCode}")

        // Get Google Places types based on selected party types
        val googlePlacesTypes = if (request.partyTypes.isNotEmpty()) {
            partyTypeService.getGooglePlacesTypesForPartyTypes(request.partyTypes)
        } else {
            listOf("amusement_center", "bowling_alley", "park")
        }

        // Convert distance to meters
        val radiusMeters = (request.maxDistanceMiles * 1609.34).toInt()

        // Geocode ZIP and search - block for synchronous response
        val location = googlePlacesService.geocodeZipCode(request.zipCode).block()
            ?: return ResponseEntity.badRequest().build()

        val searchResponse = googlePlacesService.searchNearbyPlaces(location, googlePlacesTypes, radiusMeters).block()
            ?: return ResponseEntity.internalServerError().build()

        val venues = searchResponse.places?.mapNotNull { place ->
            try {
                mapToEnhancedVenue(place, location, request)
            } catch (e: Exception) {
                logger.warn("Failed to map place: ${e.message}")
                null
            }
        }
            ?.filter { filterBySetting(it, request.setting) }
            ?.filter { it.distanceInMiles <= request.maxDistanceMiles }
            ?.sortedByDescending { it.matchScore }
            ?: emptyList()

        val response = PartySearchResponse(
            venues = venues,
            searchCriteria = PartySearchCriteria(
                age = request.age,
                partyTypes = request.partyTypes,
                guestCount = request.guestCount,
                budgetMin = request.budgetMin,
                budgetMax = request.budgetMax,
                zipCode = request.zipCode,
                setting = request.setting,
                maxDistanceMiles = request.maxDistanceMiles,
                date = request.date
            ),
            partyTypeSuggestions = partyTypeService.getPartyTypesForAge(request.age)
        )

        logger.info("Returning ${venues.size} venue options")
        return ResponseEntity.ok(response)
    }

    /**
     * Get party type suggestions for an age
     */
    @GetMapping("/party-types/{age}")
    fun getPartyTypesForAge(@PathVariable age: Int): ResponseEntity<List<PartyTypeSuggestion>> {
        val suggestions = partyTypeService.getPartyTypesForAge(age)
        return ResponseEntity.ok(suggestions)
    }

    /**
     * Get all party types
     */
    @GetMapping("/party-types")
    fun getAllPartyTypes(): ResponseEntity<List<PartyTypeTaxonomy>> {
        return ResponseEntity.ok(partyTypeService.getAllPartyTypes())
    }

    /**
     * Estimate budget for a party configuration
     */
    @PostMapping("/estimate-budget")
    fun estimateBudget(@Valid @RequestBody request: BudgetEstimateRequest): ResponseEntity<BudgetEstimateResponse> {
        val estimatedCost = budgetEstimationService.estimatePartyCost(
            partyTypes = request.partyTypes,
            guestCount = request.guestCount,
            priceLevel = request.priceLevel
        )

        val perPersonCost = budgetEstimationService.estimateCostPerPerson(
            partyTypes = request.partyTypes,
            priceLevel = request.priceLevel
        )

        return ResponseEntity.ok(BudgetEstimateResponse(
            estimatedTotal = estimatedCost,
            estimatedPerPerson = perPersonCost,
            budgetCategory = budgetEstimationService.getBudgetRangeDescription(estimatedCost)
        ))
    }

    /**
     * Get party details for a specific venue type
     */
    @GetMapping("/party-details")
    fun getPartyDetails(
        @RequestParam partyTypes: List<String>,
        @RequestParam guestCount: Int,
        @RequestParam(required = false) priceLevel: Int?
    ): ResponseEntity<PartyDetailsResponse> {
        return ResponseEntity.ok(PartyDetailsResponse(
            includedItems = partyDetailsService.getIncludedItems(partyTypes, priceLevel),
            notIncluded = partyDetailsService.getNotIncludedItems(partyTypes, priceLevel),
            suggestedAddOns = partyDetailsService.getSuggestedAddOns(partyTypes, guestCount),
            whatToBring = partyDetailsService.getWhatToBring(partyTypes, priceLevel),
            typicalDuration = partyDetailsService.getTypicalDuration(partyTypes),
            ageAppropriatenessDescription = partyDetailsService.getAgeAppropriatenessDescription(partyTypes)
        ))
    }

    /**
     * Map Google Place to EnhancedVenue
     */
    private fun mapToEnhancedVenue(
        place: Place,
        searchLocation: Location,
        request: PartySearchRequest
    ): EnhancedVenue {
        val placeLocation = place.location ?: throw IllegalArgumentException("Place missing location")
        val distanceInMiles = calculateDistance(
            searchLocation.lat,
            searchLocation.lng,
            placeLocation.latitude,
            placeLocation.longitude
        )

        val placeTypes = place.types ?: emptyList()
        val priceLevel = parsePriceLevel(place.priceLevel)

        // Calculate capacity based on venue type
        val (minCapacity, maxCapacity) = estimateCapacityRange(placeTypes)

        // Calculate match score
        val matchResult = matchScoreService.calculateMatchScore(
            request = request,
            venuePlaceTypes = placeTypes,
            venueRating = place.rating,
            venueUserRatingsTotal = place.userRatingCount,
            venuePriceLevel = priceLevel,
            venueDistanceMiles = distanceInMiles,
            venueMinCapacity = minCapacity,
            venueMaxCapacity = maxCapacity
        )

        // Calculate budget estimate
        val estimatedTotal = budgetEstimationService.estimatePartyCost(
            partyTypes = request.partyTypes,
            guestCount = request.guestCount,
            priceLevel = priceLevel
        )

        val estimatedPerPerson = budgetEstimationService.estimateCostPerPerson(
            partyTypes = request.partyTypes,
            priceLevel = priceLevel
        )

        // Get party details
        val includedItems = partyDetailsService.getIncludedItems(request.partyTypes, priceLevel)
        val notIncluded = partyDetailsService.getNotIncludedItems(request.partyTypes, priceLevel)
        val suggestedAddOns = partyDetailsService.getSuggestedAddOns(request.partyTypes, request.guestCount)

        // Determine setting
        val setting = inferSetting(placeTypes)

        // Get age appropriateness
        val popularForAges = partyDetailsService.getAgeAppropriatenessDescription(request.partyTypes)

        // Get typical duration
        val typicalDuration = partyDetailsService.getTypicalDuration(request.partyTypes)

        // Extract photo URLs (if available)
        val photos = place.photos?.take(5)?.map { photo ->
            // Photos would need additional API call, for now return placeholder
            "https://places.googleapis.com/v1/${photo.name}/media?key=API_KEY&maxWidthPx=400"
        } ?: emptyList()

        return EnhancedVenue(
            id = place.id ?: "unknown-${System.currentTimeMillis()}",
            name = place.displayName?.text ?: "Unknown Venue",
            address = place.formattedAddress ?: "Address not available",
            rating = place.rating ?: 0.0,
            userRatingsTotal = place.userRatingCount ?: 0,
            phoneNumber = place.internationalPhoneNumber,
            website = place.websiteUri,
            distanceInMiles = "%.1f".format(distanceInMiles).toDouble(),
            priceLevel = priceLevel,
            placeTypes = placeTypes,
            photos = photos,

            matchScore = matchResult.totalScore,
            matchReasons = matchResult.reasons,

            estimatedTotal = estimatedTotal,
            estimatedPricePerPerson = estimatedPerPerson,
            includedItems = includedItems,
            notIncluded = notIncluded,
            suggestedAddOns = suggestedAddOns,

            popularForAges = popularForAges,
            typicalPartyDuration = typicalDuration,

            minCapacity = minCapacity,
            maxCapacity = maxCapacity,

            setting = setting,

            isOpenOnDate = null, // Would need additional API call
            openingHours = null
        )
    }

    /**
     * Filter venue by indoor/outdoor preference
     */
    private fun filterBySetting(venue: EnhancedVenue, requestedSetting: String): Boolean {
        return when (requestedSetting) {
            "any" -> true
            "indoor" -> venue.setting == "indoor" || venue.setting == "both"
            "outdoor" -> venue.setting == "outdoor" || venue.setting == "both"
            else -> true
        }
    }

    /**
     * Infer indoor/outdoor setting from place types
     */
    private fun inferSetting(types: List<String>): String {
        val lowercaseTypes = types.map { it.lowercase() }
        return when {
            lowercaseTypes.any { it.contains("park") || it.contains("zoo") || it.contains("garden") } -> "outdoor"
            lowercaseTypes.any { it.contains("pool") || it.contains("water") } -> "both"
            else -> "indoor"
        }
    }

    /**
     * Estimate capacity range from place types
     */
    private fun estimateCapacityRange(types: List<String>): Pair<Int, Int> {
        return when {
            types.any { it.contains("banquet") || it.contains("event_venue") } -> Pair(20, 200)
            types.any { it.contains("restaurant") } -> Pair(10, 80)
            types.any { it.contains("amusement_park") } -> Pair(10, 100)
            types.any { it.contains("bowling") } -> Pair(8, 50)
            types.any { it.contains("amusement_center") } -> Pair(8, 60)
            types.any { it.contains("movie_theater") } -> Pair(10, 40)
            types.any { it.contains("park") } -> Pair(5, 100)
            types.any { it.contains("gym") } -> Pair(10, 40)
            else -> Pair(10, 40)
        }
    }

    /**
     * Parse price level from Google Places API string
     */
    private fun parsePriceLevel(priceLevel: String?): Int {
        return when (priceLevel) {
            "PRICE_LEVEL_FREE" -> 0
            "PRICE_LEVEL_INEXPENSIVE" -> 1
            "PRICE_LEVEL_MODERATE" -> 2
            "PRICE_LEVEL_EXPENSIVE" -> 3
            "PRICE_LEVEL_VERY_EXPENSIVE" -> 4
            else -> 2
        }
    }

    /**
     * Calculate distance between two coordinates (Haversine formula)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMiles = 3959.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMiles * c
    }
}

/**
 * Request model for budget estimation
 */
data class BudgetEstimateRequest(
    @field:NotEmpty(message = "At least one party type is required")
    val partyTypes: List<String>,
    @field:Min(value = 1, message = "Guest count must be at least 1")
    val guestCount: Int,
    val priceLevel: Int? = null
)

/**
 * Response model for budget estimation
 */
data class BudgetEstimateResponse(
    val estimatedTotal: Int,
    val estimatedPerPerson: Int,
    val budgetCategory: String
)

/**
 * Response model for party details
 */
data class PartyDetailsResponse(
    val includedItems: List<String>,
    val notIncluded: List<String>,
    val suggestedAddOns: List<AddOn>,
    val whatToBring: List<String>,
    val typicalDuration: String,
    val ageAppropriatenessDescription: String
)
