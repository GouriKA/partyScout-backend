package com.partyscout.model

data class PartyOptionsResponse(
    val venueOptions: List<VenueOption>,
    val searchCriteria: SearchCriteria
)

data class VenueOption(
    val id: String,
    val name: String,
    val type: String,
    val address: String,
    val distance: Double,
    val rating: Double,
    val priceLevel: Int,
    val amenities: List<String>,
    val availableCapacity: Int,
    val estimatedCost: Double,
    val description: String
)

data class SearchCriteria(
    val age: Int,
    val areaCode: String,
    val time: String
)
