package com.pascal.claudemobile.data

import java.util.UUID

enum class Role { USER, ASSISTANT }

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false,
)
