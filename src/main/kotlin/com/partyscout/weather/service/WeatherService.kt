package com.partyscout.weather.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val WMO_LABELS = mapOf(
    0 to "Clear Sky", 1 to "Mainly Clear", 2 to "Partly Cloudy", 3 to "Overcast",
    45 to "Foggy", 48 to "Foggy",
    51 to "Light Drizzle", 53 to "Drizzle", 55 to "Heavy Drizzle",
    61 to "Light Rain", 63 to "Rain", 65 to "Heavy Rain",
    71 to "Light Snow", 73 to "Snow", 75 to "Heavy Snow", 77 to "Snow",
    80 to "Showers", 81 to "Showers", 82 to "Heavy Showers",
    85 to "Snow Showers", 86 to "Heavy Snow Showers",
    95 to "Thunderstorm", 96 to "Thunderstorm", 99 to "Thunderstorm"
)

private fun wmoToConditionType(code: Int) = when (code) {
    0 -> "CLEAR"
    1 -> "MOSTLY_CLEAR"
    2 -> "PARTLY_CLOUDY"
    3, 45, 48 -> "OVERCAST"
    in 51..55 -> "DRIZZLE"
    in 61..65 -> "RAIN"
    in 71..77 -> "SNOW"
    in 80..82 -> "SHOWERS"
    in 85..86 -> "SNOW"
    in 95..99 -> "THUNDERSTORM"
    else -> "UNKNOWN"
}

@Service
class WeatherService {
    private val logger = LoggerFactory.getLogger(WeatherService::class.java)
    private val forecastClient = WebClient.create("https://api.open-meteo.com")
    private val archiveClient = WebClient.create("https://archive-api.open-meteo.com")
    private val geoClient = WebClient.create("https://api.zippopotam.us")

    fun getForecast(zipCode: String, date: LocalDate): WeatherForecastResponse? {
        val today = LocalDate.now()
        val daysUntil = ChronoUnit.DAYS.between(today, date).toInt()
        if (daysUntil < 0 || daysUntil > 30) return null

        val location = geocodeZip(zipCode) ?: return null

        return if (daysUntil <= 16) {
            getForecastFromOpenMeteo(location, date)
        } else {
            getClimateAverage(location, date)
        }
    }

    private fun geocodeZip(zipCode: String): LatLon? {
        return try {
            val response = geoClient.get()
                .uri("/us/$zipCode")
                .retrieve()
                .bodyToMono<ZippopotamResponse>()
                .block()
            val place = response?.places?.firstOrNull() ?: return null
            LatLon(place.latitude.toDouble(), place.longitude.toDouble())
        } catch (e: Exception) {
            logger.warn("ZIP geocoding failed for {}: {}", zipCode, e.message)
            null
        }
    }

    private fun getForecastFromOpenMeteo(location: LatLon, date: LocalDate): WeatherForecastResponse? {
        val response = forecastClient.get()
            .uri { b ->
                b.path("/v1/forecast")
                    .queryParam("latitude", location.lat)
                    .queryParam("longitude", location.lon)
                    .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weathercode")
                    .queryParam("start_date", date)
                    .queryParam("end_date", date)
                    .queryParam("timezone", "auto")
                    .queryParam("temperature_unit", "fahrenheit")
                    .build()
            }
            .retrieve()
            .bodyToMono<OpenMeteoResponse>()
            .doOnError { logger.error("Open-Meteo forecast error: {}", it.message) }
            .onErrorReturn(OpenMeteoResponse())
            .block() ?: return null

        val daily = response.daily ?: return null
        val code = daily.weathercode?.firstOrNull() ?: return null
        val highF = daily.temperatureMax?.firstOrNull()?.toInt() ?: return null
        val lowF = daily.temperatureMin?.firstOrNull()?.toInt() ?: return null
        val precipProb = daily.precipProbMax?.firstOrNull() ?: 0

        return WeatherForecastResponse(
            temperatureHighF = highF,
            temperatureLowF = lowF,
            precipitationProbability = precipProb,
            condition = WMO_LABELS[code] ?: "Unknown",
            conditionType = wmoToConditionType(code),
            forecastType = "FORECAST"
        )
    }

