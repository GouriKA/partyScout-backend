package com.partyscout.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "google.places")
data class GooglePlacesConfig(
    var apiKey: String = "",
    var baseUrl: String = "https://maps.googleapis.com/maps/api/place"
)
