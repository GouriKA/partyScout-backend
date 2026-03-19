package com.partyscout.persistence.service

import com.partyscout.persistence.entity.VenueEnrichmentEntity
import com.partyscout.persistence.repository.VenueEnrichmentRepository
import org.springframework.stereotype.Service

@Service
class VenueEnrichmentService(
    private val venueEnrichmentRepository: VenueEnrichmentRepository,
) {
    /**
     * Batch-fetch enrichment for a list of place IDs.
     * Returns a map of placeId → enrichment (only entries that exist in DB).
     * Non-fatal: missing enrichment entries are simply absent from the map.
     */
    fun batchLookup(placeIds: List<String>): Map<String, VenueEnrichmentEntity> {
        if (placeIds.isEmpty()) return emptyMap()
        return venueEnrichmentRepository.findAllByPlaceIdIn(placeIds)
            .associateBy { it.placeId }
    }
}
