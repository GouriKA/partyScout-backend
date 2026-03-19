package com.partyscout.venue.service

import com.partyscout.venue.config.GooglePlacesConfig
import com.partyscout.venue.dto.*
import com.partyscout.shared.logging.LogSanitizer
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
        logger.info("Geocoding ZIP code: {}", LogSanitizer.maskZipCode(zipCode))

        return googlePlacesWebClient
            .get()
            .uri("https://maps.googleapis.com/maps/api/geocode/json?address={zipCode}&key={apiKey}",
                zipCode, googlePlacesConfig.apiKey)
            .retrieve()
            .bodyToMono<GeocodingResponse>()
            .doOnError { error ->
                logger.error("Geocoding API error for ZIP code {}: {}", LogSanitizer.maskZipCode(zipCode), error.message)
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
     * Return up to 5 US city suggestions for a partial input string.
     * Uses the New Places API (v1) autocomplete endpoint.
     */
    fun autocompleteCity(input: String): Mono<List<String>> {
        if (input.isBlank()) return Mono.just(emptyList())

        val requestBody = NewAutocompleteRequest(input = input)

        return googlePlacesWebClient
            .post()
            .uri("https://places.googleapis.com/v1/places:autocomplete")
            .header("X-Goog-Api-Key", googlePlacesConfig.apiKey)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono<NewAutocompleteResponse>()
            .map { response ->
                (response.suggestions ?: emptyList()).take(5).mapNotNull { suggestion ->
                    val text = suggestion.placePrediction?.text?.text ?: return@mapNotNull null
                    // text is "Austin, TX, USA" — strip trailing ", USA"
                    text.removeSuffix(", USA")
                }
            }
            .onErrorResume { err ->
                logger.error("Places autocomplete error for input '{}': {}", input, err.message)
                Mono.just(emptyList())
            }
    }

    /**
     * Convert city name (e.g. "Austin, TX") to latitude/longitude using Google Geocoding API
     */
    fun geocodeCity(city: String): Mono<Location> {
        logger.info("Geocoding city: {}", city)

        return googlePlacesWebClient
            .get()
            .uri("https://maps.googleapis.com/maps/api/geocode/json?address={city}&key={apiKey}",
                city, googlePlacesConfig.apiKey)
            .retrieve()
            .bodyToMono<GeocodingResponse>()
            .doOnError { error ->
                logger.error("Geocoding API error for city {}: {}", city, error.message)
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
        logger.info("Searching nearby places with types: {}, radius: {}m", keywords, radiusMeters)

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
            .header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.userRatingCount,places.priceLevel,places.types,places.googleMapsUri,places.websiteUri,places.internationalPhoneNumber,places.photos,places.currentOpeningHours")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<SearchNearbyResponse>()
            .doOnError { error ->
                logger.error("Nearby Search API error: {}", error.message)
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
                "sports_complex" -> listOf("sports_complex")
                "restaurant" -> listOf("restaurant")
                "bar" -> listOf("bar", "night_club")
                "banquet_hall" -> listOf("banquet_hall", "event_venue")
                else -> listOf(keyword)
            }
        }.distinct()
    }

    /**
     * Search for places by free-text query using Places API v1 searchText.
     * Uses locationBias (soft preference) so text relevance is not sacrificed for proximity.
     */
    fun searchText(
        query: String,
        location: Location,
        radiusMeters: Int = 8000
    ): Mono<SearchNearbyResponse> {
        logger.debug("Text search: query='{}', radius={}m", query, radiusMeters)

        val request = SearchTextRequest(
            textQuery = query,
            maxResultCount = 20,
            locationBias = LocationBias(
                circle = Circle(
                    center = LatLng(latitude = location.lat, longitude = location.lng),
                    radius = radiusMeters.toDouble()
                )
            )
        )

        return googlePlacesWebClient
            .post()
            .uri("https://places.googleapis.com/v1/places:searchText")
            .header("X-Goog-Api-Key", googlePlacesConfig.apiKey)
            .header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.userRatingCount,places.priceLevel,places.types,places.googleMapsUri,places.websiteUri,places.internationalPhoneNumber,places.photos,places.currentOpeningHours")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<SearchNearbyResponse>()
            .doOnError { error -> logger.warn("Text search failed for '{}': {}", query, error.message) }
            .onErrorReturn(SearchNearbyResponse(emptyList()))
    }

    /**
     * Get detailed place information (optional - for enrichment)
     */
    fun getPlaceDetails(placeId: String): Mono<PlaceDetails> {
        logger.info("Fetching place details for placeId: {}", placeId)

        return googlePlacesWebClient
            .get()
            .uri("https://maps.googleapis.com/maps/api/place/details/json?place_id={placeId}&key={apiKey}",
                placeId, googlePlacesConfig.apiKey)
            .retrieve()
            .bodyToMono<PlaceDetailsResponse>()
            .doOnError { error ->
                logger.error("Place Details API error for placeId {}: {}", placeId, error.message)
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
