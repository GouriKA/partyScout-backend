package com.partyscout.unit

import com.partyscout.weather.service.WeatherService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.lang.reflect.Field
import java.time.LocalDate

@DisplayName("WeatherService")
class WeatherServiceTest {

    private lateinit var weatherService: WeatherService
    private lateinit var forecastServer: MockWebServer
    private lateinit var archiveServer: MockWebServer
    private lateinit var geoServer: MockWebServer

    @BeforeEach
    fun setUp() {
        forecastServer = MockWebServer()
        archiveServer = MockWebServer()
        geoServer = MockWebServer()

        forecastServer.start()
        archiveServer.start()
        geoServer.start()

        weatherService = WeatherService()

        // Inject MockWebServer-backed clients via reflection to override hardcoded URLs
        injectWebClient(weatherService, "forecastClient", forecastServer.url("/").toString().trimEnd('/'))
        injectWebClient(weatherService, "archiveClient", archiveServer.url("/").toString().trimEnd('/'))
        injectWebClient(weatherService, "geoClient", geoServer.url("/").toString().trimEnd('/'))
    }

    @AfterEach
    fun tearDown() {
        forecastServer.shutdown()
        archiveServer.shutdown()
        geoServer.shutdown()
    }

    private fun injectWebClient(target: Any, fieldName: String, baseUrl: String) {
        val field: Field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, WebClient.create(baseUrl))
    }

    // -------------------------------------------------------------------------
    // Date boundary logic (no HTTP needed)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getForecast - date boundary validation")
    inner class DateBoundaryValidation {

        @Test
        @DisplayName("should return null for a past date (yesterday)")
        fun shouldReturnNullForPastDate() {
            val yesterday = LocalDate.now().minusDays(1)

            val result = weatherService.getForecast("94105", yesterday)

            assertNull(result, "Expected null for a past date")
        }

        @Test
        @DisplayName("should return null for a date 31 days in the future")
        fun shouldReturnNullForDateMoreThan30DaysOut() {
            val tooFar = LocalDate.now().plusDays(31)

            val result = weatherService.getForecast("94105", tooFar)

            assertNull(result, "Expected null for a date more than 30 days out")
        }

        @Test
        @DisplayName("should return null for a date exactly 31 days in the future")
        fun shouldReturnNullForExactly31Days() {
            val tooFar = LocalDate.now().plusDays(31)

            val result = weatherService.getForecast("94105", tooFar)

            assertNull(result)
        }
    }

    // -------------------------------------------------------------------------
    // HTTP-backed tests: geocoding failure
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getForecast - geocoding")
    inner class GeocodingBehaviour {

        @Test
        @DisplayName("should return null when geocoding returns 404")
        fun shouldReturnNullWhenGeocodingFails() {
            geoServer.enqueue(MockResponse().setResponseCode(404))

            val date = LocalDate.now().plusDays(5)
            val result = weatherService.getForecast("00000", date)

            assertNull(result, "Expected null when geocoding fails")
        }

        @Test
        @DisplayName("should return null when geocoding returns empty places list")
        fun shouldReturnNullWhenGeocodingReturnsNoPlaces() {
            geoServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""{"places":[]}""")
            )

            val date = LocalDate.now().plusDays(5)
            val result = weatherService.getForecast("99999", date)

            assertNull(result, "Expected null when geocoding returns no places")
        }
    }

    // -------------------------------------------------------------------------
    // HTTP-backed tests: FORECAST path (date within 16 days)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getForecast - FORECAST type (within 16 days)")
    inner class ForecastType {

        @Test
        @DisplayName("should return FORECAST response for a near-future date")
        fun shouldReturnForecastResponseForNearFutureDate() {
            // Geocoding response
            geoServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "post code": "94105",
                          "country": "United States",
                          "places": [
                            {
                              "place name": "San Francisco",
                              "longitude": "-122.3932",
                              "latitude": "37.7893",
                              "state": "California",
                              "state abbreviation": "CA"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )

            // Open-Meteo forecast response
            forecastServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "daily": {
                            "time": ["2026-04-01"],
                            "temperature_2m_max": [72.5],
                            "temperature_2m_min": [55.0],
                            "precipitation_probability_max": [10],
                            "weathercode": [1]
                          }
                        }
                        """.trimIndent()
                    )
            )

            val date = LocalDate.now().plusDays(5)
            val result = weatherService.getForecast("94105", date)

            assertNotNull(result, "Expected a forecast response")
            assertEquals("FORECAST", result!!.forecastType)
            assertEquals(72, result.temperatureHighF)
            assertEquals(55, result.temperatureLowF)
            assertEquals(10, result.precipitationProbability)
            assertEquals("Mainly Clear", result.condition)
            assertEquals("MOSTLY_CLEAR", result.conditionType)
        }

        @Test
        @DisplayName("should return FORECAST response with correct fields for today")
        fun shouldReturnForecastResponseForToday() {
            geoServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "places": [
                            {
                              "latitude": "37.7893",
                              "longitude": "-122.3932"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )

            forecastServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "daily": {
                            "time": ["${LocalDate.now()}"],
                            "temperature_2m_max": [68.0],
                            "temperature_2m_min": [50.0],
                            "precipitation_probability_max": [30],
                            "weathercode": [61]
                          }
                        }
                        """.trimIndent()
                    )
            )

            val result = weatherService.getForecast("94105", LocalDate.now())

            assertNotNull(result)
            assertEquals("FORECAST", result!!.forecastType)
            assertEquals(68, result.temperatureHighF)
            assertEquals(50, result.temperatureLowF)
            assertEquals(30, result.precipitationProbability)
            assertEquals("RAIN", result.conditionType)
        }
    }

    // -------------------------------------------------------------------------
    // HTTP-backed tests: CLIMATE_AVERAGE path (date 17–30 days out)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getForecast - CLIMATE_AVERAGE type (17 to 30 days out)")
    inner class ClimateAverageType {

        @Test
        @DisplayName("should return CLIMATE_AVERAGE response for a date 20 days out")
        fun shouldReturnClimateAverageResponseFor20DaysOut() {
            // Geocoding
            geoServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "places": [
                            {
                              "latitude": "37.7893",
                              "longitude": "-122.3932"
                            }
                          ]
                        }
                        """.trimIndent()
                    )
            )

            val archiveBody = """
                {
                  "daily": {
                    "time": ["2025-04-07","2025-04-08","2025-04-09"],
                    "temperature_2m_max": [65.0, 67.0, 63.0],
                    "temperature_2m_min": [48.0, 50.0, 46.0],
                    "precipitation_sum": [0.0, 2.5, 0.0],
                    "weathercode": [1, 61, 2]
                  }
                }
            """.trimIndent()

            // Two archive calls (year-1 and year-2) run in parallel via Mono.zip
            archiveServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(archiveBody)
            )
            archiveServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(archiveBody)
            )

            val date = LocalDate.now().plusDays(20)
            val result = weatherService.getForecast("94105", date)

            assertNotNull(result, "Expected a climate-average response")
            assertEquals("CLIMATE_AVERAGE", result!!.forecastType)
            // Averages of [65, 67, 63, 65, 67, 63] = 65
            assertEquals(65, result.temperatureHighF)
            // Averages of [48, 50, 46, 48, 50, 46] = 48
            assertEquals(48, result.temperatureLowF)
        }

        @Test
        @DisplayName("should return null when archive data is empty")
        fun shouldReturnNullWhenArchiveDataIsEmpty() {
            geoServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(
                        """{"places":[{"latitude":"37.7893","longitude":"-122.3932"}]}"""
                    )
            )

            val emptyArchive = """{"daily": {"temperature_2m_max": [], "temperature_2m_min": [], "precipitation_sum": [], "weathercode": []}}"""
            archiveServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(emptyArchive)
            )
            archiveServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(emptyArchive)
            )

            val date = LocalDate.now().plusDays(20)
            val result = weatherService.getForecast("94105", date)

            assertNull(result, "Expected null when archive data has no temperature readings")
        }
    }
}
