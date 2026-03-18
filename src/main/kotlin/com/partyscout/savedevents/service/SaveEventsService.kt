package com.partyscout.savedevents.service

import com.partyscout.persistence.entity.ProfileEntity
import com.partyscout.persistence.entity.SavedEventEntity
import com.partyscout.persistence.repository.ProfileRepository
import com.partyscout.persistence.repository.SavedEventRepository
import com.partyscout.savedevents.controller.CreateProfileRequest
import com.partyscout.savedevents.controller.MergeItem
import com.partyscout.savedevents.controller.ProfileResponse
import com.partyscout.savedevents.controller.SaveEventRequest
import com.partyscout.savedevents.controller.SavedEventResponse
import com.partyscout.savedevents.controller.SavedEventsResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class SaveEventsService(
    private val savedEventRepository: SavedEventRepository,
    private val profileRepository: ProfileRepository,
) {

    fun getProfiles(userId: Long): List<ProfileResponse> =
        profileRepository.findByUserId(userId).map { it.toResponse() }

    @Transactional
    fun createProfile(userId: Long, request: CreateProfileRequest): ProfileResponse {
        val profile = ProfileEntity(userId = userId, name = request.name, age = request.age)
        return profileRepository.save(profile).toResponse()
    }

    @Transactional
    fun deleteProfile(userId: Long, profileId: Long) {
        val profile = profileRepository.findById(profileId)
            .orElseThrow { NoSuchElementException("Profile not found") }
        require(profile.userId == userId) { "Not authorized" }
        profileRepository.delete(profile)
    }

    fun getSavedEvents(userId: Long): SavedEventsResponse {
        val events = savedEventRepository.findByUserId(userId)
        val profiles = profileRepository.findByUserId(userId)
        return SavedEventsResponse(
            savedEvents = events.map { it.toResponse() },
            profiles = profiles.map { it.toResponse() }
        )
    }

    @Transactional
    fun saveEvent(userId: Long, request: SaveEventRequest): SavedEventResponse {
        val event = SavedEventEntity(
            userId = userId,
            profileId = request.profileId,
            googlePlaceId = request.googlePlaceId,
            venueName = request.venueName,
            eventDate = request.eventDate?.let { LocalDate.parse(it) },
            partyTypes = request.partyTypes,
            guestCount = request.guestCount
        )
        return savedEventRepository.save(event).toResponse()
    }

    @Transactional
    fun unsaveEvent(userId: Long, savedEventId: Long) {
        val event = savedEventRepository.findById(savedEventId)
            .orElseThrow { NoSuchElementException("Saved event not found") }
        require(event.userId == userId) { "Not authorized" }
        savedEventRepository.delete(event)
    }

    @Transactional
    fun mergeGuestData(userId: Long, items: List<MergeItem>): SavedEventsResponse {
        for (item in items) {
            val profileId: Long? = if (item.profileAge != null) {
                val existing = profileRepository.findByUserIdAndNameAndAge(
                    userId, item.profileName, item.profileAge
                )
                val profile = existing ?: profileRepository.save(
                    ProfileEntity(userId = userId, name = item.profileName, age = item.profileAge)
                )
                profile.id
            } else null

            val alreadyExists = savedEventRepository.existsByUserIdAndProfileIdAndGooglePlaceId(
                userId, profileId, item.googlePlaceId
            )
            if (!alreadyExists) {
                savedEventRepository.save(
                    SavedEventEntity(
                        userId = userId,
                        profileId = profileId,
                        googlePlaceId = item.googlePlaceId,
                        venueName = item.venueName,
                        eventDate = item.eventDate?.let { LocalDate.parse(it) },
                        partyTypes = item.partyTypes,
                        guestCount = item.guestCount
                    )
                )
            }
        }
        return getSavedEvents(userId)
    }

    private fun ProfileEntity.toResponse() = ProfileResponse(id = id!!, name = name, age = age)

    private fun SavedEventEntity.toResponse() = SavedEventResponse(
        id = id!!,
        profileId = profileId,
        googlePlaceId = googlePlaceId,
        venueName = venueName,
        eventDate = eventDate?.toString(),
        partyTypes = partyTypes,
        guestCount = guestCount,
        createdAt = createdAt.toString()
    )
}
