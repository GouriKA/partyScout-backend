package com.partyscout.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class ChatRequest(
    val message: String,
    val conversationHistory: List<ChatMessage>,
    val existingContext: LandingPageContext,
    val knownVenues: List<KnownVenue> = emptyList(),
)

data class KnownVenue(
    val num: Int,
    val name: String,
    val rating: Double? = null,
    val address: String? = null,
    val setting: String? = null,
    val reason: String? = null,
)

data class ChatMessage(
    val role: String,
    val content: String,
)

data class LandingPageContext(
    val city: String?,
    val persona: String?,
    val occasion: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatIntent(
    val city: String? = null,
    val persona: String? = null,
    val occasion: String? = null,
    val age: Int? = null,
    val groupSize: Int? = null,
    val themes: List<String> = emptyList(),
    val indoor: Boolean? = null,
    val date: String? = null,
    val readyToSearch: Boolean = false,
    val showVenues: Boolean = false,
)
