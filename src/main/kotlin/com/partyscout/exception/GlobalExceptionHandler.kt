package com.partyscout.exception

import com.partyscout.service.GooglePlacesException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.reactive.function.client.WebClientResponseException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(GooglePlacesException::class)
    fun handleGooglePlacesException(ex: GooglePlacesException): ResponseEntity<ErrorResponse> {
        logger.error("Google Places API error", ex)
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(
                error = "GOOGLE_PLACES_ERROR",
                message = "Unable to fetch venue data from Google Places API",
                details = ex.message
            ))
    }

    @ExceptionHandler(WebClientResponseException::class)
    fun handleWebClientException(ex: WebClientResponseException): ResponseEntity<ErrorResponse> {
        logger.error("Web client error: ${ex.statusCode}", ex)
        return ResponseEntity
            .status(ex.statusCode)
            .body(ErrorResponse(
                error = "EXTERNAL_API_ERROR",
                message = "Error communicating with external service",
                details = ex.message
            ))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.allErrors.map { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.defaultMessage ?: "Invalid value"
            "$fieldName: $errorMessage"
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "VALIDATION_ERROR",
                message = "Invalid request parameters",
                details = errors.joinToString(", ")
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                error = "INTERNAL_ERROR",
                message = "An unexpected error occurred",
                details = null
            ))
    }
}

data class ErrorResponse(
    val error: String,
    val message: String,
    val details: String?
)
