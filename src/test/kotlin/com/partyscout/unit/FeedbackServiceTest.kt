package com.partyscout.unit

import com.partyscout.feedback.FeedbackRequest
import com.partyscout.feedback.FeedbackService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

@DisplayName("FeedbackService")
class FeedbackServiceTest {

    private val mailSender = mockk<JavaMailSender>(relaxed = true)

    private fun service(smtpUsername: String = "sender@example.com") =
        FeedbackService(mailSender, smtpUsername)

    private val requestWithEmail = FeedbackRequest(
        name    = "Alice",
        email   = "alice@example.com",
        type    = "Bug Report",
        message = "Something is broken"
    )

    private val requestNoEmail = FeedbackRequest(
        name    = null,
        email   = null,
        type    = "General Feedback",
        message = "Great app!"
    )

    @Nested
    @DisplayName("when SMTP is not configured")
    inner class SmtpNotConfigured {

        @Test
        fun `does not send any email`() {
            service(smtpUsername = "").sendFeedbackEmail(requestWithEmail)
            verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
        }
    }

    @Nested
    @DisplayName("when SMTP is configured")
    inner class SmtpConfigured {

        @Test
        fun `sends feedback email to recipient`() {
            val messages = mutableListOf<SimpleMailMessage>()
            every { mailSender.send(capture(messages)) } returns Unit

            service().sendFeedbackEmail(requestWithEmail)

            val feedback = messages.first()
            assertTrue(feedback.to!!.contains("gouri.kulkarni@partyscout.live"))
            assertTrue(feedback.subject!!.contains("Bug Report"))
        }

        @Test
        fun `sets reply-to to submitter email`() {
            val messages = mutableListOf<SimpleMailMessage>()
            every { mailSender.send(capture(messages)) } returns Unit

            service().sendFeedbackEmail(requestWithEmail)

            val feedback = messages.first()
            assertEquals("alice@example.com", feedback.replyTo)
        }

        @Test
        fun `sends auto-reply to submitter when email is present`() {
            val messages = mutableListOf<SimpleMailMessage>()
            every { mailSender.send(capture(messages)) } returns Unit

            service().sendFeedbackEmail(requestWithEmail)

            assertEquals(2, messages.size)
            val autoReply = messages[1]
            assertTrue(autoReply.to!!.contains("alice@example.com"))
            assertTrue(autoReply.subject!!.contains("Thanks for your feedback"))
            assertTrue(autoReply.text!!.contains("Alice"))
        }

        @Test
        fun `auto-reply uses generic greeting when name is absent`() {
            val messages = mutableListOf<SimpleMailMessage>()
            every { mailSender.send(capture(messages)) } returns Unit

            service().sendFeedbackEmail(requestNoEmail.copy(email = "anon@example.com"))

            val autoReply = messages[1]
            assertTrue(autoReply.text!!.contains("Hi there,"))
        }

        @Test
        fun `does not send auto-reply when email is absent`() {
            val messages = mutableListOf<SimpleMailMessage>()
            every { mailSender.send(capture(messages)) } returns Unit

            service().sendFeedbackEmail(requestNoEmail)

            assertEquals(1, messages.size)
        }

        @Test
        fun `feedback email body contains message text`() {
            val messages = mutableListOf<SimpleMailMessage>()
            every { mailSender.send(capture(messages)) } returns Unit

            service().sendFeedbackEmail(requestWithEmail)

            val feedback = messages.first()
            assertTrue(feedback.text!!.contains("Something is broken"))
            assertTrue(feedback.text!!.contains("Alice"))
            assertTrue(feedback.text!!.contains("alice@example.com"))
        }
    }
}
