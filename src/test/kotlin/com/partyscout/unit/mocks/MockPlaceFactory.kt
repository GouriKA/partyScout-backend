package com.partyscout.unit.mocks

import com.partyscout.dto.*

object MockPlaceFactory {

    fun createMockPlace(
        id: String = "place-1",
        name: String = "Test Venue",
        address: String = "123 Test St",
        lat: Double = 37.7900,
        lng: Double = -122.3900,
        rating: Double = 4.5,
        userRatingCount: Int = 100,
        priceLevel: String = "PRICE_LEVEL_MODERATE",
        types: List<String> = listOf("amusement_center")
    ) = Place(
        id = id,
        displayName = DisplayName(text = name),
        formattedAddress = address,
        location = LatLng(latitude = lat, longitude = lng),
        rating = rating,
        userRatingCount = userRatingCount,
        priceLevel = priceLevel,
        types = types,
        internationalPhoneNumber = "(415) 555-0123",
        websiteUri = "https://testvenue.com"
    )
}
