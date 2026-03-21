package com.jarvis.voiceassistant.data

/**
 * Single item in a todo list block.
 */
data class TodoItem(
    val text: String,
    val completed: Boolean = false
)