    private fun getClimateAverage(location: LatLon, date: LocalDate): WeatherForecastResponse? {
        // Fetch ±7 day window around same date for the past 2 years in parallel
        val mono1 = fetchArchiveWindow(location, date.minusYears(1))
        val mono2 = fetchArchiveWindow(location, date.minusYears(2))

        val (r1, r2) = Mono.zip(mono1, mono2).block()?.let { Pair(it.t1, it.t2) } ?: return null

        val allHighs = (r1.daily?.temperatureMax.orEmpty() + r2.daily?.temperatureMax.orEmpty()).filterNotNull()
        val allLows = (r1.daily?.temperatureMin.orEmpty() + r2.daily?.temperatureMin.orEmpty()).filterNotNull()
        val allPrecip = (r1.daily?.precipSum.orEmpty() + r2.daily?.precipSum.orEmpty()).filterNotNull()
        val allCodes = (r1.daily?.weathercode.orEmpty() + r2.daily?.weathercode.orEmpty()).filterNotNull()

        if (allHighs.isEmpty()) return null

        val avgHigh = allHighs.average().toInt()
        val avgLow = allLows.average().toInt()
        val rainyDays = allPrecip.count { it > 1.0 }
        val precipProb = if (allPrecip.isNotEmpty()) (rainyDays * 100 / allPrecip.size) else 0
        val dominantCode = allCodes.groupBy { it }.maxByOrNull { it.value.size }?.key ?: 2

        return WeatherForecastResponse(
            temperatureHighF = avgHigh,
            temperatureLowF = avgLow,
            precipitationProbability = precipProb,
            condition = WMO_LABELS[dominantCode] ?: "Typical conditions",
            conditionType = wmoToConditionType(dominantCode),
            forecastType = "CLIMATE_AVERAGE"
        )
    }

    private fun fetchArchiveWindow(location: LatLon, centerDate: LocalDate): Mono<OpenMeteoResponse> {
        return archiveClient.get()
            .uri { b ->
                b.path("/v1/archive")
                    .queryParam("latitude", location.lat)
                    .queryParam("longitude", location.lon)
                    .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,weathercode")
                    .queryParam("start_date", centerDate.minusDays(7))
                    .queryParam("end_date", centerDate.plusDays(7))
                    .queryParam("timezone", "auto")
                    .queryParam("temperature_unit", "fahrenheit")
                    .build()
            }
            .retrieve()
            .bodyToMono<OpenMeteoResponse>()
            .doOnError { logger.error("Open-Meteo archive error: {}", it.message) }
            .onErrorReturn(OpenMeteoResponse())
    }
}

data class WeatherForecastResponse(
    val temperatureHighF: Int,
    val temperatureLowF: Int,
    val precipitationProbability: Int,
    val condition: String,
    val conditionType: String,
    val forecastType: String  // "FORECAST" or "CLIMATE_AVERAGE"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenMeteoResponse(val daily: OpenMeteoDaily? = null)

data class LatLon(val lat: Double, val lon: Double)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ZippopotamResponse(val places: List<ZipPlace>? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ZipPlace(
    val latitude: String = "0",
    val longitude: String = "0"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenMeteoDaily(
    val time: List<String>? = null,
    @JsonProperty("temperature_2m_max") val temperatureMax: List<Double?>? = null,
    @JsonProperty("temperature_2m_min") val temperatureMin: List<Double?>? = null,
    @JsonProperty("precipitation_probability_max") val precipProbMax: List<Int?>? = null,
    @JsonProperty("precipitation_sum") val precipSum: List<Double?>? = null,
    val weathercode: List<Int?>? = null
)
