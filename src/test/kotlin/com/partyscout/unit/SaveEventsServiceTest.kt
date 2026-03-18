package com.partyscout.unit

import com.partyscout.persistence.entity.ProfileEntity
import com.partyscout.persistence.entity.SavedEventEntity
import com.partyscout.persistence.repository.ProfileRepository
import com.partyscout.persistence.repository.SavedEventRepository
import com.partyscout.savedevents.controller.CreateProfileRequest
import com.partyscout.savedevents.controller.MergeItem
import com.partyscout.savedevents.controller.SaveEventRequest
import com.partyscout.savedevents.service.SaveEventsService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

@DisplayName("SaveEventsService")
class SaveEventsServiceTest {

    private lateinit var saveEventsService: SaveEventsService
    private lateinit var savedEventRepository: SavedEventRepository
    private lateinit var profileRepository: ProfileRepository

    @BeforeEach
    fun setUp() {
        savedEventRepository = mockk(relaxed = true)
        profileRepository = mockk(relaxed = true)
        saveEventsService = SaveEventsService(savedEventRepository, profileRepository)
    }

    private fun makeProfile(id: Long, userId: Long, name: String? = "Emma", age: Int = 7) =
        ProfileEntity(id = id, userId = userId, name = name, age = age, createdAt = Instant.now())

    private fun makeEvent(id: Long, userId: Long, placeId: String = "place-1", profileId: Long? = null) =
        SavedEventEntity(
            id = id,
            userId = userId,
            profileId = profileId,
            googlePlaceId = placeId,
            venueName = "Sky Zone",
            createdAt = Instant.now()
        )

    // ── getProfiles ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProfiles")
    inner class GetProfiles {

        @Test
        @DisplayName("should return mapped list of profiles for user")
        fun shouldReturnMappedProfileList() {
            // Given
            val userId = 1L
            val profiles = listOf(
                makeProfile(id = 10L, userId = userId, name = "Emma", age = 7),
                makeProfile(id = 11L, userId = userId, name = "Liam", age = 5)
            )
            every { profileRepository.findByUserId(userId) } returns profiles

            // When
            val result = saveEventsService.getProfiles(userId)

            // Then
            assertEquals(2, result.size)
            assertEquals(10L, result[0].id)
            assertEquals("Emma", result[0].name)
            assertEquals(7, result[0].age)
            assertEquals(11L, result[1].id)
            assertEquals("Liam", result[1].name)
            verify { profileRepository.findByUserId(userId) }
        }

        @Test
        @DisplayName("should return empty list when user has no profiles")
        fun shouldReturnEmptyListWhenNoProfiles() {
            // Given
            val userId = 42L
            every { profileRepository.findByUserId(userId) } returns emptyList()

            // When
            val result = saveEventsService.getProfiles(userId)

            // Then
            assertTrue(result.isEmpty())
        }
    }

    // ── createProfile ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createProfile")
    inner class CreateProfile {

        @Test
        @DisplayName("should create and return profile")
        fun shouldCreateAndReturnProfile() {
            // Given
            val userId = 1L
            val request = CreateProfileRequest(name = "Emma", age = 7)
            val saved = makeProfile(id = 20L, userId = userId, name = "Emma", age = 7)
            every { profileRepository.save(any()) } returns saved

            // When
            val result = saveEventsService.createProfile(userId, request)

            // Then
            assertEquals(20L, result.id)
            assertEquals("Emma", result.name)
            assertEquals(7, result.age)
            verify { profileRepository.save(match { it.userId == userId && it.name == "Emma" && it.age == 7 }) }
        }

        @Test
        @DisplayName("should create profile without name")
        fun shouldCreateProfileWithoutName() {
            // Given
            val userId = 1L
            val request = CreateProfileRequest(name = null, age = 5)
            val saved = makeProfile(id = 21L, userId = userId, name = null, age = 5)
            every { profileRepository.save(any()) } returns saved

            // When
            val result = saveEventsService.createProfile(userId, request)

            // Then
            assertNull(result.name)
            assertEquals(5, result.age)
        }
    }

