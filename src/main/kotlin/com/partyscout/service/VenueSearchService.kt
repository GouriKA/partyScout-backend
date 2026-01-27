package com.partyscout.service

import com.partyscout.dto.Location
import com.partyscout.dto.Place
import com.partyscout.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class VenueSearchService(
    private val googlePlacesService: GooglePlacesService
) {
    private val logger = LoggerFactory.getLogger(VenueSearchService::class.java)

    /**
     * Search for birthday venues based on age and location
     */
    fun searchVenues(age: Int, zipCode: String): Mono<List<BirthdayVenueOption>> {
        logger.info("Searching venues for age: $age, ZIP: $zipCode")

        val keywords = getKeywordsForAge(age)

        return googlePlacesService.geocodeZipCode(zipCode)
            .flatMap { location ->
                googlePlacesService.searchNearbyPlaces(location, keywords)
                    .map { response ->
                        response.places?.mapNotNull { place ->
                            try {
                                mapToVenueOption(place, location, age)
                            } catch (e: Exception) {
                                logger.warn("Failed to map place: ${e.message}")
                                null
                            }
                        } ?: emptyList()
                    }
            }
            .doOnError { error ->
                logger.error("Venue search failed for age $age, ZIP $zipCode", error)
            }
            .onErrorReturn(emptyList()) // Return empty list on failure
    }

    /**
     * Search for party venue options (for PartyOptionsController)
     */
    fun searchPartyOptions(age: Int, zipCode: String): Mono<List<VenueOption>> {
        logger.info("Searching party options for age: $age, ZIP: $zipCode")

        val keywords = getKeywordsForAge(age)

        return googlePlacesService.geocodeZipCode(zipCode)
            .flatMap { location ->
                googlePlacesService.searchNearbyPlaces(location, keywords)
                    .map { response ->
                        response.places?.mapNotNull { place ->
                            try {
                                mapToSimpleVenueOption(place, location)
                            } catch (e: Exception) {
                                logger.warn("Failed to map place: ${e.message}")
                                null
                            }
                        } ?: emptyList()
                    }
            }
            .doOnError { error ->
                logger.error("Party options search failed for age $age, ZIP $zipCode", error)
            }
            .onErrorReturn(emptyList())
    }

    /**
     * Determine search keywords based on age
     */
    private fun getKeywordsForAge(age: Int): List<String> {
        return when {
            age <= 12 -> listOf("playground", "amusement_park", "bowling_alley")
            age <= 18 -> listOf("arcade", "movie_theater", "sports_complex")
            else -> listOf("restaurant", "bar", "banquet_hall")
        }
    }

    /**
     * Map Google Place (new API) to BirthdayVenueOption (detailed model)
     */
    private fun mapToVenueOption(
        place: Place,
        searchLocation: Location,
        age: Int
    ): BirthdayVenueOption {
        val placeLocation = place.location ?: throw IllegalArgumentException("Place missing location")
        val distanceInMiles = calculateDistance(
            searchLocation.lat,
            searchLocation.lng,
            placeLocation.latitude,
            placeLocation.longitude
        )

        val types = place.types ?: emptyList()
        val priceLevel = parsePriceLevel(place.priceLevel)

        return BirthdayVenueOption(
            name = place.displayName?.text ?: "Unknown Venue",
            address = place.formattedAddress ?: "Address not available",
            rating = place.rating ?: 0.0,
            kidFriendlyFeatures = inferKidFriendlyFeatures(types, age),
            estimatedCapacity = estimateCapacity(types),
            description = generateDescription(place.displayName?.text, place.rating, types),
            priceRange = formatPriceRange(priceLevel),
            phoneNumber = place.internationalPhoneNumber,
            website = place.websiteUri,
            distanceInMiles = distanceInMiles
        )
    }

    /**
     * Map Google Place (new API) to VenueOption (simpler model)
     */
    private fun mapToSimpleVenueOption(
        place: Place,
        searchLocation: Location
    ): VenueOption {
        val placeLocation = place.location ?: throw IllegalArgumentException("Place missing location")
        val distanceInMiles = calculateDistance(
            searchLocation.lat,
            searchLocation.lng,
            placeLocation.latitude,
            placeLocation.longitude
        )

        val types = place.types ?: emptyList()
        val priceLevel = parsePriceLevel(place.priceLevel)

        return VenueOption(
            id = place.id ?: "unknown",
            name = place.displayName?.text ?: "Unknown Venue",
            type = inferVenueType(types),
            address = place.formattedAddress ?: "Address not available",
            distance = distanceInMiles,
            rating = place.rating ?: 0.0,
            priceLevel = priceLevel,
            amenities = inferAmenities(types),
            availableCapacity = estimateCapacity(types),
            estimatedCost = estimateCost(priceLevel),
            description = generateDescription(place.displayName?.text, place.rating, types)
        )
    }

    /**
     * Parse price level from string to int (new API returns PRICE_LEVEL_* strings)
     */
    private fun parsePriceLevel(priceLevel: String?): Int {
        return when (priceLevel) {
            "PRICE_LEVEL_FREE" -> 0
            "PRICE_LEVEL_INEXPENSIVE" -> 1
            "PRICE_LEVEL_MODERATE" -> 2
            "PRICE_LEVEL_EXPENSIVE" -> 3
            "PRICE_LEVEL_VERY_EXPENSIVE" -> 4
            else -> 2 // Default to moderate
        }
    }

    /**
     * Infer kid-friendly features from place types and age
     */
    private fun inferKidFriendlyFeatures(types: List<String>, age: Int): KidFriendlyFeatures {
        val lowercaseTypes = types.map { it.lowercase() }

        return when {
            age <= 12 -> KidFriendlyFeatures(
                isKidFriendly = true,
                ageRange = "3-12",
                hasPlayArea = lowercaseTypes.any { it.contains("playground") || it.contains("park") },
                hasKidsMenu = lowercaseTypes.any { it.contains("restaurant") || it.contains("cafe") },
                hasHighChairs = lowercaseTypes.any { it.contains("restaurant") },
                hasChangingStation = lowercaseTypes.any { it.contains("shopping") || it.contains("mall") },
                entertainmentOptions = inferEntertainmentOptions(lowercaseTypes, age),
                safetyFeatures = listOf("Supervised area", "Safe environment"),
                specialAccommodations = listOf("Wheelchair accessible")
            )
            age <= 18 -> KidFriendlyFeatures(
                isKidFriendly = true,
                ageRange = "13-18",
                entertainmentOptions = inferEntertainmentOptions(lowercaseTypes, age),
                specialAccommodations = listOf("Group-friendly", "Teen-appropriate")
            )
            else -> KidFriendlyFeatures(
                isKidFriendly = false,
                ageRange = "18+",
                entertainmentOptions = inferEntertainmentOptions(lowercaseTypes, age),
                specialAccommodations = listOf("Full bar", "Catering available")
            )
        }
    }

    /**
     * Infer entertainment options from place types
     */
    private fun inferEntertainmentOptions(types: List<String>, age: Int): List<String> {
        val options = mutableListOf<String>()

        types.forEach { type ->
            when {
                type.contains("amusement_park") -> options.add("Rides and attractions")
                type.contains("bowling") -> options.add("Bowling lanes")
                type.contains("arcade") -> options.add("Video games")
                type.contains("movie") -> options.add("Movie screenings")
                type.contains("sports") -> options.add("Sports activities")
                type.contains("restaurant") && age > 18 -> options.add("Live music")
                type.contains("bar") -> options.add("Bar service")
            }
        }

        return options.ifEmpty { listOf("Various entertainment options") }
    }

    /**
     * Infer venue type from Google place types
     */
    private fun inferVenueType(types: List<String>): String {
        return when {
            types.any { it.contains("amusement") } -> "Amusement Park"
            types.any { it.contains("bowling") } -> "Bowling Alley"
            types.any { it.contains("arcade") } -> "Arcade"
            types.any { it.contains("movie") } -> "Movie Theater"
            types.any { it.contains("restaurant") } -> "Restaurant"
            types.any { it.contains("bar") } -> "Bar & Lounge"
            types.any { it.contains("banquet") } -> "Banquet Hall"
            types.any { it.contains("park") } -> "Park"
            else -> "Entertainment Venue"
        }
    }

    /**
     * Infer amenities from place types
     */
    private fun inferAmenities(types: List<String>): List<String> {
        val amenities = mutableListOf<String>()

        types.forEach { type ->
            when {
                type.contains("parking") -> amenities.add("Parking")
                type.contains("restaurant") -> amenities.add("Dining")
                type.contains("bar") -> amenities.add("Bar")
            }
        }

        return amenities.ifEmpty { listOf("Standard amenities") }
    }

    /**
     * Estimate capacity based on venue type
     */
    private fun estimateCapacity(types: List<String>): Int {
        return when {
            types.any { it.contains("banquet") || it.contains("hall") } -> 200
            types.any { it.contains("restaurant") } -> 80
            types.any { it.contains("amusement") || it.contains("park") } -> 100
            types.any { it.contains("bowling") } -> 50
            types.any { it.contains("arcade") || it.contains("movie") } -> 40
            else -> 30
        }
    }

    /**
     * Format price range from Google price level (0-4)
     */
    private fun formatPriceRange(priceLevel: Int?): String {
        return when (priceLevel) {
            1 -> "$100-$300"
            2 -> "$300-$600"
            3 -> "$600-$1200"
            4 -> "$1200-$2500"
            else -> "$200-$500"
        }
    }

    /**
     * Estimate cost from price level
     */
    private fun estimateCost(priceLevel: Int?): Double {
        return when (priceLevel) {
            1 -> 200.0
            2 -> 450.0
            3 -> 900.0
            4 -> 1800.0
            else -> 400.0
        }
    }

    /**
     * Generate description from place data (new API)
     */
    private fun generateDescription(name: String?, rating: Double?, types: List<String>): String {
        val venueType = inferVenueType(types)
        val ratingText = rating?.let { "Rated ${String.format("%.1f", it)} stars" } ?: "Popular venue"
        return "$venueType - $ratingText with great amenities for celebrations"
    }

    /**
     * Calculate distance between two coordinates (Haversine formula)
     * Returns distance in miles
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
