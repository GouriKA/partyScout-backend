package com.partyscout.auth.repository

import com.partyscout.auth.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByFirebaseUid(firebaseUid: String): Optional<UserEntity>
    fun findByEmail(email: String): Optional<UserEntity>
}
