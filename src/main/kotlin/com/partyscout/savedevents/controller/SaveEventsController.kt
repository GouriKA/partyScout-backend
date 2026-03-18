package com.partyscout.savedevents.controller

import com.google.firebase.auth.FirebaseToken
import com.partyscout.auth.repository.UserRepository
import com.partyscout.savedevents.service.SaveEventsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v2")
class SaveEventsController(
    private val saveEventsService: SaveEventsService,
    private val userRepository: UserRepository,
) {
    private fun resolveUserId(token: FirebaseToken): Long =
        userRepository.findByFirebaseUid(token.uid)
            .orElseThrow { NoSuchElementException("User not found — call POST /api/v2/auth/me first") }
            .id!!

    // ── Profiles ────────────────────────────────────────────────────────────

    @GetMapping("/profiles")
    fun listProfiles(@AuthenticationPrincipal token: FirebaseToken): ResponseEntity<List<ProfileResponse>> =
        ResponseEntity.ok(saveEventsService.getProfiles(resolveUserId(token)))

    @PostMapping("/profiles")
    fun createProfile(
        @AuthenticationPrincipal token: FirebaseToken,
        @RequestBody request: CreateProfileRequest,
    ): ResponseEntity<ProfileResponse> =
        ResponseEntity.ok(saveEventsService.createProfile(resolveUserId(token), request))

    @DeleteMapping("/profiles/{id}")
    fun deleteProfile(
        @AuthenticationPrincipal token: FirebaseToken,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        saveEventsService.deleteProfile(resolveUserId(token), id)
        return ResponseEntity.noContent().build()
    }

    // ── Saved Events ─────────────────────────────────────────────────────────

    @GetMapping("/saved-events")
    fun listSavedEvents(@AuthenticationPrincipal token: FirebaseToken): ResponseEntity<SavedEventsResponse> =
        ResponseEntity.ok(saveEventsService.getSavedEvents(resolveUserId(token)))

    @PostMapping("/saved-events")
    fun saveEvent(
        @AuthenticationPrincipal token: FirebaseToken,
        @RequestBody request: SaveEventRequest,
    ): ResponseEntity<SavedEventResponse> =
        ResponseEntity.ok(saveEventsService.saveEvent(resolveUserId(token), request))

    @DeleteMapping("/saved-events/{id}")
    fun unsaveEvent(
        @AuthenticationPrincipal token: FirebaseToken,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        saveEventsService.unsaveEvent(resolveUserId(token), id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/saved-events/merge")
    fun mergeGuestData(
        @AuthenticationPrincipal token: FirebaseToken,
        @RequestBody items: List<MergeItem>,
    ): ResponseEntity<SavedEventsResponse> =
        ResponseEntity.ok(saveEventsService.mergeGuestData(resolveUserId(token), items))
}

// ── DTOs ─────────────────────────────────────────────────────────────────────

data class ProfileResponse(val id: Long, val name: String?, val age: Int)

data class CreateProfileRequest(val name: String? = null, val age: Int)

data class SaveEventRequest(
    val googlePlaceId: String,
    val venueName: String,
    val profileId: Long? = null,
    val eventDate: String? = null,
    val partyTypes: String? = null,
)

data class SavedEventResponse(
    val id: Long,
    val profileId: Long?,
    val googlePlaceId: String,
    val venueName: String,
    val eventDate: String?,
    val partyTypes: String?,
    val createdAt: String,
)

data class SavedEventsResponse(
    val savedEvents: List<SavedEventResponse>,
    val profiles: List<ProfileResponse>,
)

data class MergeItem(
    val googlePlaceId: String,
    val venueName: String,
    val profileName: String? = null,
    val profileAge: Int? = null,
    val eventDate: String? = null,
    val partyTypes: String? = null,
)
