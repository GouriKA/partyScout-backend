package com.partyscout.unit

import com.partyscout.service.PartyTypeService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("PartyTypeService")
class PartyTypeServiceTest {

    private lateinit var partyTypeService: PartyTypeService

    @BeforeEach
    fun setUp() {
        partyTypeService = PartyTypeService()
    }

    @Nested
    @DisplayName("getPartyTypesForAge")
    inner class GetPartyTypesForAge {

        @Test
        @DisplayName("should return party types for a 7-year-old")
        fun shouldReturnPartyTypesForAge7() {
            val types = partyTypeService.getPartyTypesForAge(7)

            assertFalse(types.isEmpty(), "Should return at least one party type")
            assertTrue(types.all { it.ageRange.contains("7") || isAgeInRange(7, it.ageRange) },
                "All types should be appropriate for age 7")
        }

        @Test
        @DisplayName("should return party types for a toddler (age 3)")
        fun shouldReturnPartyTypesForToddler() {
            val types = partyTypeService.getPartyTypesForAge(3)

            assertFalse(types.isEmpty())
            // Characters & Performers should be available for toddlers
            assertTrue(types.any { it.type == "characters_performers" })
        }

        @Test
        @DisplayName("should return party types for a teenager (age 15)")
        fun shouldReturnPartyTypesForTeenager() {
            val types = partyTypeService.getPartyTypesForAge(15)

            assertFalse(types.isEmpty())
            // Amusement should be available for teens
            assertTrue(types.any { it.type == "amusement" })
        }

        @Test
        @DisplayName("should return types sorted by popularity score")
        fun shouldReturnTypesSortedByPopularity() {
            val types = partyTypeService.getPartyTypesForAge(7)

            for (i in 0 until types.size - 1) {
                assertTrue(types[i].popularityScore >= types[i + 1].popularityScore,
                    "Types should be sorted by popularity score descending")
            }
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 5, 10, 15, 18])
        @DisplayName("should return non-empty results for valid ages")
        fun shouldReturnResultsForValidAges(age: Int) {
            val types = partyTypeService.getPartyTypesForAge(age)
            assertFalse(types.isEmpty(), "Should return results for age $age")
        }

