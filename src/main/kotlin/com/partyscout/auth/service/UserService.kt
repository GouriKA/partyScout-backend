package com.partyscout.auth.service

import com.google.firebase.auth.FirebaseToken
import com.partyscout.auth.entity.UserEntity
import com.partyscout.auth.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class UserService(private val userRepository: UserRepository) {

    private val log = LoggerFactory.getLogger(UserService::class.java)

    @Transactional
    fun getOrCreateUser(token: FirebaseToken): UserEntity {
        val existing = userRepository.findByFirebaseUid(token.uid)

        return if (existing.isPresent) {
            val user = existing.get()
            user.lastSeenAt = Instant.now()
            // Keep display name / photo in sync with Firebase
            user.displayName = token.name
            user.photoUrl = token.picture
            user.emailVerified = token.isEmailVerified
            userRepository.save(user)
        } else {
            log.info("Creating new user for provider={}", determineProvider(token))
            val provider = determineProvider(token)
            val newUser = UserEntity(
                firebaseUid = token.uid,
                email = token.email ?: "",
                displayName = token.name,
                photoUrl = token.picture,
                provider = provider,
                emailVerified = token.isEmailVerified
            )
            userRepository.save(newUser)
        }
    }

    @Transactional
    fun softDeleteUser(firebaseUid: String) {
        userRepository.findByFirebaseUid(firebaseUid).ifPresent { user ->
            user.deleted = true
            user.email = "deleted_${user.id}@deleted"
            user.displayName = null
            user.photoUrl = null
            userRepository.save(user)
            log.info("Soft-deleted user id={}", user.id)
        }
    }

    private fun determineProvider(token: FirebaseToken): String {
        @Suppress("UNCHECKED_CAST")
        val firebaseClaim = token.claims["firebase"] as? Map<String, Any>
        val signInProvider = firebaseClaim?.get("sign_in_provider") as? String
        return when (signInProvider) {
            "google.com" -> "google"
            "password" -> "password"
            else -> signInProvider ?: "unknown"
        }
    }
}
