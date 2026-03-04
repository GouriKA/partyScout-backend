package com.partyscout.shared.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.partyscout.persistence.entity.OutboxEventEntity
import com.partyscout.persistence.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxEventListener(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(OutboxEventListener::class.java)

    @EventListener
    @Transactional
    fun handleDomainEvent(event: DomainEvent) {
        logger.debug("Persisting domain event to outbox: {}", event.eventType)

        val entity = OutboxEventEntity(
            eventType = event.eventType,
            eventVersion = event.eventVersion,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            correlationId = event.correlationId,
            payload = objectMapper.writeValueAsString(event)
        )

        outboxEventRepository.save(entity)
        logger.debug("Outbox event saved with id: {}", entity.id)
    }
}
