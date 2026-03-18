package com.partyscout.persistence.repository

import com.partyscout.persistence.entity.SavedEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SavedEventRepository : JpaRepository<SavedEventEntity, Long> {
    fun findByUserId(userId: Long): List<SavedEventEntity>
    fun findByUserIdAndProfileId(userId: Long, profileId: Long?): List<SavedEventEntity>
    fun existsByUserIdAndProfileIdAndGooglePlaceId(
        userId: Long,
        profileId: Long?,
        googlePlaceId: String
    ): Boolean
}
