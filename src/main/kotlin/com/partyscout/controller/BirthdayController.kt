package com.partyscout.controller

import com.partyscout.model.*
import com.partyscout.service.VenueSearchService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/birthdays")
class BirthdayController(
    private val venueSearchService: VenueSearchService
) {
    private val logger = LoggerFactory.getLogger(BirthdayController::class.java)

    @PostMapping("/search")
    fun searchBirthdayVenues(@Valid @RequestBody request: BirthdayRequest): ResponseEntity<BirthdayResponse> {
        logger.info("Received birthday venue search request: age=${request.age}, areaCode=${request.areaCode}")

        // Use reactive service - block for synchronous API
        val venueOptions = venueSearchService.searchVenues(request.age, request.areaCode)
            .block() ?: emptyList()

        val response = BirthdayResponse(
            venueOptions = venueOptions,
            totalResults = venueOptions.size,
            searchParameters = BirthdaySearchParameters(
                age = request.age,
                areaCode = request.areaCode,
                time = request.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )

        logger.info("Returning ${venueOptions.size} venue options")
        return ResponseEntity.ok(response)
    }
}
