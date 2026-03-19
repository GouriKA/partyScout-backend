package com.partyscout.persistence.repository

import com.partyscout.persistence.entity.VenueEnrichmentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface VenueEnrichmentRepository : JpaRepository<VenueEnrichmentEntity, String> {
    fun findAllByPlaceIdIn(placeIds: List<String>): List<VenueEnrichmentEntity>
}
