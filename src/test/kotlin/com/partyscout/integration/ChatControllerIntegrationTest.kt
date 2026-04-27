package com.partyscout.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.chat.ChatMessage
import com.partyscout.chat.ChatRequest
import com.partyscout.chat.KnownVenue
import com.partyscout.chat.LandingPageContext
import com.partyscout.integration.mocks.TestGooglePlacesConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestGooglePlacesConfig::class)
@DisplayName("ChatController Integration Tests")
class ChatControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun buildRequest(
        message: String = "Find a venue in Austin for a 7-year-old",
        history: List<ChatMessage> = emptyList(),
        city: String? = "Austin",
        occasion: String? = "birthday",
        knownVenues: List<KnownVenue> = emptyList(),
    ) = ChatRequest(
        message = message,
        conversationHistory = history,
        existingContext = LandingPageContext(city = city, persona = null, occasion = occasion),
        knownVenues = knownVenues,
    )

    @Nested
    @DisplayName("POST /api/chat")
    inner class PostChat {

        @Test
        @DisplayName("should return 200 with text/event-stream content type")
        fun shouldReturnSseStream() {
            val request = buildRequest()

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
        }

        @Test
        @DisplayName("should accept request with empty conversation history")
        fun shouldAcceptEmptyHistory() {
            val request = buildRequest(history = emptyList())

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("should accept request with known venues")
        fun shouldAcceptRequestWithKnownVenues() {
            val request = buildRequest(
                message = "Is venue 1 good for toddlers?",
                knownVenues = listOf(
                    KnownVenue(num = 1, name = "Chuck E. Cheese", rating = 4.2, address = "123 Main St"),
                    KnownVenue(num = 2, name = "Pump It Up", rating = 4.5),
                )
            )

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("should accept request with null city in context")
        fun shouldAcceptNullCity() {
            val request = buildRequest(city = null)

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("should accept request with conversation history")
        fun shouldAcceptRequestWithHistory() {
            val history = listOf(
                ChatMessage(role = "user", content = "I need a venue in Austin"),
                ChatMessage(role = "assistant", content = "Who's the party for?"),
                ChatMessage(role = "user", content = "My 7-year-old"),
            )
            val request = buildRequest(
                message = "Show me options",
                history = history,
            )

            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("should return 400 for malformed JSON body")
        fun shouldRejectMalformedJson() {
            mockMvc.perform(
                post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ invalid json }")
            )
                .andExpect(status().isBadRequest)
        }
    }
}
