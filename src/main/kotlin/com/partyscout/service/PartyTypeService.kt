package com.partyscout.service

import com.partyscout.model.PartyTypeSuggestion
import com.partyscout.model.PartyTypeTaxonomy
import org.springframework.stereotype.Service

@Service
class PartyTypeService {

    private val partyTypeTaxonomy: List<PartyTypeTaxonomy> = listOf(
        // Toddler-friendly (Ages 1-4)
        PartyTypeTaxonomy(
            type = "toddler_play",
            displayName = "Toddler Play",
            description = "Safe, soft play areas perfect for little ones",
            icon = "baby",
            minAge = 1,
            maxAge = 4,
            googlePlacesTypes = listOf("playground", "amusement_center"),
            searchKeywords = listOf("soft play", "indoor playground", "play cafe", "toddler gym"),
            typicalDuration = "1.5 hours",
            averageCostPerPerson = 15..30,
            setting = "indoor"
        ),
        PartyTypeTaxonomy(
            type = "character_party",
            displayName = "Character Party",
            description = "Meet favorite characters for magical moments",
            icon = "sparkles",
            minAge = 2,
            maxAge = 6,
            googlePlacesTypes = listOf("amusement_center", "event_venue"),
            searchKeywords = listOf("character party", "princess party", "superhero party"),
            typicalDuration = "2 hours",
            averageCostPerPerson = 25..50,
            setting = "both"
        ),

        // Young kids (Ages 4-12)
        PartyTypeTaxonomy(
            type = "bounce_house",
            displayName = "Bounce House",
            description = "Jump, bounce, and burn energy at trampoline parks",
            icon = "rocket",
            minAge = 4,
            maxAge = 12,
            googlePlacesTypes = listOf("amusement_center", "gym"),
            searchKeywords = listOf("trampoline park", "bounce house", "jump zone", "inflatable park"),
            typicalDuration = "2 hours",
            averageCostPerPerson = 20..40,
            setting = "indoor"
        ),
        PartyTypeTaxonomy(
            type = "arcade",
            displayName = "Arcade Gaming",
            description = "Classic games, prizes, and pizza fun",
            icon = "gamepad",
            minAge = 5,
            maxAge = 14,
            googlePlacesTypes = listOf("amusement_center"),
            searchKeywords = listOf("arcade", "game center", "chuck e cheese", "dave and busters"),
            typicalDuration = "2 hours",
            averageCostPerPerson = 25..50,
            setting = "indoor"
        ),
        PartyTypeTaxonomy(
            type = "sports",
            displayName = "Sports Active",
            description = "Active fun with bowling, laser tag, or sports",
            icon = "trophy",
            minAge = 6,
            maxAge = 16,
            googlePlacesTypes = listOf("bowling_alley", "gym", "sports_complex"),
            searchKeywords = listOf("bowling", "laser tag", "sports center", "batting cages"),
            typicalDuration = "2 hours",
            averageCostPerPerson = 20..45,
            setting = "indoor"
        ),
        PartyTypeTaxonomy(
            type = "arts_crafts",
            displayName = "Arts & Crafts",
            description = "Creative parties with painting, pottery, or crafts",
            icon = "palette",
            minAge = 5,
            maxAge = 14,
            googlePlacesTypes = listOf("art_studio"),
            searchKeywords = listOf("pottery painting", "art studio", "craft party", "painting party"),
            typicalDuration = "2 hours",
            averageCostPerPerson = 25..45,
            setting = "indoor"
        ),
        PartyTypeTaxonomy(
            type = "outdoor",
            displayName = "Outdoor Adventure",
            description = "Parks, zoos, and nature-based celebrations",
            icon = "tree",
            minAge = 5,
            maxAge = 14,
            googlePlacesTypes = listOf("park", "zoo", "amusement_park"),
            searchKeywords = listOf("park pavilion", "nature center", "zoo", "botanical garden"),
            typicalDuration = "3 hours",
            averageCostPerPerson = 10..35,
            setting = "outdoor"
        ),

        // Tweens and Teens (Ages 10-18)
        PartyTypeTaxonomy(
            type = "escape_room",
            displayName = "Escape Room",
            description = "Team puzzles and mystery-solving adventures",
            icon = "key",
            minAge = 10,
            maxAge = 18,
            googlePlacesTypes = listOf("amusement_center"),
            searchKeywords = listOf("escape room", "puzzle room", "VR experience", "virtual reality"),
            typicalDuration = "1.5 hours",
            averageCostPerPerson = 30..50,
            setting = "indoor"
        ),
        PartyTypeTaxonomy(
            type = "movies",
            displayName = "Movie Theater",
            description = "Private screening with popcorn and friends",
            icon = "film",
            minAge = 6,
            maxAge = 18,
            googlePlacesTypes = listOf("movie_theater"),
            searchKeywords = listOf("movie theater", "cinema", "private screening"),
            typicalDuration = "2.5 hours",
            averageCostPerPerson = 15..35,
            setting = "indoor"
        ),
        PartyTypeTaxonomy(
            type = "pool_party",
            displayName = "Pool Party",
            description = "Splash and swim at water parks or pools",
            icon = "waves",
            minAge = 5,
            maxAge = 16,
            googlePlacesTypes = listOf("amusement_park", "swimming_pool"),
            searchKeywords = listOf("water park", "swim party", "pool party", "aquatic center"),
            typicalDuration = "3 hours",
            averageCostPerPerson = 20..45,
            setting = "both"
        ),
        PartyTypeTaxonomy(
            type = "go_karts",
            displayName = "Go-Karts",
            description = "Racing fun for speed-loving kids",
            icon = "car",
            minAge = 8,
            maxAge = 18,
            googlePlacesTypes = listOf("amusement_center"),
            searchKeywords = listOf("go kart", "racing", "speedway", "karting"),
            typicalDuration = "2 hours",
            averageCostPerPerson = 30..60,
            setting = "both"
        ),
        PartyTypeTaxonomy(
            type = "adventure_park",
            displayName = "Adventure Park",
            description = "Climbing, zip lines, and outdoor challenges",
            icon = "mountain",
            minAge = 8,
            maxAge = 18,
            googlePlacesTypes = listOf("amusement_park", "park"),
            searchKeywords = listOf("adventure park", "climbing", "zip line", "ropes course"),
            typicalDuration = "3 hours",
            averageCostPerPerson = 35..65,
            setting = "outdoor"
        )
    )

