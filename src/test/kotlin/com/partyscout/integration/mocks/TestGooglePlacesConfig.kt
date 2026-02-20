package com.partyscout.integration.mocks

import com.partyscout.dto.*
import com.partyscout.service.GooglePlacesService
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import reactor.core.publisher.Mono

@TestConfiguration
class TestGooglePlacesConfig {

    @Bean
    @Primary
    fun mockGooglePlacesService(): GooglePlacesService {
        val mockService = mockk<GooglePlacesService>(relaxed = true)

        every { mockService.geocodeZipCode(any()) } returns Mono.just(
            Location(lat = 37.7893, lng = -122.3932)
        )

        every { mockService.searchNearbyPlaces(any(), any(), any()) } returns Mono.just(
            SearchNearbyResponse(
                places = listOf(
                    Place(
                        id = "mock-venue-1",
                        displayName = DisplayName(text = "Mock Trampoline Park"),
                        formattedAddress = "123 Mock St, San Francisco, CA 94105",
                        location = LatLng(latitude = 37.7900, longitude = -122.3900),
                        rating = 4.5,
                        userRatingCount = 234,
                        priceLevel = "PRICE_LEVEL_MODERATE",
                        types = listOf("amusement_center", "gym"),
                        internationalPhoneNumber = "(415) 555-0123",
                        websiteUri = "https://mocktrampoline.com"
                    ),
                    Place(
                        id = "mock-venue-2",
                        displayName = DisplayName(text = "Mock Bowling Alley"),
                        formattedAddress = "456 Mock Ave, San Francisco, CA 94105",
                        location = LatLng(latitude = 37.7910, longitude = -122.3910),
                        rating = 4.2,
                        userRatingCount = 156,
                        priceLevel = "PRICE_LEVEL_INEXPENSIVE",
                        types = listOf("bowling_alley"),
                        internationalPhoneNumber = "(415) 555-0456"
                    )
                )
            )
        )

        return mockService
    }
}
