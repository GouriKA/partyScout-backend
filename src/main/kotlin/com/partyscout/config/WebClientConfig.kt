package com.partyscout.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig(
    private val googlePlacesConfig: GooglePlacesConfig
) {

    @Bean
    fun googlePlacesWebClient(): WebClient {
        // Increase buffer size to handle larger Google Places responses
        val exchangeStrategies = ExchangeStrategies.builder()
            .codecs { configurer: ClientCodecConfigurer ->
                configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) // 2MB
            }
            .build()

        return WebClient.builder()
            .baseUrl(googlePlacesConfig.baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .exchangeStrategies(exchangeStrategies)
            .build()
    }
}