    // ── deleteProfile ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteProfile")
    inner class DeleteProfile {

        @Test
        @DisplayName("should throw NoSuchElementException when profile not found")
        fun shouldThrowWhenProfileNotFound() {
            // Given
            val userId = 1L
            val profileId = 999L
            every { profileRepository.findById(profileId) } returns Optional.empty()

            // When & Then
            assertThrows(NoSuchElementException::class.java) {
                saveEventsService.deleteProfile(userId, profileId)
            }
            verify(exactly = 0) { profileRepository.delete(any()) }
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when userId does not match")
        fun shouldThrowWhenUserIdMismatch() {
            // Given
            val userId = 1L
            val otherUserId = 2L
            val profileId = 10L
            val profile = makeProfile(id = profileId, userId = otherUserId)
            every { profileRepository.findById(profileId) } returns Optional.of(profile)

            // When & Then
            assertThrows(IllegalArgumentException::class.java) {
                saveEventsService.deleteProfile(userId, profileId)
            }
            verify(exactly = 0) { profileRepository.delete(any()) }
        }

        @Test
        @DisplayName("should delete profile when authorized")
        fun shouldDeleteWhenAuthorized() {
            // Given
            val userId = 1L
            val profileId = 10L
            val profile = makeProfile(id = profileId, userId = userId)
            every { profileRepository.findById(profileId) } returns Optional.of(profile)
            every { profileRepository.delete(profile) } just Runs

            // When
            saveEventsService.deleteProfile(userId, profileId)

            // Then
            verify { profileRepository.delete(profile) }
        }
    }

    // ── getSavedEvents ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSavedEvents")
    inner class GetSavedEvents {

        @Test
        @DisplayName("should return saved events and profiles grouped for user")
        fun shouldReturnEventsAndProfiles() {
            // Given
            val userId = 1L
            val events = listOf(
                makeEvent(id = 100L, userId = userId, placeId = "place-1"),
                makeEvent(id = 101L, userId = userId, placeId = "place-2", profileId = 10L)
            )
            val profiles = listOf(makeProfile(id = 10L, userId = userId))
            every { savedEventRepository.findByUserId(userId) } returns events
            every { profileRepository.findByUserId(userId) } returns profiles

            // When
            val result = saveEventsService.getSavedEvents(userId)

            // Then
            assertEquals(2, result.savedEvents.size)
            assertEquals(1, result.profiles.size)
            assertEquals("place-1", result.savedEvents[0].googlePlaceId)
            assertEquals(10L, result.profiles[0].id)
        }
    }

    // ── saveEvent ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveEvent")
    inner class SaveEvent {

        @Test
        @DisplayName("should save and return event")
        fun shouldSaveAndReturnEvent() {
            // Given
            val userId = 1L
            val request = SaveEventRequest(
                googlePlaceId = "ChIJN1t_tDeuEmsRUsoyG83frY4",
                venueName = "Sky Zone",
                profileId = null,
                eventDate = null,
                partyTypes = "active_play"
            )
            val saved = makeEvent(id = 200L, userId = userId, placeId = request.googlePlaceId)
            every { savedEventRepository.save(any()) } returns saved

            // When
            val result = saveEventsService.saveEvent(userId, request)

            // Then
            assertEquals(200L, result.id)
            assertEquals(request.googlePlaceId, result.googlePlaceId)
            verify { savedEventRepository.save(match { it.userId == userId && it.googlePlaceId == request.googlePlaceId }) }
        }

        @Test
        @DisplayName("should save event with profileId and eventDate")
        fun shouldSaveEventWithProfileIdAndDate() {
            // Given
            val userId = 1L
            val request = SaveEventRequest(
                googlePlaceId = "place-abc",
                venueName = "Chuck E. Cheese",
                profileId = 10L,
                eventDate = "2026-06-15",
                partyTypes = null
            )
            val saved = makeEvent(id = 201L, userId = userId, placeId = "place-abc", profileId = 10L)
            every { savedEventRepository.save(any()) } returns saved

            // When
            val result = saveEventsService.saveEvent(userId, request)

            // Then
            assertEquals(10L, result.profileId)
        }
    }

    // ── unsaveEvent ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unsaveEvent")
    inner class UnsaveEvent {

        @Test
        @DisplayName("should throw NoSuchElementException when event not found")
        fun shouldThrowWhenEventNotFound() {
            // Given
            val userId = 1L
            val savedEventId = 999L
            every { savedEventRepository.findById(savedEventId) } returns Optional.empty()

            // When & Then
            assertThrows(NoSuchElementException::class.java) {
                saveEventsService.unsaveEvent(userId, savedEventId)
            }
            verify(exactly = 0) { savedEventRepository.delete(any()) }
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when userId does not match")
        fun shouldThrowWhenUserIdMismatch() {
            // Given
            val userId = 1L
            val otherUserId = 2L
            val savedEventId = 100L
            val event = makeEvent(id = savedEventId, userId = otherUserId)
            every { savedEventRepository.findById(savedEventId) } returns Optional.of(event)

            // When & Then
            assertThrows(IllegalArgumentException::class.java) {
                saveEventsService.unsaveEvent(userId, savedEventId)
            }
            verify(exactly = 0) { savedEventRepository.delete(any()) }
        }

        @Test
        @DisplayName("should delete event when authorized")
        fun shouldDeleteWhenAuthorized() {
            // Given
            val userId = 1L
            val savedEventId = 100L
            val event = makeEvent(id = savedEventId, userId = userId)
            every { savedEventRepository.findById(savedEventId) } returns Optional.of(event)
            every { savedEventRepository.delete(event) } just Runs

            // When
            saveEventsService.unsaveEvent(userId, savedEventId)

            // Then
            verify { savedEventRepository.delete(event) }
        }
    }

    // ── mergeGuestData ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("mergeGuestData")
    inner class MergeGuestData {

        @Test
        @DisplayName("should skip duplicate events that already exist")
        fun shouldSkipDuplicates() {
            // Given
            val userId = 1L
            val item = MergeItem(
                googlePlaceId = "place-1",
                venueName = "Sky Zone",
                profileName = null,
                profileAge = null
            )
            every { savedEventRepository.existsByUserIdAndProfileIdAndGooglePlaceId(userId, null, "place-1") } returns true
            every { savedEventRepository.findByUserId(userId) } returns emptyList()
            every { profileRepository.findByUserId(userId) } returns emptyList()

            // When
            saveEventsService.mergeGuestData(userId, listOf(item))

            // Then
            verify(exactly = 0) { savedEventRepository.save(any()) }
        }

        @Test
        @DisplayName("should create new profile when profileAge is provided and profile does not exist")
        fun shouldCreateNewProfileWhenNotExisting() {
            // Given
            val userId = 1L
            val item = MergeItem(
                googlePlaceId = "place-2",
                venueName = "Bounce World",
                profileName = "Emma",
                profileAge = 7
            )
            val newProfile = makeProfile(id = 30L, userId = userId, name = "Emma", age = 7)
            every { profileRepository.findByUserIdAndNameAndAge(userId, "Emma", 7) } returns null
            every { profileRepository.save(any<ProfileEntity>()) } returns newProfile
            every { savedEventRepository.existsByUserIdAndProfileIdAndGooglePlaceId(userId, 30L, "place-2") } returns false
            every { savedEventRepository.save(any<SavedEventEntity>()) } returns makeEvent(id = 300L, userId = userId, placeId = "place-2", profileId = 30L)
            every { savedEventRepository.findByUserId(userId) } returns emptyList()
            every { profileRepository.findByUserId(userId) } returns listOf(newProfile)

            // When
            saveEventsService.mergeGuestData(userId, listOf(item))

            // Then
            verify { profileRepository.save(match { it.name == "Emma" && it.age == 7 }) }
            verify { savedEventRepository.save(match { it.googlePlaceId == "place-2" && it.profileId == 30L }) }
        }

        @Test
        @DisplayName("should reuse existing profile when it already exists")
        fun shouldReuseExistingProfile() {
            // Given
            val userId = 1L
            val existingProfile = makeProfile(id = 10L, userId = userId, name = "Liam", age = 5)
            val item = MergeItem(
                googlePlaceId = "place-3",
                venueName = "Jump Zone",
                profileName = "Liam",
                profileAge = 5
            )
            every { profileRepository.findByUserIdAndNameAndAge(userId, "Liam", 5) } returns existingProfile
            every { savedEventRepository.existsByUserIdAndProfileIdAndGooglePlaceId(userId, 10L, "place-3") } returns false
            every { savedEventRepository.save(any<SavedEventEntity>()) } returns makeEvent(id = 301L, userId = userId, placeId = "place-3", profileId = 10L)
            every { savedEventRepository.findByUserId(userId) } returns emptyList()
            every { profileRepository.findByUserId(userId) } returns listOf(existingProfile)

            // When
            saveEventsService.mergeGuestData(userId, listOf(item))

            // Then
            // Should not create a new profile — only one save call, and it's for the event
            verify(exactly = 0) { profileRepository.save(any()) }
            verify { savedEventRepository.save(match { it.profileId == 10L }) }
        }

        @Test
        @DisplayName("should save new event when it does not already exist")
        fun shouldSaveNewEventWhenNotDuplicate() {
            // Given
            val userId = 1L
            val item = MergeItem(
                googlePlaceId = "place-new",
                venueName = "Fun Land",
                profileName = null,
                profileAge = null
            )
            every { savedEventRepository.existsByUserIdAndProfileIdAndGooglePlaceId(userId, null, "place-new") } returns false
            every { savedEventRepository.save(any<SavedEventEntity>()) } returns makeEvent(id = 302L, userId = userId, placeId = "place-new")
            every { savedEventRepository.findByUserId(userId) } returns emptyList()
            every { profileRepository.findByUserId(userId) } returns emptyList()

            // When
            saveEventsService.mergeGuestData(userId, listOf(item))

            // Then
            verify { savedEventRepository.save(match { it.googlePlaceId == "place-new" && it.userId == userId }) }
        }
    }
}
