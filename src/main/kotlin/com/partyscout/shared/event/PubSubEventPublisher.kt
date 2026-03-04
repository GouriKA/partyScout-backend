package com.partyscout.shared.event

import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.partyscout.persistence.entity.OutboxEventEntity
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["spring.cloud.gcp.pubsub.enabled"],
    havingValue = "true"
)
class PubSubEventPublisher(
    private val pubSubTemplate: PubSubTemplate
) {
    private val logger = LoggerFactory.getLogger(PubSubEventPublisher::class.java)

    fun publish(event: OutboxEventEntity) {
        val topic = mapEventTypeToTopic(event.eventType)
        logger.info("Publishing event to Pub/Sub topic={}, eventType={}", topic, event.eventType)

        pubSubTemplate.publish(topic, event.payload ?: "{}")
        logger.debug("Event published successfully to topic={}", topic)
    }

    private fun mapEventTypeToTopic(eventType: String): String {
        return when (eventType) {
            "VenueSearched" -> "partyscout-venue-events"
            "BudgetEstimated" -> "partyscout-budget-events"
            else -> "partyscout-domain-events"
        }
    }
}
