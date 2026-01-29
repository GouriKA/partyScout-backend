package com.partyscout.service

import com.partyscout.model.AddOn
import org.springframework.stereotype.Service

/**
 * Service for generating party details including what's included,
 * what's not included, and suggested add-ons
 */
@Service
class PartyDetailsService(
    private val partyTypeService: PartyTypeService
) {

    /**
     * Standard included items by party type
     */
    private val includedByType: Map<String, List<String>> = mapOf(
        "toddler_play" to listOf(
            "Supervised play time",
            "Party room access",
            "Basic paper goods (plates, napkins)",
            "Table setup"
        ),
        "character_party" to listOf(
            "Character appearance",
            "Photo opportunities",
            "Party favors",
            "Themed decorations"
        ),
        "bounce_house" to listOf(
            "Unlimited jump time",
            "Party room access",
            "Socks included",
            "Basic party supplies"
        ),
        "arcade" to listOf(
            "Game tokens/credits",
            "Pizza and drinks",
            "Party host",
            "Prize tickets"
        ),
        "sports" to listOf(
            "Lane/court rental",
            "Equipment (shoes, balls)",
            "Scoring system",
            "Party area"
        ),
        "arts_crafts" to listOf(
            "Art supplies and materials",
            "Instructor guidance",
            "Take-home project",
            "Aprons provided"
        ),
        "outdoor" to listOf(
            "Pavilion or area rental",
            "Picnic tables",
            "Trash receptacles",
            "Open play space"
        ),
        "escape_room" to listOf(
            "Private room booking",
            "Game master",
            "Team photos",
            "Lobby gathering area"
        ),
        "movies" to listOf(
            "Private screening room",
            "Popcorn and drinks",
            "Reserved seating",
            "Movie selection assistance"
        ),
        "pool_party" to listOf(
            "Pool access",
            "Lifeguard on duty",
            "Party area",
            "Locker room access"
        ),
        "go_karts" to listOf(
            "Races included",
            "Safety gear",
            "Party room access",
            "Winner recognition"
        ),
        "adventure_park" to listOf(
            "Admission to attractions",
            "Safety equipment",
            "Instructor/guide",
            "Group photos"
        )
    )

    /**
     * Premium included items (for higher price levels)
     */
    private val premiumInclusions: Map<String, List<String>> = mapOf(
        "toddler_play" to listOf(
            "Extended play time",
            "Themed decorations",
            "Party host assistance",
            "Digital invitations"
        ),
        "character_party" to listOf(
            "Extended character time",
            "Face painting",
            "Balloon artist",
            "Custom party favors"
        ),
        "bounce_house" to listOf(
            "Extended time",
            "Pizza and drinks",
            "Party host",
            "Goody bags"
        ),
        "arcade" to listOf(
            "Unlimited play",
            "VIP experience",
            "Exclusive games access",
            "Custom cake"
        ),
        "sports" to listOf(
            "Extra game time",
            "Private lanes/courts",
            "Food and drinks",
            "Trophies/medals"
        ),
        "arts_crafts" to listOf(
            "Premium materials",
            "Additional project",
            "Snacks and drinks",
            "Custom frames"
        ),
        "outdoor" to listOf(
            "Tent/canopy",
            "Setup and cleanup",
            "Grill access",
            "Activity equipment"
        ),
        "escape_room" to listOf(
            "Multiple rooms",
            "Extended time",
            "Snacks and drinks",
            "Commemorative photo"
        ),
        "movies" to listOf(
            "Premium snacks",
            "Multiple movie choice",
            "Party decorations",
            "VIP seating"
        ),
        "pool_party" to listOf(
            "Private pool time",
            "Pool toys",
            "Snacks and drinks",
            "Party coordinator"
        ),
        "go_karts" to listOf(
            "Extra races",
            "VIP pit area",
            "Food package",
            "Trophies"
        ),
        "adventure_park" to listOf(
            "All-access pass",
            "Extra activities",
            "Lunch included",
            "Commemorative gear"
        )
    )

    /**
     * Items typically NOT included (that parents should know about)
     */
    private val notIncludedByType: Map<String, List<String>> = mapOf(
        "toddler_play" to listOf(
            "Cake/cupcakes",
            "Custom decorations",
            "Party favors",
            "Additional food"
        ),
        "character_party" to listOf(
            "Venue rental",
            "Cake",
            "Additional entertainment",
            "Food service"
        ),
        "bounce_house" to listOf(
            "Cake",
            "Custom decorations",
            "Additional food",
            "Party favors"
        ),
        "arcade" to listOf(
            "Birthday cake",
            "Custom decorations",
            "Additional food items",
            "Party favors"
        ),
        "sports" to listOf(
            "Food beyond basic",
            "Custom cake",
            "Decorations",
            "Party favors"
        ),
        "arts_crafts" to listOf(
            "Food and drinks",
            "Cake",
            "Decorations",
            "Party favors"
        ),
        "outdoor" to listOf(
            "Food and drinks",
            "Entertainment",
            "Decorations",
            "Party supplies",
            "Cleanup service"
        ),
        "escape_room" to listOf(
            "Food",
            "Cake",
            "Decorations",
            "Party favors"
        ),
        "movies" to listOf(
            "Birthday cake",
            "Custom decorations",
            "Party favors",
            "Meal service"
        ),
        "pool_party" to listOf(
            "Food beyond snacks",
            "Cake",
            "Decorations",
            "Pool toys (some venues)",
            "Towels"
        ),
        "go_karts" to listOf(
            "Cake",
            "Decorations",
            "Party favors",
            "Additional food"
        ),
        "adventure_park" to listOf(
            "Cake",
            "Custom decorations",
            "Party favors",
            "Souvenirs"
        )
    )

    /**
     * Suggested add-ons by party type
     */
    private val addOnsByType: Map<String, List<AddOn>> = mapOf(
        "toddler_play" to listOf(
            AddOn("Character visit", "Add a costumed character appearance", 75, true),
            AddOn("Extra play time", "30 additional minutes of play", 40, false),
            AddOn("Face painting", "Simple designs for all guests", 60, true),
            AddOn("Balloon twisting", "Custom balloon animals", 50, false)
        ),
        "character_party" to listOf(
            AddOn("Second character", "Add another character to the party", 100, false),
            AddOn("Magic show", "15-minute magic performance", 75, true),
            AddOn("Princess makeovers", "Hair, nails, and makeup", 15, false),
            AddOn("Superhero training", "Interactive activity session", 50, false)
        ),
        "bounce_house" to listOf(
            AddOn("Pizza package", "2 slices + drink per child", 8, true),
            AddOn("Extra time", "30 additional minutes", 50, false),
            AddOn("Goody bags", "Pre-made party favors", 5, true),
            AddOn("Glow party upgrade", "UV lights and glow items", 40, false)
        ),
        "arcade" to listOf(
            AddOn("Extra tokens", "25 additional game tokens", 15, true),
            AddOn("Prize upgrade", "Guaranteed prize tier", 10, false),
            AddOn("VIP lane", "Private bowling/attraction access", 50, false),
            AddOn("Custom cake", "Themed birthday cake", 40, true)
        ),
        "sports" to listOf(
            AddOn("Extra game", "Additional bowling game or court time", 35, true),
            AddOn("Shoe upgrade", "Premium rental shoes", 5, false),
            AddOn("Trophy package", "Winner trophies and medals", 25, true),
            AddOn("Food upgrade", "Premium food package", 8, false)
        ),
        "arts_crafts" to listOf(
            AddOn("Second project", "Additional art activity", 10, true),
            AddOn("Premium canvas", "Upgrade to gallery canvas", 8, false),
            AddOn("Frame it", "Take-home display frames", 6, true),
            AddOn("Instructor demo", "Live painting demonstration", 30, false)
        ),
        "outdoor" to listOf(
            AddOn("Bounce house rental", "Inflatable entertainment", 150, true),
            AddOn("Face painting", "Artist for 2 hours", 100, true),
            AddOn("Sports equipment", "Soccer, frisbee, etc.", 30, false),
            AddOn("Tent rental", "Shade canopy 10x10", 75, false)
        ),
        "escape_room" to listOf(
            AddOn("Second room", "Book an additional escape room", 150, false),
            AddOn("Extended time", "15 extra minutes per room", 30, false),
            AddOn("Hint package", "Extra hints available", 15, false),
            AddOn("Photo package", "Professional in-game photos", 25, true)
        ),
        "movies" to listOf(
            AddOn("Candy bar", "Assorted movie candy", 4, true),
            AddOn("Premium seating", "Recliner upgrades", 5, false),
            AddOn("Popcorn refills", "Unlimited popcorn", 15, false),
            AddOn("3D movie upgrade", "3D glasses included", 3, false)
        ),
        "pool_party" to listOf(
            AddOn("Pool toys", "Floats and water toys package", 30, true),
            AddOn("Swim instructor", "Games and water activities", 75, false),
            AddOn("Extra pool time", "Additional hour", 50, false),
            AddOn("Cabana rental", "Private shaded area", 100, false)
        ),
        "go_karts" to listOf(
            AddOn("Extra races", "2 additional races per person", 12, true),
            AddOn("VIP experience", "Priority lane access", 10, false),
            AddOn("Trophy ceremony", "Winner celebration package", 30, true),
            AddOn("Group photo", "Professional race day photo", 20, false)
        ),
        "adventure_park" to listOf(
            AddOn("Additional attraction", "Access to bonus activities", 15, true),
            AddOn("Photo package", "Action shots of all guests", 40, true),
            AddOn("Lunch upgrade", "Premium meal option", 8, false),
            AddOn("Souvenir package", "T-shirt for birthday child", 20, false)
        )
    )

    /**
     * Get included items for a venue based on party type and price level
     */
    fun getIncludedItems(partyTypes: List<String>, priceLevel: Int?): List<String> {
        val baseItems = partyTypes
            .flatMap { includedByType[it] ?: emptyList() }
            .distinct()

        // Add premium items for higher price levels
        val premiumItems = if ((priceLevel ?: 2) >= 3) {
            partyTypes
                .flatMap { premiumInclusions[it] ?: emptyList() }
                .take(2) // Add a couple premium items
        } else {
            emptyList()
        }

        return (baseItems + premiumItems).distinct()
    }

    /**
     * Get items NOT included that parents should know about
     */
    fun getNotIncludedItems(partyTypes: List<String>, priceLevel: Int?): List<String> {
        val allNotIncluded = partyTypes
            .flatMap { notIncludedByType[it] ?: emptyList() }
            .distinct()

        // At premium venues, fewer things are excluded
        return if ((priceLevel ?: 2) >= 3) {
            allNotIncluded.take(3)
        } else {
            allNotIncluded
        }
    }

    /**
     * Get suggested add-ons for a party
     */
    fun getSuggestedAddOns(partyTypes: List<String>, guestCount: Int): List<AddOn> {
        val addOns = partyTypes
            .flatMap { addOnsByType[it] ?: emptyList() }
            .distinctBy { it.name }

        // Adjust costs for per-person items based on guest count
        return addOns.map { addOn ->
            if (addOn.estimatedCost <= 15) {
                // Likely a per-person cost
                addOn.copy(
                    estimatedCost = addOn.estimatedCost * guestCount,
                    description = "${addOn.description} (${addOn.estimatedCost}/person)"
                )
            } else {
                addOn
            }
        }
    }

    /**
     * Get typical party duration for a venue type
     */
    fun getTypicalDuration(partyTypes: List<String>): String {
        if (partyTypes.isEmpty()) return "2 hours"

        val durations = partyTypes.mapNotNull { partyTypeService.getTypicalDuration(it) }
        if (durations.isEmpty()) return "2 hours"

        // Return longest duration if multiple types
        return durations.maxByOrNull {
            it.replace(" hours", "").replace(" hour", "").replace(".5", ".5").toDoubleOrNull() ?: 2.0
        } ?: "2 hours"
    }

    /**
     * Get what to bring suggestions
     */
    fun getWhatToBring(partyTypes: List<String>, priceLevel: Int?): List<String> {
        val baseSuggestions = mutableListOf(
            "Birthday cake or cupcakes",
            "Candles and cake server",
            "Camera for photos"
        )

        // Add type-specific suggestions
        if (partyTypes.any { it == "pool_party" }) {
            baseSuggestions.addAll(listOf("Towels", "Sunscreen", "Change of clothes"))
        }
        if (partyTypes.any { it == "outdoor" }) {
            baseSuggestions.addAll(listOf("Cooler with ice", "Sunscreen", "Bug spray"))
        }
        if (partyTypes.any { it in listOf("bounce_house", "adventure_park", "go_karts") }) {
            baseSuggestions.add("Comfortable clothes for activity")
        }

        // At budget venues, need to bring more
        if ((priceLevel ?: 2) <= 1) {
            baseSuggestions.addAll(listOf("Paper goods", "Drinks", "Snacks"))
        }

        return baseSuggestions.distinct()
    }

    /**
     * Generate age appropriateness description
     */
    fun getAgeAppropriatenessDescription(partyTypes: List<String>): String {
        if (partyTypes.isEmpty()) return "Suitable for various ages"

        val taxonomies = partyTypes.mapNotNull { partyTypeService.getTaxonomyForType(it) }
        if (taxonomies.isEmpty()) return "Suitable for various ages"

        val minAge = taxonomies.minOfOrNull { it.minAge } ?: 1
        val maxAge = taxonomies.maxOfOrNull { it.maxAge } ?: 18

        return "Best for ages $minAge-$maxAge"
    }
}
