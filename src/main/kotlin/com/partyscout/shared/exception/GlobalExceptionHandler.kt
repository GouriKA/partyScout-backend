package com.partyscout.shared.exception

import com.partyscout.shared.logging.LogSanitizer
import com.partyscout.venue.service.GooglePlacesException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.reactive.function.client.WebClientResponseException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(GooglePlacesException::class)
    fun handleGooglePlacesException(ex: GooglePlacesException): ResponseEntity<ErrorResponse> {
        // Log only the sanitized message — the exception may contain API URLs with keys
        logger.error("Google Places API error: {}", LogSanitizer.scrubPii(ex.message ?: "unknown"))
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(
                error = "GOOGLE_PLACES_ERROR",
                message = "Unable to fetch venue data from Google Places API",
                details = null  // Never leak upstream API details to clients
            ))
    }

    @ExceptionHandler(WebClientResponseException::class)
    fun handleWebClientException(ex: WebClientResponseException): ResponseEntity<ErrorResponse> {
        // Log status only — response body may contain sensitive upstream data
        logger.error("External API error: status={}", ex.statusCode)
        return ResponseEntity
            .status(ex.statusCode)
            .body(ErrorResponse(
                error = "EXTERNAL_API_ERROR",
                message = "Error communicating with external service",
                details = null  // Never expose upstream error bodies to clients
            ))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        // Field names are safe to log; user-supplied values are NOT included
        val errors = ex.bindingResult.allErrors.map { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.defaultMessage ?: "Invalid value"
            "$fieldName: $errorMessage"
        }
        logger.warn("Validation failed: {}", errors.joinToString(", "))

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "VALIDATION_ERROR",
                message = "Invalid request parameters",
                details = errors.joinToString(", ")
            ))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        // Scrub the exception message — it may echo back user-supplied JSON content
        logger.warn("Malformed request body: {}", LogSanitizer.scrubPii(ex.message ?: "unreadable"))
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "BAD_REQUEST",
                message = "Malformed or unreadable request body",
                details = null  // Don't echo parse details — may contain user input
            ))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("Type mismatch on parameter '{}': received '{}'", ex.name, ex.value)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "BAD_REQUEST",
                message = "Invalid parameter type for '${ex.name}'",
                details = null
            ))
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> {
        // No need to log — the RequestLoggingFilter already logs 404 responses
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                error = "NOT_FOUND",
                message = "The requested resource was not found",
                details = null
            ))
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleHttpMediaTypeNotSupported(ex: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> {
        logger.warn("Unsupported media type: {}", ex.contentType)
        return ResponseEntity
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(ErrorResponse(
                error = "UNSUPPORTED_MEDIA_TYPE",
                message = "Content type '${ex.contentType}' is not supported",
                details = null
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        // Log class + sanitized message. Stack trace is included by logback for ERROR level.
        logger.error("Unexpected error [{}]: {}", ex.javaClass.simpleName, LogSanitizer.scrubPii(ex.message ?: "unknown"), ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                error = "INTERNAL_ERROR",
                message = "An unexpected error occurred",
                details = null  // Never expose internal details to clients
            ))
    }
}

data class ErrorResponse(
    val error: String,
    val message: String,
    val details: String?
)
