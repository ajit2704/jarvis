package com.jarvis.voiceassistant.data

/**
 * Represents a chat message
 */
data class Message(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