    /**
     * Get party type suggestions appropriate for a given age
     */
    fun getPartyTypesForAge(age: Int): List<PartyTypeSuggestion> {
        return partyTypeTaxonomy
            .filter { age in it.minAge..it.maxAge }
            .sortedByDescending { calculatePopularityForAge(it, age) }
            .map { taxonomy ->
                PartyTypeSuggestion(
                    type = taxonomy.type,
                    displayName = taxonomy.displayName,
                    description = taxonomy.description,
                    icon = taxonomy.icon,
                    ageRange = "Ages ${taxonomy.minAge}-${taxonomy.maxAge}",
                    averageCost = "$${taxonomy.averageCostPerPerson.first * 10}-${taxonomy.averageCostPerPerson.last * 10}",
                    popularityScore = calculatePopularityForAge(taxonomy, age)
                )
            }
    }

    /**
     * Get Google Places search keywords for a party type
     */
    fun getKeywordsForPartyType(type: String): List<String> {
        return partyTypeTaxonomy
            .find { it.type == type }
            ?.searchKeywords
            ?: emptyList()
    }

    /**
     * Get Google Places types for a party type
     */
    fun getGooglePlacesTypesForPartyType(type: String): List<String> {
        return partyTypeTaxonomy
            .find { it.type == type }
            ?.googlePlacesTypes
            ?: emptyList()
    }

    /**
     * Get full taxonomy entry for a party type
     */
    fun getTaxonomyForType(type: String): PartyTypeTaxonomy? {
        return partyTypeTaxonomy.find { it.type == type }
    }

    /**
     * Get all party types for multiple selections
     */
    fun getAllPartyTypes(): List<PartyTypeTaxonomy> = partyTypeTaxonomy

    /**
     * Get typical duration for a party type
     */
    fun getTypicalDuration(type: String): String {
        return partyTypeTaxonomy
            .find { it.type == type }
            ?.typicalDuration
            ?: "2 hours"
    }

    /**
     * Get setting (indoor/outdoor/both) for a party type
     */
    fun getSetting(type: String): String {
        return partyTypeTaxonomy
            .find { it.type == type }
            ?.setting
            ?: "indoor"
    }

    /**
     * Calculate popularity score (1-5) based on how well the party type fits the age
     */
    private fun calculatePopularityForAge(taxonomy: PartyTypeTaxonomy, age: Int): Int {
        val midpoint = (taxonomy.minAge + taxonomy.maxAge) / 2.0
        val range = (taxonomy.maxAge - taxonomy.minAge) / 2.0
        val distanceFromMidpoint = kotlin.math.abs(age - midpoint)
        val normalizedDistance = distanceFromMidpoint / range

        return when {
            normalizedDistance <= 0.3 -> 5 // Very close to ideal age
            normalizedDistance <= 0.5 -> 4
            normalizedDistance <= 0.7 -> 3
            normalizedDistance <= 0.9 -> 2
            else -> 1
        }
    }

    /**
     * Combine keywords from multiple party types
     */
    fun getKeywordsForPartyTypes(types: List<String>): List<String> {
        return types.flatMap { getKeywordsForPartyType(it) }.distinct()
    }

    /**
     * Combine Google Places types from multiple party types
     */
    fun getGooglePlacesTypesForPartyTypes(types: List<String>): List<String> {
        return types.flatMap { getGooglePlacesTypesForPartyType(it) }.distinct()
    }
}
