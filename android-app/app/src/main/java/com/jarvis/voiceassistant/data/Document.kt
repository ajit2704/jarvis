package com.jarvis.voiceassistant.data

/**
 * In-memory document: title + ordered blocks. Persistence (Room) in Phase 3.
 */
data class Document(
    val id: String,
    val title: String,
    val blocks: MutableList<Block> = mutableListOf()
)
