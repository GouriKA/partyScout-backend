package com.partyscout.auth.controller

import com.google.firebase.auth.FirebaseToken
import com.partyscout.auth.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v2/auth")
class AuthController(private val userService: UserService) {

    /** Called after sign-in to get/create the user profile */
    @PostMapping("/me")
    fun getOrCreateProfile(
        @AuthenticationPrincipal token: FirebaseToken
    ): ResponseEntity<UserProfileResponse> {
        val user = userService.getOrCreateUser(token)
        return ResponseEntity.ok(
            UserProfileResponse(
                id = user.id!!,
                email = user.email,
                displayName = user.displayName,
                photoUrl = user.photoUrl,
                provider = user.provider,
                emailVerified = user.emailVerified
            )
        )
    }

    /** GDPR erasure — soft-deletes the authenticated user's data */
    @DeleteMapping("/me")
    fun deleteAccount(
        @AuthenticationPrincipal token: FirebaseToken
    ): ResponseEntity<Void> {
        userService.softDeleteUser(token.uid)
        return ResponseEntity.noContent().build()
    }
}

data class UserProfileResponse(
    val id: Long,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val provider: String,
    val emailVerified: Boolean
)
