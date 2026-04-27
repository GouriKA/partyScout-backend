package com.partyscout.unit

import com.partyscout.chat.ScoutTools
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

@DisplayName("ScoutTools")
class ScoutToolsTest {

    @Nested
    @DisplayName("Tool definitions")
    inner class ToolDefinitions {

        @Test
        @DisplayName("should define exactly three tools")
        fun shouldDefineThreeTools() {
            assertEquals(3, ScoutTools.definitions.size)
        }

        @Test
        @DisplayName("should include ask_clarifying_question tool")
        fun shouldIncludeAskClarifyingQuestion() {
            val tool = ScoutTools.definitions.find { it["name"] == "ask_clarifying_question" }
            assertNotNull(tool)
        }

        @Test
        @DisplayName("should include search_venues tool")
        fun shouldIncludeSearchVenues() {
            val tool = ScoutTools.definitions.find { it["name"] == "search_venues" }
            assertNotNull(tool)
        }

        @Test
        @DisplayName("should include respond_to_user tool")
        fun shouldIncludeRespondToUser() {
            val tool = ScoutTools.definitions.find { it["name"] == "respond_to_user" }
            assertNotNull(tool)
        }

        @Test
        @DisplayName("each tool should have name, description, and input_schema")
        fun eachToolShouldHaveRequiredFields() {
            ScoutTools.definitions.forEach { tool ->
                assertTrue(tool.containsKey("name"), "Tool missing 'name': $tool")
                assertTrue(tool.containsKey("description"), "Tool missing 'description': $tool")
                assertTrue(tool.containsKey("input_schema"), "Tool missing 'input_schema': $tool")
            }
        }

        @Test
        @DisplayName("search_venues should require city")
        fun searchVenuesShouldRequireCity() {
            val tool = ScoutTools.definitions.find { it["name"] == "search_venues" }!!
            @Suppress("UNCHECKED_CAST")
            val schema = tool["input_schema"] as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val required = schema["required"] as List<String>
            assertTrue(required.contains("city"))
        }

        @Test
        @DisplayName("ask_clarifying_question should require question")
        fun askClarifyingQuestionShouldRequireQuestion() {
            val tool = ScoutTools.definitions.find { it["name"] == "ask_clarifying_question" }!!
            @Suppress("UNCHECKED_CAST")
            val schema = tool["input_schema"] as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val required = schema["required"] as List<String>
            assertTrue(required.contains("question"))
        }

        @Test
        @DisplayName("respond_to_user should require message")
        fun respondToUserShouldRequireMessage() {
            val tool = ScoutTools.definitions.find { it["name"] == "respond_to_user" }!!
            @Suppress("UNCHECKED_CAST")
            val schema = tool["input_schema"] as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val required = schema["required"] as List<String>
            assertTrue(required.contains("message"))
        }
    }
}
