package com.partyscout.shared.event

class VenueSearchedEvent(
    correlationId: String?,
    val city: String,
    val age: Int,
    val partyTypes: List<String>,
    val guestCount: Int,
    val venueCount: Int
) : DomainEvent(
    eventType = "VenueSearched",
    correlationId = correlationId,
    aggregateType = "Search",
    aggregateId = correlationId ?: "unknown"
)

class BudgetEstimatedEvent(
    correlationId: String?,
    val partyTypes: List<String>,
    val guestCount: Int,
    val priceLevel: Int?,
    val estimatedTotal: Int,
    val estimatedPerPerson: Int
) : DomainEvent(
    eventType = "BudgetEstimated",
    correlationId = correlationId,
    aggregateType = "Budget",
    aggregateId = correlationId ?: "unknown"
)
