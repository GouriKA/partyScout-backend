package com.partyscout.integration.mocks

import com.partyscout.weather.service.WeatherService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestWeatherServiceConfig {

    @Bean
    @Primary
    fun mockWeatherService(): WeatherService = mockk(relaxed = true)
}
