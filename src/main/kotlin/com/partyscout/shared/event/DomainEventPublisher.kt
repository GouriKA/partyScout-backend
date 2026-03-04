package com.partyscout.shared.event

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class DomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(DomainEventPublisher::class.java)

    fun publish(event: DomainEvent) {
        logger.debug("Publishing domain event: {}", event.eventType)
        applicationEventPublisher.publishEvent(event)
    }
}
