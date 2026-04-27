package com.partyscout.chat

/**
 * Tool definitions sent to Claude in the agent loop.
 * Claude chooses which tool to call at each step.
 */
object ScoutTools {

    val definitions: List<Map<String, Any>> = listOf(
        mapOf(
            "name" to "ask_clarifying_question",
            "description" to "Ask the user a single clarifying question when you need more information to find venues. Use this when city, age/persona, or occasion is unknown. Ask only ONE question at a time.",
            "input_schema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "question" to mapOf(
                        "type" to "string",
                        "description" to "The question to ask the user. One sentence, conversational."
                    )
                ),
                "required" to listOf("question")
            )
        ),
        mapOf(
            "name" to "search_venues",
            "description" to "Search for party venues. Call this when you have enough context (at minimum: a city). Returns a list of venues.",
            "input_schema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "city" to mapOf("type" to "string", "description" to "City to search in"),
                    "age" to mapOf("type" to "integer", "description" to "Age of the birthday person"),
                    "occasion" to mapOf("type" to "string", "description" to "Type of occasion e.g. birthday, graduation"),
                    "indoor" to mapOf("type" to "boolean", "description" to "true=indoor, false=outdoor, omit for no preference"),
                    "groupSize" to mapOf("type" to "integer", "description" to "Number of guests"),
                    "themes" to mapOf(
                        "type" to "array",
                        "items" to mapOf("type" to "string"),
                        "description" to "Party themes e.g. dinosaurs, unicorns"
                    )
                ),
                "required" to listOf("city")
            )
        ),
        mapOf(
            "name" to "respond_to_user",
            "description" to "Send a final conversational response to the user. Use this to answer follow-up questions, provide information, or hand off venue results. Do NOT call this and search_venues in the same turn.",
            "input_schema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "message" to mapOf(
                        "type" to "string",
                        "description" to "The response to send to the user."
                    ),
                    "includeVenues" to mapOf(
                        "type" to "boolean",
                        "description" to "Set true if venues were just searched and should be shown to the user."
                    )
                ),
                "required" to listOf("message")
            )
        )
    )
}
