package com.partyscout.feedback

import org.slf4j.LoggerFactory
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class FeedbackService(private val mailSender: JavaMailSender) {
    private val logger = LoggerFactory.getLogger(FeedbackService::class.java)
    private val recipientEmail = "gouri.kulkarni@partyscout.live"

    fun sendFeedbackEmail(request: FeedbackRequest) {
        try {
            val message = SimpleMailMessage()
            message.setTo(recipientEmail)
            message.subject = "[PartyScout Feedback] ${request.type}"
            message.text = buildEmailBody(request)
            message.from = "noreply@partyscout.live"
            mailSender.send(message)
            logger.info("Feedback email sent for type={}", request.type)
        } catch (e: Exception) {
            logger.error("Failed to send feedback email: {}", e.message)
            throw e
        }
    }

    private fun buildEmailBody(request: FeedbackRequest): String {
        return buildString {
            appendLine("New feedback from PartyScout")
            appendLine("=" .repeat(40))
            appendLine("Type: ${request.type}")
            request.name?.let { appendLine("Name: $it") }
            request.email?.let { appendLine("Email: $it") }
            appendLine()
            appendLine("Message:")
            appendLine(request.message)
        }
    }
}
