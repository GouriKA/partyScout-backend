package com.partyscout.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "party_types")
class PartyTypeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var type: String = "",

    @Column(name = "display_name", nullable = false)
    var displayName: String = "",

    var description: String? = null,

    var icon: String? = null,

    @Column(name = "min_age")
    var minAge: Int? = null,

    @Column(name = "max_age")
    var maxAge: Int? = null,

    @Column(name = "google_places_types")
    var googlePlacesTypes: String? = null,

    @Column(name = "search_keywords")
    var searchKeywords: String? = null,

    @Column(name = "typical_duration")
    var typicalDuration: String? = null,

    @Column(name = "avg_cost_min")
    var avgCostMin: Int? = null,

    @Column(name = "avg_cost_max")
    var avgCostMax: Int? = null,

    var setting: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now()
)
