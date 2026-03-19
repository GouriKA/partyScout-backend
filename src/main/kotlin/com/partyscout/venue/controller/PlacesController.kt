package com.partyscout.venue.controller

import com.partyscout.venue.service.GooglePlacesService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/places")
class PlacesController(
    private val googlePlacesService: GooglePlacesService
) {

    /**
     * City autocomplete — returns up to 5 US city name suggestions.
     * GET /api/v2/places/autocomplete?input=Aus
     */
    @GetMapping("/autocomplete")
    fun autocomplete(
        @RequestParam(required = true) input: String
    ): ResponseEntity<List<String>> {
        if (input.isBlank()) return ResponseEntity.ok(emptyList())
        val suggestions = googlePlacesService.autocompleteCity(input).block() ?: emptyList()
        return ResponseEntity.ok(suggestions)
    }
}
