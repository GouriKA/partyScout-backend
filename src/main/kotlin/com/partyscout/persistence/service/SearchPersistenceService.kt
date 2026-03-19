package com.partyscout.persistence.service

import com.partyscout.party.model.PartySearchRequest
import com.partyscout.persistence.entity.SearchEntity
import com.partyscout.persistence.repository.SearchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SearchPersistenceService(
    private val searchRepository: SearchRepository
) {
    private val logger = LoggerFactory.getLogger(SearchPersistenceService::class.java)

    fun recordSearch(correlationId: String?, request: PartySearchRequest, venueCount: Int) {
        val entity = SearchEntity(
            correlationId = correlationId,
            age = request.age,
            guestCount = request.guestCount,
            budgetMin = request.budgetMin,
            budgetMax = request.budgetMax,
            zipCode = request.city,
            setting = request.setting,
            maxDistanceMiles = request.maxDistanceMiles,
            venueCount = venueCount
        )
        entity.setPartyTypesFromList(request.partyTypes)

        searchRepository.save(entity)
        logger.debug("Recorded search with correlation ID: {}", correlationId)
    }
}
