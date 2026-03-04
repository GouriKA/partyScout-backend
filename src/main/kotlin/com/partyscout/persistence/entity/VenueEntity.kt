package com.partyscout.persistence.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "venues")
class VenueEntity(
    @Id
    @Column(name = "google_place_id")
    var googlePlaceId: String = "",

    @Column(nullable = false)
    var name: String = "",

    var address: String? = null,

    var latitude: Double? = null,

    var longitude: Double? = null,

    var rating: Double? = null,

    @Column(name = "user_ratings_total")
    var userRatingsTotal: Int? = null,

    @Column(name = "price_level")
    var priceLevel: Int? = null,

    @Column(name = "place_types")
    var placeTypes: String? = null,

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    var website: String? = null,

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
) {
    fun setPlaceTypesFromList(types: List<String>) {
        placeTypes = types.joinToString(",")
    }

    fun getPlaceTypesAsList(): List<String> {
        return placeTypes?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
}
