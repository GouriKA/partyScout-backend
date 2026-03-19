package com.partyscout.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "venue_enrichment")
class VenueEnrichmentEntity(
    @Id
    @Column(name = "place_id")
    val placeId: String,

    @Column(name = "age_range_min")
    val ageRangeMin: Int? = null,

    @Column(name = "age_range_max")
    val ageRangeMax: Int? = null,

    @Column(name = "persona_tags")
    val personaTags: String? = null,

    @Column(name = "under18_welcome")
    val under18Welcome: Boolean? = null,

    @Column(name = "requires_adult")
    val requiresAdult: Boolean? = null,

    @Column(name = "alcohol_premises")
    val alcoholPremises: Boolean? = null,

    @Column(name = "last_enriched")
    val lastEnriched: Instant = Instant.now(),
)
