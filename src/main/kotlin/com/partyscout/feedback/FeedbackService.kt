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
            request.email?.takeIf { it.isNotBlank() }?.let { message.replyTo = it }
            mailSender.send(message)
            logger.info("Feedback email sent for type={}", request.type)

            // Auto-reply to submitter if email is available
            request.email?.takeIf { it.isNotBlank() }?.let { userEmail ->
                val reply = SimpleMailMessage()
                reply.setTo(userEmail)
                reply.subject = "Thanks for your feedback — PartyScout"
                reply.from = smtpUsername
                reply.text = buildAutoReply(request.name)
                mailSender.send(reply)
                logger.info("Auto-reply sent to {}", userEmail)
            }
        } catch (e: Exception) {
            logger.error("Failed to send feedback email: {}", e.message)
            throw e
        }
    }

    private fun buildAutoReply(name: String?): String {
        val greeting = if (!name.isNullOrBlank()) "Hi $name," else "Hi there,"
        return """
            $greeting

            Thanks for taking the time to share your thoughts — we read every submission and use it to make PartyScout better.

            We'll follow up if we have questions.

            — The PartyScout team
        """.trimIndent()
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
