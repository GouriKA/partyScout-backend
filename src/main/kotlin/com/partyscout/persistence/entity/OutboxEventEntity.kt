package com.partyscout.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "event_type", nullable = false)
    var eventType: String = "",

    @Column(name = "event_version")
    var eventVersion: Int = 1,

    @Column(name = "aggregate_type")
    var aggregateType: String? = null,

    @Column(name = "aggregate_id")
    var aggregateId: String? = null,

    @Column(name = "correlation_id")
    var correlationId: String? = null,

    @Column(columnDefinition = "TEXT")
    var payload: String? = null,

    var published: Boolean = false,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null
)
