package com.partyscout.dto

import com.fasterxml.jackson.annotation.JsonProperty

// Geocoding API Response
data class GeocodingResponse(
    val results: List<GeocodingResult>,
    val status: String,
    val error_message: String = ""
)

data class GeocodingResult(
    @JsonProperty("formatted_address")
    val formattedAddress: String,
    val geometry: Geometry,
    @JsonProperty("place_id")
    val placeId: String
)

data class Geometry(
    val location: Location,
    @JsonProperty("location_type")
    val locationType: String
)

data class Location(
    val lat: Double,
    val lng: Double
)

// Nearby Search API Response
data class NearbySearchResponse(
    val results: List<PlaceResult>,
    val status: String,
    @JsonProperty("next_page_token")
    val nextPageToken: String? = null
)

data class PlaceResult(
    @JsonProperty("place_id")
    val placeId: String,
    val name: String,
    val vicinity: String,  // Address
    val geometry: Geometry,
    val rating: Double? = null,
    @JsonProperty("user_ratings_total")
    val userRatingsTotal: Int? = null,
    @JsonProperty("price_level")
    val priceLevel: Int? = null,
    val types: List<String> = emptyList(),
    @JsonProperty("opening_hours")
    val openingHours: OpeningHours? = null,
    val photos: List<Photo>? = null
)

data class OpeningHours(
    @JsonProperty("open_now")
    val openNow: Boolean? = null
)

data class Photo(
    @JsonProperty("photo_reference")
    val photoReference: String,
    val height: Int,
    val width: Int
)

// Place Details API Response (for enrichment if needed)
data class PlaceDetailsResponse(
    val result: PlaceDetails,
    val status: String
)

data class PlaceDetails(
    @JsonProperty("place_id")
    val placeId: String,
    val name: String,
    @JsonProperty("formatted_address")
    val formattedAddress: String,
    @JsonProperty("formatted_phone_number")
    val formattedPhoneNumber: String? = null,
    val website: String? = null,
    val rating: Double? = null,
    @JsonProperty("user_ratings_total")
    val userRatingsTotal: Int? = null,
    @JsonProperty("price_level")
    val priceLevel: Int? = null,
    val reviews: List<Review>? = null
)

data class Review(
    @JsonProperty("author_name")
    val authorName: String,
    val rating: Int,
    val text: String,
    val time: Long
)

// ========== NEW GOOGLE PLACES API (v1) ==========

// Search Nearby Request (New API)
data class SearchNearbyRequest(
    val includedTypes: List<String>,
    val maxResultCount: Int = 20,
    val locationRestriction: LocationRestriction
)

data class LocationRestriction(
    val circle: Circle
)

data class Circle(
    val center: LatLng,
    val radius: Double
)

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

// Search Nearby Response (New API)
data class SearchNearbyResponse(
    val places: List<Place>? = emptyList()
)

data class Place(
    val id: String? = null,
    val displayName: DisplayName? = null,
    val formattedAddress: String? = null,
    val location: LatLng? = null,
    val rating: Double? = null,
    val userRatingCount: Int? = null,
    val priceLevel: String? = null,
    val types: List<String>? = emptyList(),
    val currentOpeningHours: CurrentOpeningHours? = null,
    val photos: List<PlacePhoto>? = null,
    val googleMapsUri: String? = null,
    val websiteUri: String? = null,
    val internationalPhoneNumber: String? = null
)

data class DisplayName(
    val text: String,
    val languageCode: String? = null
)

data class CurrentOpeningHours(
    val openNow: Boolean? = null,
    val weekdayDescriptions: List<String>? = null
)

data class PlacePhoto(
    val name: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null
)
