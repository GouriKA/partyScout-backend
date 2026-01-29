package com.partyscout.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {

    @Bean
    fun corsFilter(): CorsFilter {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()

        // Allow frontend origins (local dev + Cloud Run + Cloud Storage)
        config.allowedOriginPatterns = listOf(
            "http://localhost:5173",
            "http://localhost:3000",
            "https://*.run.app",
            "https://storage.googleapis.com",
            "https://*.storage.googleapis.com"
        )

        // Allow all HTTP methods
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")

        // Allow all headers
        config.allowedHeaders = listOf("*")

        // Allow credentials
        config.allowCredentials = true

        source.registerCorsConfiguration("/**", config)
        return CorsFilter(source)
    }
}
