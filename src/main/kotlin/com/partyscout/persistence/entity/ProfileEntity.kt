package com.partyscout.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "profiles")
class ProfileEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,

    @Column(nullable = true)
    var name: String? = null,

    @Column(nullable = false)
    var age: Int = 0,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now()
)