        @Test
        @DisplayName("should return empty for age 0")
        fun shouldReturnEmptyForInvalidAge() {
            val types = partyTypeService.getPartyTypesForAge(0)
            assertTrue(types.isEmpty(), "Should return empty for age 0")
        }
    }

    @Nested
    @DisplayName("getKeywordsForPartyType")
    inner class GetKeywordsForPartyType {

        @Test
        @DisplayName("should return keywords for active_play")
        fun shouldReturnKeywordsForActivePlay() {
            val keywords = partyTypeService.getKeywordsForPartyType("active_play")

            assertFalse(keywords.isEmpty())
            assertTrue(keywords.any { it.contains("trampoline", ignoreCase = true) })
        }

        @Test
        @DisplayName("should return keywords for amusement")
        fun shouldReturnKeywordsForAmusement() {
            val keywords = partyTypeService.getKeywordsForPartyType("amusement")

            assertFalse(keywords.isEmpty())
            assertTrue(keywords.any { it.contains("arcade", ignoreCase = true) })
        }

        @Test
        @DisplayName("should return empty list for unknown type")
        fun shouldReturnEmptyForUnknownType() {
            val keywords = partyTypeService.getKeywordsForPartyType("unknown_type")
            assertTrue(keywords.isEmpty())
        }
    }

    @Nested
    @DisplayName("getGooglePlacesTypesForPartyType")
    inner class GetGooglePlacesTypes {

        @Test
        @DisplayName("should return Google Places types for active_play")
        fun shouldReturnPlacesTypesForActivePlay() {
            val types = partyTypeService.getGooglePlacesTypesForPartyType("active_play")

            assertFalse(types.isEmpty())
            assertTrue(types.any { it == "amusement_center" || it == "gym" })
        }

        @Test
        @DisplayName("should return Google Places types for outdoor")
        fun shouldReturnPlacesTypesForOutdoor() {
            val types = partyTypeService.getGooglePlacesTypesForPartyType("outdoor")

            assertFalse(types.isEmpty())
            assertTrue(types.contains("park") || types.contains("zoo"))
        }
    }

    @Nested
    @DisplayName("getTaxonomyForType")
    inner class GetTaxonomyForType {

        @Test
        @DisplayName("should return taxonomy for valid type")
        fun shouldReturnTaxonomyForValidType() {
            val taxonomy = partyTypeService.getTaxonomyForType("active_play")

            assertNotNull(taxonomy)
            assertEquals("Active Play", taxonomy?.displayName)
            assertEquals("indoor", taxonomy?.setting)
        }

        @Test
        @DisplayName("should return null for unknown type")
        fun shouldReturnNullForUnknownType() {
            val taxonomy = partyTypeService.getTaxonomyForType("unknown")
            assertNull(taxonomy)
        }
    }

    @Nested
    @DisplayName("getAllPartyTypes")
    inner class GetAllPartyTypes {

        @Test
        @DisplayName("should return exactly 6 party types")
        fun shouldReturnSixPartyTypes() {
            val types = partyTypeService.getAllPartyTypes()
            assertEquals(6, types.size, "Should have exactly 6 broad party types")
        }

        @Test
        @DisplayName("should include all expected types")
        fun shouldIncludeAllExpectedTypes() {
            val types = partyTypeService.getAllPartyTypes()
            val typeNames = types.map { it.type }

            assertTrue(typeNames.contains("active_play"))
            assertTrue(typeNames.contains("creative"))
            assertTrue(typeNames.contains("amusement"))
            assertTrue(typeNames.contains("outdoor"))
            assertTrue(typeNames.contains("characters_performers"))
            assertTrue(typeNames.contains("social_dining"))
        }
    }

    @Nested
    @DisplayName("getTypicalDuration")
    inner class GetTypicalDuration {

        @Test
        @DisplayName("should return duration for valid type")
        fun shouldReturnDurationForValidType() {
            val duration = partyTypeService.getTypicalDuration("active_play")
            assertEquals("2 hours", duration)
        }

        @Test
        @DisplayName("should return default for unknown type")
        fun shouldReturnDefaultForUnknownType() {
            val duration = partyTypeService.getTypicalDuration("unknown")
            assertEquals("2 hours", duration)
        }
    }

    @Nested
    @DisplayName("getSetting")
    inner class GetSetting {

        @Test
        @DisplayName("should return indoor for active_play")
        fun shouldReturnIndoorForActivePlay() {
            val setting = partyTypeService.getSetting("active_play")
            assertEquals("indoor", setting)
        }

        @Test
        @DisplayName("should return outdoor for outdoor type")
        fun shouldReturnOutdoorForOutdoorType() {
            val setting = partyTypeService.getSetting("outdoor")
            assertEquals("outdoor", setting)
        }

        @Test
        @DisplayName("should return both for characters_performers")
        fun shouldReturnBothForCharacters() {
            val setting = partyTypeService.getSetting("characters_performers")
            assertEquals("both", setting)
        }
    }

    @Nested
    @DisplayName("getKeywordsForPartyTypes (multiple)")
    inner class GetKeywordsForMultipleTypes {

        @Test
        @DisplayName("should combine keywords from multiple types")
        fun shouldCombineKeywords() {
            val keywords = partyTypeService.getKeywordsForPartyTypes(listOf("active_play", "amusement"))

            assertTrue(keywords.any { it.contains("trampoline", ignoreCase = true) })
            assertTrue(keywords.any { it.contains("arcade", ignoreCase = true) })
        }

        @Test
        @DisplayName("should return distinct keywords")
        fun shouldReturnDistinctKeywords() {
            val keywords = partyTypeService.getKeywordsForPartyTypes(listOf("active_play", "amusement"))
            assertEquals(keywords.size, keywords.distinct().size)
        }
    }

    // Helper function to check if age is in range like "Ages 3-16"
    private fun isAgeInRange(age: Int, ageRange: String): Boolean {
        val regex = """Ages (\d+)-(\d+)""".toRegex()
        val match = regex.find(ageRange) ?: return false
        val (min, max) = match.destructured
        return age in min.toInt()..max.toInt()
    }
}
