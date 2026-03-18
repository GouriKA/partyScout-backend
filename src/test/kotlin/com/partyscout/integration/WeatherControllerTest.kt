package com.partyscout.integration

import com.partyscout.integration.mocks.TestGooglePlacesConfig
import com.partyscout.integration.mocks.TestWeatherServiceConfig
import com.partyscout.weather.service.WeatherForecastResponse
import com.partyscout.weather.service.WeatherService
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestGooglePlacesConfig::class, TestWeatherServiceConfig::class)
@DisplayName("WeatherController Integration Tests")
class WeatherControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var weatherService: WeatherService

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun aForecastResponse(forecastType: String = "FORECAST") = WeatherForecastResponse(
        temperatureHighF = 72,
        temperatureLowF = 55,
        precipitationProbability = 10,
        condition = "Mainly Clear",
        conditionType = "MOSTLY_CLEAR",
        forecastType = forecastType
    )

    // -------------------------------------------------------------------------
    // Happy-path tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v2/weather/forecast - successful responses")
    inner class SuccessfulResponses {

        @Test
        @DisplayName("should return 200 with full weather fields when service returns a FORECAST response")
        fun shouldReturn200WithForecastFields() {
            every { weatherService.getForecast(any(), any()) } returns aForecastResponse("FORECAST")

            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
                    .param("date", "2026-04-15")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.temperatureHighF").value(72))
                .andExpect(jsonPath("$.temperatureLowF").value(55))
                .andExpect(jsonPath("$.precipitationProbability").value(10))
                .andExpect(jsonPath("$.condition").value("Mainly Clear"))
                .andExpect(jsonPath("$.conditionType").value("MOSTLY_CLEAR"))
                .andExpect(jsonPath("$.forecastType").value("FORECAST"))
        }

        @Test
        @DisplayName("should return forecastType FORECAST when service returns a short-range forecast")
        fun shouldReturnForecastType() {
            every { weatherService.getForecast(any(), any()) } returns aForecastResponse("FORECAST")

            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
                    .param("date", LocalDate.now().plusDays(5).toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.forecastType").value("FORECAST"))
        }

        @Test
        @DisplayName("should return forecastType CLIMATE_AVERAGE when service returns a historical average")
        fun shouldReturnClimateAverageType() {
            every { weatherService.getForecast(any(), any()) } returns aForecastResponse("CLIMATE_AVERAGE")

            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
                    .param("date", LocalDate.now().plusDays(20).toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.forecastType").value("CLIMATE_AVERAGE"))
        }

        @Test
        @DisplayName("should return 200 with all required response fields present")
        fun shouldReturnAllRequiredFields() {
            every { weatherService.getForecast(any(), any()) } returns aForecastResponse()

            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
                    .param("date", "2026-04-15")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.temperatureHighF").exists())
                .andExpect(jsonPath("$.temperatureLowF").exists())
                .andExpect(jsonPath("$.precipitationProbability").exists())
                .andExpect(jsonPath("$.condition").exists())
                .andExpect(jsonPath("$.conditionType").exists())
                .andExpect(jsonPath("$.forecastType").exists())
        }
    }

    // -------------------------------------------------------------------------
    // 204 No Content
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v2/weather/forecast - 204 No Content")
    inner class NoContentResponses {

        @Test
        @DisplayName("should return 204 when service returns null (out-of-range date)")
        fun shouldReturn204WhenServiceReturnsNull() {
            every { weatherService.getForecast(any(), any()) } returns null

            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
                    .param("date", LocalDate.now().plusDays(35).toString())
            )
                .andExpect(status().isNoContent)
        }

        @Test
        @DisplayName("should return 204 when service returns null for a past date")
        fun shouldReturn204ForPastDate() {
            every { weatherService.getForecast(any(), any()) } returns null

            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
                    .param("date", LocalDate.now().minusDays(1).toString())
            )
                .andExpect(status().isNoContent)
        }
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request — invalid / missing parameters
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v2/weather/forecast - 400 Bad Request")
    inner class BadRequestResponses {

        @Test
        @DisplayName("should return 400 for an invalid date format")
        fun shouldReturn400ForInvalidDateFormat() {
            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
                    .param("date", "not-a-date")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for a non-ISO date format (MM/DD/YYYY)")
        fun shouldReturn400ForNonIsoDateFormat() {
            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
                    .param("date", "04/15/2026")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("should return error when zipCode parameter is missing")
        fun shouldReturnErrorWhenZipCodeIsMissing() {
            // MissingServletRequestParameterException is not mapped in GlobalExceptionHandler,
            // so it falls through to the generic handler which returns 500.
            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("date", "2026-04-15")
            )
                .andExpect(status().is5xxServerError)
        }

        @Test
        @DisplayName("should return error when date parameter is missing")
        fun shouldReturnErrorWhenDateIsMissing() {
            // MissingServletRequestParameterException is not mapped in GlobalExceptionHandler,
            // so it falls through to the generic handler which returns 500.
            mockMvc.perform(
                get("/api/v2/weather/forecast")
                    .param("zipCode", "94105")
            )
                .andExpect(status().is5xxServerError)
        }

        @Test
        @DisplayName("should return error when both parameters are missing")
        fun shouldReturnErrorWhenBothParametersAreMissing() {
            // MissingServletRequestParameterException is not mapped in GlobalExceptionHandler,
            // so it falls through to the generic handler which returns 500.
            mockMvc.perform(get("/api/v2/weather/forecast"))
                .andExpect(status().is5xxServerError)
        }
    }
}
