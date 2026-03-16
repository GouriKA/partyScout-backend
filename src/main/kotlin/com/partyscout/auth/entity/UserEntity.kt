package com.partyscout.auth.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "firebase_uid", nullable = false, unique = true)
    var firebaseUid: String = "",

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(name = "photo_url")
    var photoUrl: String? = null,

    @Column(nullable = false)
    var provider: String = "",

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(nullable = false)
    var deleted: Boolean = false,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_seen_at")
    var lastSeenAt: Instant = Instant.now()
)
