package com.partyscout.service

import com.partyscout.model.PartyTypeSuggestion
import com.partyscout.model.PartyTypeTaxonomy
import org.springframework.stereotype.Service

@Service
class PartyTypeService {

    private val partyTypeTaxonomy: List<PartyTypeTaxonomy> = listOf(
        // Active Play - Physical activities, sports, movement
        PartyTypeTaxonomy(
            type = "active_play",
            displayName = "Active Play",
            description = "Jump, run, and burn energy with physical fun",
            icon = "rocket",
            minAge = 3,
            maxAge = 16,
            googlePlacesTypes = listOf("amusement_center", "gym", "bowling_alley", "swimming_pool"),
            searchKeywords = listOf(
                "trampoline park", "bounce house", "jump zone", "gymnastics",
                "skating rink", "roller skating", "ice skating", "ninja warrior",
                "obstacle course", "rock climbing", "sports center", "swim party",
                "pool party", "aquatic center"
            ),
            typicalDuration = "2 hours",
            averageCostPerPerson = 20..45,
            setting = "indoor"
        ),

        // Creative - Arts, crafts, hands-on making
        PartyTypeTaxonomy(
            type = "creative",
            displayName = "Creative",
            description = "Arts, crafts, cooking, and hands-on activities",
            icon = "palette",
            minAge = 4,
            maxAge = 14,
            googlePlacesTypes = listOf("art_studio", "museum"),
            searchKeywords = listOf(
                "art studio", "pottery painting", "craft party", "painting party",
                "cooking class", "baking party", "science center", "STEM party",
                "slime party", "jewelry making", "canvas painting"
            ),
            typicalDuration = "2 hours",
            averageCostPerPerson = 25..50,
            setting = "indoor"
        ),

        // Amusement - Games, movies, competitive fun
        PartyTypeTaxonomy(
            type = "amusement",
            displayName = "Amusement",
            description = "Arcades, movies, escape rooms, and games galore",
            icon = "gamepad",
            minAge = 5,
            maxAge = 18,
            googlePlacesTypes = listOf("amusement_center", "movie_theater", "bowling_alley"),
            searchKeywords = listOf(
                "arcade", "game center", "chuck e cheese", "dave and busters",
                "movie theater", "cinema", "private screening",
                "escape room", "puzzle room", "VR experience", "virtual reality",
                "bowling", "laser tag", "go kart", "racing", "mini golf", "putt putt"
            ),
            typicalDuration = "2 hours",
            averageCostPerPerson = 25..55,
            setting = "indoor"
        ),

        // Outdoor - Nature, parks, open-air activities
        PartyTypeTaxonomy(
            type = "outdoor",
            displayName = "Outdoor",
            description = "Parks, zoos, farms, and nature adventures",
            icon = "tree",
            minAge = 3,
            maxAge = 16,
            googlePlacesTypes = listOf("park", "zoo", "amusement_park", "campground"),
            searchKeywords = listOf(
                "park pavilion", "nature center", "zoo", "botanical garden",
                "farm party", "petting zoo", "pumpkin patch", "adventure park",
                "climbing", "zip line", "ropes course", "picnic area",
                "outdoor party venue", "garden party"
            ),
            typicalDuration = "3 hours",
            averageCostPerPerson = 15..40,
            setting = "outdoor"
        ),

        // Characters & Performers - Entertainers, themed experiences
        PartyTypeTaxonomy(
            type = "characters_performers",
            displayName = "Characters & Performers",
            description = "Magicians, princesses, superheroes, and entertainers",
            icon = "sparkles",
            minAge = 2,
            maxAge = 10,
            googlePlacesTypes = listOf("event_venue", "amusement_center"),
            searchKeywords = listOf(
                "party entertainer", "magician", "magic show",
                "princess party", "superhero party", "character party",
                "clown", "face painter", "balloon artist", "balloon twister",
                "costumed character", "themed party entertainment"
            ),
            typicalDuration = "2 hours",
            averageCostPerPerson = 20..45,
            setting = "both"
        ),

        // Social/Dining - Food-focused, casual gatherings
        PartyTypeTaxonomy(
            type = "social_dining",
            displayName = "Social & Dining",
            description = "Restaurants, cafes, and food-focused celebrations",
            icon = "utensils",
            minAge = 1,
            maxAge = 18,
            googlePlacesTypes = listOf("restaurant", "cafe", "bakery"),
            searchKeywords = listOf(
                "restaurant party room", "private dining", "pizza party",
                "play cafe", "kids cafe", "party room rental",
                "ice cream party", "dessert bar", "themed restaurant"
            ),
            typicalDuration = "2 hours",
            averageCostPerPerson = 15..35,
            setting = "indoor"
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
