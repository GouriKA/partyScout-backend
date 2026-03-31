package com.partyscout.search.controller

import com.partyscout.venue.dto.Location
import com.partyscout.venue.dto.Place
import com.partyscout.shared.logging.LogSanitizer
import com.partyscout.party.model.*
import com.partyscout.party.service.*
import com.partyscout.venue.service.GooglePlacesService
import com.partyscout.persistence.service.SearchPersistenceService
import com.partyscout.persistence.service.VenueEnrichmentService
import com.partyscout.persona.PersonaService
import com.partyscout.llm.LlmFilterService
import com.partyscout.shared.event.BudgetEstimatedEvent
import com.partyscout.shared.event.DomainEventPublisher
import com.partyscout.shared.event.VenueSearchedEvent
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@RestController
@RequestMapping("/api/v2/party-wizard")
class PartySearchController(
    private val googlePlacesService: GooglePlacesService,
    private val googlePlacesConfig: com.partyscout.venue.config.GooglePlacesConfig,
    private val partyTypeService: PartyTypeService,
    private val matchScoreService: MatchScoreService,
    private val budgetEstimationService: BudgetEstimationService,
    private val partyDetailsService: PartyDetailsService,
    private val domainEventPublisher: DomainEventPublisher,
    private val searchPersistenceService: SearchPersistenceService,
    private val personaService: PersonaService,
    private val llmFilterService: LlmFilterService,
    private val venueEnrichmentService: VenueEnrichmentService,
) {
    private val logger = LoggerFactory.getLogger(PartySearchController::class.java)

    /**
     * Main party search endpoint for the wizard
     */
    @PostMapping("/search")
    fun searchPartyVenues(@Valid @RequestBody request: PartySearchRequest): ResponseEntity<PartySearchResponse> {
        // ── 1. Derive persona ────────────────────────────────────────────────────
        val persona = personaService.getPersona(request.age)
        val searchQueries = personaService.getSearchQueries(request.age)
        logger.info("Party wizard search: age={}, persona={}, queries={}, city={}",
            request.age, persona.label, searchQueries.size, request.city)

        // ── 2. Geocode city ──────────────────────────────────────────────────────
        val radiusMeters = (request.maxDistanceMiles * 1609.34).toInt()
        val location = googlePlacesService.geocodeCity(request.city).block()
            ?: return ResponseEntity.badRequest().build()

        // ── 3. Run search queries in parallel (max 5 concurrent) ───────────────
        // Always add outdoor-specific queries so filtering by outdoor is never empty.
        val outdoorQueries = listOf(
            "outdoor party venue",
            "park birthday party",
            "farm party venue",
            "outdoor event space",
            "garden party venue",
            "picnic area birthday party",
        )
        val queries = if (!request.textQuery.isNullOrBlank()) {
            listOf(request.textQuery) + outdoorQueries
        } else {
            searchQueries + outdoorQueries
        }
        val allPlaces: List<Place> = Flux.fromIterable(queries)
            .flatMap({ query ->
                googlePlacesService.searchText(query, location, radiusMeters)
                    .map { it.places ?: emptyList() }
                    .onErrorResume { err ->
                        logger.warn("Text search failed for '{}': {}", query, err.message)
                        Mono.just(emptyList())
                    }
            }, 5)
            .flatMapIterable { it }
            .collectList()
            .block() ?: emptyList()

        // ── 4. Deduplicate by place ID ───────────────────────────────────────────
        val uniquePlaces = allPlaces.distinctBy { it.id }
        logger.info("Text search: {} raw → {} unique places", allPlaces.size, uniquePlaces.size)

        // ── 5. Enrichment (batch lookup, additive — missing entries are fine) ────
        val enrichmentMap = try {
            venueEnrichmentService.batchLookup(uniquePlaces.mapNotNull { it.id })
        } catch (e: Exception) {
            logger.warn("Enrichment lookup failed: {}", e.message)
            emptyMap()
        }

        // ── 6. LLM filter (max 20 venues, graceful fallback) ─────────────────────
        val (filteredPlaces, llmFilterApplied) = llmFilterService.filter(
            places = uniquePlaces.take(20),
            age = request.age,
            persona = persona.label,
            enrichmentMap = enrichmentMap,
        )

        // ── 7. Map, score, apply setting/distance filters, sort ──────────────────
        val venues = filteredPlaces
            .filter { isNotExcludedVenueType(it.types ?: emptyList()) }
            .mapNotNull { place ->
                try {
                    mapToEnhancedVenue(place, location, request)
                } catch (e: Exception) {
                    logger.warn("Failed to map place '{}': {}", place.displayName?.text, e.message)
                    null
                }
            }
            .filter { filterBySetting(it, request.setting) }
            .filter { it.distanceInMiles <= request.maxDistanceMiles }
            .sortedByDescending { it.matchScore }

        val response = PartySearchResponse(
            venues = venues,
            searchCriteria = PartySearchCriteria(
                age = request.age,
                partyTypes = request.partyTypes,
                guestCount = request.guestCount,
                budgetMin = request.budgetMin,
                budgetMax = request.budgetMax,
                city = request.city,
                setting = request.setting,
                maxDistanceMiles = request.maxDistanceMiles,
                date = request.date
            ),
            partyTypeSuggestions = partyTypeService.getPartyTypesForAge(request.age),
            persona = persona.label,
            llmFilterApplied = llmFilterApplied,
        )

        try {
            searchPersistenceService.recordSearch(MDC.get("correlationId"), request, venues.size)
        } catch (e: Exception) {
            logger.warn("Failed to persist search: {}", e.message)
        }

        try {
            domainEventPublisher.publish(VenueSearchedEvent(
                correlationId = MDC.get("correlationId"),
                city = request.city,
                age = request.age,
                partyTypes = request.partyTypes,
                guestCount = request.guestCount,
                venueCount = venues.size
            ))
        } catch (e: Exception) {
            logger.warn("Failed to publish VenueSearchedEvent: {}", e.message)
        }

        logger.info("Returning {} venue options", venues.size)
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

        val response = BudgetEstimateResponse(
            estimatedTotal = estimatedCost,
            estimatedPerPerson = perPersonCost,
            budgetCategory = budgetEstimationService.getBudgetRangeDescription(estimatedCost)
        )

        try {
            domainEventPublisher.publish(BudgetEstimatedEvent(
                correlationId = MDC.get("correlationId"),
                partyTypes = request.partyTypes,
                guestCount = request.guestCount,
                priceLevel = request.priceLevel,
                estimatedTotal = estimatedCost,
                estimatedPerPerson = perPersonCost
            ))
        } catch (e: Exception) {
            logger.warn("Failed to publish BudgetEstimatedEvent: {}", e.message)
        }

        return ResponseEntity.ok(response)
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
        val setting = inferSetting(placeTypes, place.displayName?.text ?: "")

        // Get age appropriateness
        val popularForAges = partyDetailsService.getAgeAppropriatenessDescription(request.partyTypes)

        // Get typical duration
        val typicalDuration = partyDetailsService.getTypicalDuration(request.partyTypes)

        // Extract photo URLs (if available)
        val photos = place.photos?.take(5)?.map { photo ->
            // Photos would need additional API call, for now return placeholder
            "https://places.googleapis.com/v1/${photo.name}/media?key=${googlePlacesConfig.apiKey}&maxWidthPx=400"
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
     * Exclude venue types that are not suitable for birthday parties
     */
    private val excludedPlaceTypes = setOf(
        "grocery_store", "supermarket", "convenience_store", "gas_station",
        "pharmacy", "drugstore", "hardware_store", "home_goods_store",
        "furniture_store", "clothing_store", "department_store", "shopping_mall",
        "car_dealer", "car_repair", "car_wash", "laundry", "storage"
    )

    private fun isNotExcludedVenueType(types: List<String>): Boolean {
        return types.none { it in excludedPlaceTypes }
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
     * Infer indoor/outdoor setting from place types and venue name.
     * Google Places types are often generic (establishment, point_of_interest),
     * so the venue name is a more reliable signal for outdoor venues.
     */
    private fun inferSetting(types: List<String>, name: String = ""): String {
        val lowercaseTypes = types.map { it.lowercase() }
        val lowercaseName = name.lowercase()
        // Exact Google Places types that are genuinely outdoor
        val outdoorTypes = setOf("park", "zoo", "botanical_garden", "campground", "natural_feature", "rv_park", "national_park", "state_park")
        // Name keywords that reliably indicate outdoor venues (avoid generic words like "recreation")
        val outdoorNameKeywords = listOf("outdoor", "farm", "garden", "pavilion", "ranch", "beach", "lake", "nature center", "forest", "trail", "reserve", "campground", "picnic area", "botanical")
        // "Park" in the name is outdoor only when standalone or at word boundary, not part of "parking"
        val nameHasPark = Regex("\\bpark\\b").containsMatchIn(lowercaseName) && !lowercaseName.contains("parking")
        val nameHasField = Regex("\\bfield\\b").containsMatchIn(lowercaseName)
        val nameHasYard = Regex("\\byard\\b").containsMatchIn(lowercaseName)
        val bothNameKeywords = listOf("pool", "aquatic", "splash pad", "water park")
        return when {
            lowercaseTypes.any { it in outdoorTypes } -> "outdoor"
            outdoorNameKeywords.any { lowercaseName.contains(it) } -> "outdoor"
            nameHasPark || nameHasField || nameHasYard -> "outdoor"
            lowercaseTypes.any { it.contains("swimming_pool") } -> "both"
            bothNameKeywords.any { lowercaseName.contains(it) } -> "both"
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
