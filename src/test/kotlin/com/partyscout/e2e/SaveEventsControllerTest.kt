package com.partyscout.e2e

import com.partyscout.integration.mocks.TestGooglePlacesConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestGooglePlacesConfig::class)
@DisplayName("SaveEventsController End-to-End Tests")
class SaveEventsControllerTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    private fun baseUrl() = "http://localhost:$port"

    // ── Auth guard tests ──────────────────────────────────────────────────────
    //
    // The service layer is unit-tested thoroughly in SaveEventsServiceTest.
    // These E2E tests verify that Firebase auth is enforced at the HTTP boundary.

    @Nested
    @DisplayName("GET /api/v2/saved-events")
    inner class GetSavedEvents {

        @Test
        @DisplayName("should return 401 when request has no Authorization header")
        fun shouldReturn401WithoutAuth() {
            // When
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/saved-events",
                String::class.java
            )

            // Then
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }

    @Nested
    @DisplayName("GET /api/v2/profiles")
    inner class GetProfiles {

        @Test
        @DisplayName("should return 401 when request has no Authorization header")
        fun shouldReturn401WithoutAuth() {
            // When
            val response = restTemplate.getForEntity(
                "${baseUrl()}/api/v2/profiles",
                String::class.java
            )

            // Then
            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        }
    }
}
