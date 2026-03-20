package com.partyscout.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class ChatRequest(
    val message: String,
    val conversationHistory: List<ChatMessage>,
    val existingContext: LandingPageContext,
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
)
