package com.partyscout.unit

import com.partyscout.shared.event.DomainEventPublisher
import com.partyscout.shared.event.VenueSearchedEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

@DisplayName("DomainEventPublisher Tests")
class DomainEventPublisherTest {

    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val domainEventPublisher = DomainEventPublisher(applicationEventPublisher)

    @Test
    fun `should publish event via ApplicationEventPublisher`() {
        val event = VenueSearchedEvent(
            correlationId = "test-correlation-id",
            city = "Austin, TX",
            age = 7,
            partyTypes = listOf("active_play"),
            guestCount = 15,
            venueCount = 5
        )

        domainEventPublisher.publish(event)

        verify(exactly = 1) { applicationEventPublisher.publishEvent(event) }
    }

    @Test
    fun `should publish event with null correlation id`() {
        val event = VenueSearchedEvent(
            correlationId = null,
            city = "Austin, TX",
            age = 7,
            partyTypes = listOf("active_play"),
            guestCount = 15,
            venueCount = 5
        )

        domainEventPublisher.publish(event)

        verify(exactly = 1) { applicationEventPublisher.publishEvent(event) }
    }
}
