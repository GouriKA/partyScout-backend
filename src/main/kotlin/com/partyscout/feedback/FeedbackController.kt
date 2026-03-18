package com.partyscout.feedback

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v2/feedback")
class FeedbackController(private val feedbackService: FeedbackService) {
    private val logger = LoggerFactory.getLogger(FeedbackController::class.java)

    @PostMapping
    fun submitFeedback(@Valid @RequestBody request: FeedbackRequest): ResponseEntity<Map<String, String>> {
        logger.info("Feedback received: type={}", request.type)
        feedbackService.sendFeedbackEmail(request)
        return ResponseEntity.ok(mapOf("status" to "received"))
    }
}

data class FeedbackRequest(
    val name: String? = null,
    val email: String? = null,
    @field:NotBlank(message = "Feedback type is required")
    val type: String,
    @field:NotBlank(message = "Message is required")
    @field:Size(max = 2000, message = "Message must be under 2000 characters")
    val message: String
)
