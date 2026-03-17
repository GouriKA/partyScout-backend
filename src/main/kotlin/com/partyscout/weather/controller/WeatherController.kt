package com.partyscout.weather.controller

import com.partyscout.weather.service.WeatherForecastResponse
import com.partyscout.weather.service.WeatherService
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v2/weather")
class WeatherController(
    private val weatherService: WeatherService
) {
    private val logger = LoggerFactory.getLogger(WeatherController::class.java)

    @GetMapping("/forecast")
    fun getForecast(
        @RequestParam zipCode: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<WeatherForecastResponse> {
        logger.info("Weather forecast request: zip={}, date={}", zipCode, date)
        val forecast = weatherService.getForecast(zipCode, date)
        return if (forecast != null) ResponseEntity.ok(forecast)
        else ResponseEntity.noContent().build()
    }
}
