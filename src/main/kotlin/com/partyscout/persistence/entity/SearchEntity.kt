package com.partyscout.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "searches")
class SearchEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "correlation_id")
    var correlationId: String? = null,

    @Column(nullable = false)
    var age: Int = 0,

    @Column(name = "party_types")
    var partyTypes: String? = null,

    @Column(name = "guest_count")
    var guestCount: Int? = null,

    @Column(name = "budget_min")
    var budgetMin: Int? = null,

    @Column(name = "budget_max")
    var budgetMax: Int? = null,

    @Column(name = "zip_code")
    var zipCode: String? = null,

    var setting: String? = null,

    @Column(name = "max_distance_miles")
    var maxDistanceMiles: Int? = null,

    @Column(name = "venue_count")
    var venueCount: Int? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now()
) {
    fun setPartyTypesFromList(types: List<String>) {
        partyTypes = types.joinToString(",")
    }

    fun getPartyTypesAsList(): List<String> {
        return partyTypes?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
}
