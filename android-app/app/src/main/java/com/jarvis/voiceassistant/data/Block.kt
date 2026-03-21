package com.jarvis.voiceassistant.data

/**
 * A single block in a document. Supports text and todo list; more types (LIST, QUOTE, CODE, CANVAS) later.
 */
sealed class Block {
    data class Text(val content: String) : Block()
    data class Todo(val items: MutableList<TodoItem>) : Block()
}
