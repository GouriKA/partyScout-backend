package com.partyscout.persistence.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "saved_events")
class SavedEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(name = "profile_id")
    var profileId: Long? = null,

    @Column(name = "google_place_id", nullable = false)
    var googlePlaceId: String = "",

    @Column(name = "venue_name", nullable = false)
    var venueName: String = "",

    @Column(name = "event_date")
    var eventDate: LocalDate? = null,

    @Column(name = "party_types")
    var partyTypes: String? = null,

    @Column(name = "guest_count")
    var guestCount: Int? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now()
)
