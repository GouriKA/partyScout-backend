package com.partyscout.shared.event

import com.partyscout.persistence.repository.OutboxEventRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@ConditionalOnProperty(
    name = ["partyscout.outbox.poller.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class OutboxPoller(
    private val outboxEventRepository: OutboxEventRepository,
    private val pubSubEventPublisher: PubSubEventPublisher?
) {
    private val logger = LoggerFactory.getLogger(OutboxPoller::class.java)

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "outboxPoller", lockAtLeastFor = "4s", lockAtMostFor = "9m")
    @Transactional
    fun pollAndPublish() {
        val events = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc()
        if (events.isEmpty()) return

        logger.info("Processing {} unpublished outbox events", events.size)

        for (event in events) {
            try {
                if (pubSubEventPublisher != null) {
                    pubSubEventPublisher.publish(event)
                } else {
                    logger.info("Publishing outbox event (no Pub/Sub): type={}, correlationId={}", event.eventType, event.correlationId)
                }
                event.published = true
                event.publishedAt = Instant.now()
                outboxEventRepository.save(event)
            } catch (e: Exception) {
                logger.error("Failed to publish outbox event id={}: {}", event.id, e.message)
                break
            }
        }
    }
}
