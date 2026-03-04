package com.partyscout.persistence.repository

import com.partyscout.persistence.entity.OutboxEventEntity
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventRepository : JpaRepository<OutboxEventEntity, Long> {
    fun findByPublishedFalseOrderByCreatedAtAsc(): List<OutboxEventEntity>
}
