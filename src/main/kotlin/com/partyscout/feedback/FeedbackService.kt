package com.partyscout.feedback

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class FeedbackService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username:}") private val smtpUsername: String
) {
    private val logger = LoggerFactory.getLogger(FeedbackService::class.java)
    private val recipientEmail = "gouri.kulkarni@partyscout.live"

    fun sendFeedbackEmail(request: FeedbackRequest) {
        val body = buildEmailBody(request)

        if (smtpUsername.isBlank()) {
            logger.info("SMTP not configured — logging feedback instead:\n{}", body)
            return
        }

        try {
            val message = SimpleMailMessage()
            message.setTo(recipientEmail)
            message.subject = "[PartyScout Feedback] ${request.type}"
            message.text = body
            message.from = smtpUsername
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
