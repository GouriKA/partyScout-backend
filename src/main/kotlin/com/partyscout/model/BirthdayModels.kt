package com.partyscout.model

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/**
 * Request model for birthday party planning
 */
data class BirthdayRequest(
    @field:NotNull(message = "Age is required")
    @field:Min(value = 1, message = "Age must be at least 1")
    @field:Max(value = 150, message = "Age must be at most 150")
    val age: Int,

    @field:NotBlank(message = "Area code is required")
    @field:jakarta.validation.constraints.Pattern(
        regexp = "^\\d{5}$",
        message = "Area code must be a valid 5-digit US ZIP code"
    )
    val areaCode: String,

    @field:NotNull(message = "Time is required")
    @field:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val time: LocalDateTime
)

/**
 * Venue option with detailed information including kid-friendly features
 */
data class BirthdayVenueOption(
    @field:NotBlank(message = "Venue name is required")
    val name: String,

    @field:NotBlank(message = "Address is required")
    val address: String,

    @field:NotNull(message = "Rating is required")
    @field:Min(value = 0, message = "Rating must be between 0 and 5")
    @field:Max(value = 5, message = "Rating must be between 0 and 5")
    val rating: Double,

    @field:NotNull(message = "Kid-friendly features are required")
    val kidFriendlyFeatures: KidFriendlyFeatures,

    @field:NotNull(message = "Estimated capacity is required")
    @field:Min(value = 1, message = "Capacity must be at least 1")
    val estimatedCapacity: Int,

    val description: String? = null,
    val priceRange: String? = null,
    val phoneNumber: String? = null,
    val website: String? = null,
    val distanceInMiles: Double? = null
)

/**
 * Kid-friendly features and amenities
 */
data class KidFriendlyFeatures(
    val isKidFriendly: Boolean,
    val ageRange: String? = null,
    val hasPlayArea: Boolean = false,
    val hasKidsMenu: Boolean = false,
    val hasHighChairs: Boolean = false,
    val hasChangingStation: Boolean = false,
    val entertainmentOptions: List<String> = emptyList(),
    val safetyFeatures: List<String> = emptyList(),
    val specialAccommodations: List<String> = emptyList()
)

/**
 * Response model containing list of venue options for birthday party
 */
data class BirthdayResponse(
    val venueOptions: List<BirthdayVenueOption>,
    val totalResults: Int = venueOptions.size,
    val searchParameters: BirthdaySearchParameters? = null
)

/**
 * Search parameters used for venue search
 */
data class BirthdaySearchParameters(
    val age: Int,
    val areaCode: String,
    val time: String
)
