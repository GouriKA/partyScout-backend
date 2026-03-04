package com.partyscout.shared.event

import java.time.Instant
import java.util.UUID

abstract class DomainEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val eventVersion: Int = 1,
    val occurredAt: Instant = Instant.now(),
    val correlationId: String? = null,
    val aggregateType: String,
    val aggregateId: String
)
