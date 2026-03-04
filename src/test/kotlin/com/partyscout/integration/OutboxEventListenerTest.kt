package com.partyscout.integration

import com.partyscout.integration.mocks.TestGooglePlacesConfig
import com.partyscout.persistence.repository.OutboxEventRepository
import com.partyscout.shared.event.DomainEventPublisher
import com.partyscout.shared.event.VenueSearchedEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestGooglePlacesConfig::class)
@DisplayName("Outbox Event Listener Integration Tests")
class OutboxEventListenerTest {

    @Autowired
    private lateinit var domainEventPublisher: DomainEventPublisher

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Test
    fun `should persist domain event to outbox table`() {
        val event = VenueSearchedEvent(
            correlationId = "test-outbox-correlation",
            zipCode = "94105",
            age = 7,
            partyTypes = listOf("active_play"),
            guestCount = 15,
            venueCount = 3
        )

        domainEventPublisher.publish(event)

        val events = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc()
        assertTrue(events.isNotEmpty(), "Should have at least one unpublished event")

        val saved = events.find { it.correlationId == "test-outbox-correlation" }
        assertNotNull(saved, "Should find the saved event by correlation ID")
        assertEquals("VenueSearched", saved!!.eventType)
        assertEquals("Search", saved.aggregateType)
        assertFalse(saved.published)
        assertNotNull(saved.payload)
        assertTrue(saved.payload!!.contains("94105"))
    }
}
