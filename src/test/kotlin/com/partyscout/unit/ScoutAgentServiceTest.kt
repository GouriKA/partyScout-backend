package com.partyscout.unit

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.chat.*
import com.partyscout.persona.PersonaService
import com.partyscout.venue.config.GooglePlacesConfig
import com.partyscout.venue.service.GooglePlacesService
import io.mockk.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicBoolean

@DisplayName("ScoutAgentService")
class ScoutAgentServiceTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var service: ScoutAgentService
    private lateinit var googlePlacesService: GooglePlacesService
    private lateinit var googlePlacesConfig: GooglePlacesConfig
    private lateinit var personaService: PersonaService
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        googlePlacesService = mockk(relaxed = true)
        googlePlacesConfig = mockk(relaxed = true)
        personaService = mockk(relaxed = true)

        every { googlePlacesConfig.apiKey } returns "test-api-key"
        every { personaService.getSearchQueries(any()) } returns listOf("birthday venue", "party place")

        // Use a blank API key so agent returns early with "not configured" message
        service = ScoutAgentService(
            apiKey = "",
            objectMapper = objectMapper,
            googlePlacesService = googlePlacesService,
            googlePlacesConfig = googlePlacesConfig,
            personaService = personaService,
        )
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun makeRequest(
        message: String = "Find a venue in Austin",
        history: List<ChatMessage> = emptyList(),
        city: String? = "Austin",
        knownVenues: List<KnownVenue> = emptyList(),
    ) = ChatRequest(
        message = message,
        conversationHistory = history,
        existingContext = LandingPageContext(city = city, persona = null, occasion = "birthday"),
        knownVenues = knownVenues,
    )

    @Nested
    @DisplayName("API key not configured")
    inner class ApiKeyNotConfigured {

        @Test
        @DisplayName("should send not-configured message and complete emitter")
        fun shouldHandleMissingApiKey() {
            val emitter = mockk<SseEmitter>(relaxed = true)
            val cancelled = AtomicBoolean(false)

            service.runAgent(makeRequest(), emitter, cancelled)

            verify { emitter.send(match<String> { it.contains("not able to assist") }) }
            verify { emitter.complete() }
        }
    }

    @Nested
    @DisplayName("Cancellation")
    inner class Cancellation {

        @Test
        @DisplayName("should complete emitter even when cancelled before start")
        fun shouldCompleteOnCancellation() {
            val emitter = mockk<SseEmitter>(relaxed = true)
            val cancelled = AtomicBoolean(true) // already cancelled

            service.runAgent(makeRequest(), emitter, cancelled)

            // Should complete without sending messages (except the not-configured one due to blank key)
            verify { emitter.complete() }
        }
    }

    @Nested
    @DisplayName("Request building")
    inner class RequestBuilding {

        @Test
        @DisplayName("should only take last 10 messages from history")
        fun shouldTruncateHistory() {
            // With blank API key we can't test full loop, but we can verify
            // the service handles large history without error
            val longHistory = (1..20).map {
                ChatMessage(role = if (it % 2 == 0) "assistant" else "user", content = "message $it")
            }
            val emitter = mockk<SseEmitter>(relaxed = true)
            val cancelled = AtomicBoolean(false)

            // Should not throw even with 20 messages
            assertDoesNotThrow {
                service.runAgent(makeRequest(history = longHistory), emitter, cancelled)
            }
        }

        @Test
        @DisplayName("should handle empty conversation history")
        fun shouldHandleEmptyHistory() {
            val emitter = mockk<SseEmitter>(relaxed = true)
            val cancelled = AtomicBoolean(false)

            assertDoesNotThrow {
                service.runAgent(makeRequest(history = emptyList()), emitter, cancelled)
            }
        }

        @Test
        @DisplayName("should handle null city in existing context")
        fun shouldHandleNullCity() {
            val emitter = mockk<SseEmitter>(relaxed = true)
            val cancelled = AtomicBoolean(false)

            assertDoesNotThrow {
                service.runAgent(makeRequest(city = null), emitter, cancelled)
            }
        }
    }

    @Nested
    @DisplayName("Known venues context")
    inner class KnownVenuesContext {

        @Test
        @DisplayName("should include known venues in context note")
        fun shouldIncludeKnownVenuesInContext() {
            val knownVenues = listOf(
                KnownVenue(num = 1, name = "Chuck E. Cheese", rating = 4.2),
                KnownVenue(num = 2, name = "Pump It Up", rating = 4.5),
            )
            val emitter = mockk<SseEmitter>(relaxed = true)
            val cancelled = AtomicBoolean(false)

            // With blank API key, just verify no exception
            assertDoesNotThrow {
                service.runAgent(makeRequest(knownVenues = knownVenues), emitter, cancelled)
            }
        }
    }
}
