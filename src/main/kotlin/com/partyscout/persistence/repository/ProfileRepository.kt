package com.partyscout.persistence.repository

import com.partyscout.persistence.entity.ProfileEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProfileRepository : JpaRepository<ProfileEntity, Long> {
    fun findByUserId(userId: Long): List<ProfileEntity>
    fun findByUserIdAndNameAndAge(userId: Long, name: String?, age: Int): ProfileEntity?
}
