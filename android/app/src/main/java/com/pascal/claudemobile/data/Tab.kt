package com.pascal.claudemobile.data

import java.util.UUID

data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<Message> = emptyList(),
    val sessionId: String? = null,
    val isLoading: Boolean = false,
) {
    /** Derive a display title from the first user message, fallback to default. */
    companion object {
        const val DEFAULT_TITLE = "New chat"
        fun titleFrom(firstMessage: String): String {
            val clean = firstMessage.replace("\n", " ").trim()
            return if (clean.length <= 24) clean else clean.take(22) + "…"
        }
    }
}

data class SessionListEntry(
    val id: String,
    val title: String,
    val modified: Long,
    val turns: Int,
)
