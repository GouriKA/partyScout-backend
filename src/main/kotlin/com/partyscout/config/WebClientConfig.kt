package com.partyscout.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    private val googlePlacesConfig: GooglePlacesConfig
) {

    @Bean
    fun googlePlacesWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(googlePlacesConfig.baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}
