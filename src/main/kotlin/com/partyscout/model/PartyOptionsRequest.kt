package com.partyscout.model

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime

data class PartyOptionsRequest(
    @field:NotNull(message = "Age is required")
    @field:Min(value = 1, message = "Age must be at least 1")
    @field:Max(value = 150, message = "Age must be at most 150")
    val age: Int,

    @field:NotBlank(message = "Area code is required")
    @field:Pattern(
        regexp = "^\\d{5}$",
        message = "Area code must be a valid 5-digit US ZIP code"
    )
    val areaCode: String,

    @field:NotNull(message = "Time is required")
    @field:JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val time: LocalDateTime
)
