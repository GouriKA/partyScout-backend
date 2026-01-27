package com.partyscout.service

import com.partyscout.config.GooglePlacesConfig
import com.partyscout.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Service
class GooglePlacesService(
    private val googlePlacesWebClient: WebClient,
    private val googlePlacesConfig: GooglePlacesConfig
) {
    private val logger = LoggerFactory.getLogger(GooglePlacesService::class.java)

    /**
     * Convert ZIP code to latitude/longitude using Google Geocoding API
     */
    fun geocodeZipCode(zipCode: String): Mono<Location> {
        logger.info("Geocoding ZIP code: $zipCode")
        logger.info("Geocoding ZIP code: $googlePlacesConfig.apiKey")

        return googlePlacesWebClient
            .get()
            .uri("https://maps.googleapis.com/maps/api/geocode/json?address={zipCode}&key={apiKey}",
                zipCode, googlePlacesConfig.apiKey)
            .retrieve()
            .bodyToMono<GeocodingResponse>()
            .doOnError { error ->
                logger.error("Geocoding API error for ZIP code $zipCode", error)
            }
            .map { response ->
                if (response.status == "OK" && response.results.isNotEmpty()) {
                    response.results[0].geometry.location
                } else {
                    val errorMsg = if (response.error_message.isNotEmpty()) {
                        "Geocoding failed: ${response.status} - ${response.error_message}"
                    } else {
                        "Geocoding failed: ${response.status}"
                    }
                    throw GooglePlacesException(errorMsg)
                }
            }
    }

    /**
     * Search for nearby places using NEW Google Places API (v1)
     */
    fun searchNearbyPlaces(
        location: Location,
        keywords: List<String>,
        radiusMeters: Int = 5000
    ): Mono<SearchNearbyResponse> {
        logger.info("Searching nearby places at (${location.lat}, ${location.lng}) with types: $keywords")

        // Convert keywords to Google Places API types
        val includedTypes = mapKeywordsToTypes(keywords)

        val request = SearchNearbyRequest(
            includedTypes = includedTypes,
            maxResultCount = 20,
            locationRestriction = LocationRestriction(
                circle = Circle(
                    center = LatLng(
                        latitude = location.lat,
                        longitude = location.lng
                    ),
                    radius = radiusMeters.toDouble()
                )
            )
        )

        return googlePlacesWebClient
            .post()
            .uri("https://places.googleapis.com/v1/places:searchNearby")
            .header("X-Goog-Api-Key", googlePlacesConfig.apiKey)
            .header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.userRatingCount,places.priceLevel,places.types,places.googleMapsUri,places.websiteUri,places.internationalPhoneNumber")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<SearchNearbyResponse>()
            .doOnError { error ->
                logger.error("Nearby Search API error", error)
            }
            .map { response ->
                logger.info("Found ${response.places?.size ?: 0} places")
                response
            }
    }

    /**
     * Map search keywords to Google Places API types
     */
    private fun mapKeywordsToTypes(keywords: List<String>): List<String> {
        return keywords.flatMap { keyword ->
            when (keyword) {
                "playground" -> listOf("park", "playground")
                "amusement_park" -> listOf("amusement_park", "amusement_center")
                "bowling_alley" -> listOf("bowling_alley")
                "arcade" -> listOf("amusement_center")
                "movie_theater" -> listOf("movie_theater")
                "sports_complex" -> listOf("gym", "stadium", "sports_complex")
                "restaurant" -> listOf("restaurant")
                "bar" -> listOf("bar", "night_club")
                "banquet_hall" -> listOf("banquet_hall", "event_venue")
                else -> listOf(keyword)
            }
        }.distinct()
    }

    /**
     * Get detailed place information (optional - for enrichment)
     */
    fun getPlaceDetails(placeId: String): Mono<PlaceDetails> {
        logger.info("Fetching place details for: $placeId")

        return googlePlacesWebClient
            .get()
            .uri("https://maps.googleapis.com/maps/api/place/details/json?place_id={placeId}&key={apiKey}",
                placeId, googlePlacesConfig.apiKey)
            .retrieve()
            .bodyToMono<PlaceDetailsResponse>()
            .doOnError { error ->
                logger.error("Place Details API error for $placeId", error)
            }
            .map { response ->
                if (response.status == "OK") {
                    response.result
                } else {
                    throw GooglePlacesException("Place details failed: ${response.status}")
                }
            }
    }
}

class GooglePlacesException(message: String) : RuntimeException(message)
