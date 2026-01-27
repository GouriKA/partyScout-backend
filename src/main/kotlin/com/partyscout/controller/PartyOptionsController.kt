package com.partyscout.controller

import com.partyscout.model.*
import com.partyscout.service.VenueSearchService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/v1")
class PartyOptionsController(
    private val venueSearchService: VenueSearchService
) {
    private val logger = LoggerFactory.getLogger(PartyOptionsController::class.java)

    @PostMapping("/party-options")
    fun getPartyOptions(@Valid @RequestBody request: PartyOptionsRequest): ResponseEntity<PartyOptionsResponse> {
        logger.info("Received party options request: age=${request.age}, areaCode=${request.areaCode}")

        // Use reactive service - block for synchronous API
        val venueOptions = venueSearchService.searchPartyOptions(request.age, request.areaCode)
            .block() ?: emptyList()

        val response = PartyOptionsResponse(
            venueOptions = venueOptions,
            searchCriteria = SearchCriteria(
                age = request.age,
                areaCode = request.areaCode,
                time = request.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        )

        logger.info("Returning ${venueOptions.size} party options")
        return ResponseEntity.ok(response)
    }
}
