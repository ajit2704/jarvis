package com.jarvis.voiceassistant.assistant

import org.json.JSONObject

/**
 * Tool set for low-latency flow: classifier picks one tool, then arg extractor fills args.
 */
object JarvisTools {
    val VALID_TOOLS: List<String> = listOf(
        "create_note",
        "todo_list",
        "set_reminder",
        "canvas",
        "draw"
    )

    /** First token or word that can map to a tool (e.g. "todo" -> todo_list). */
    private val TOOL_ALIASES: Map<String, String> = mapOf(
        "note" to "create_note",
        "create_note" to "create_note",
        "todo" to "todo_list",
        "todo_list" to "todo_list",
        "todolist" to "todo_list",
        "list" to "todo_list",
        "reminder" to "set_reminder",
        "set_reminder" to "set_reminder",
        "alarm" to "set_reminder",
        "remind" to "set_reminder",
        "canvas" to "canvas",
        "whiteboard" to "canvas",
        "draw" to "draw",
        "sketch" to "draw"
    )

    /**
     * Parse classifier LLM output to a valid tool name.
     * Strips whitespace, lowercases, handles single token or "tool_name" in output.
     */
    fun parseToolFromOutput(output: String?): String? {
        if (output.isNullOrBlank()) return null
        val raw = output.trim().lowercase()
            .replace("-", "_")
            .split(Regex("\\s+")).firstOrNull() ?: return null
        if (raw in VALID_TOOLS) return raw
        return TOOL_ALIASES[raw] ?: VALID_TOOLS.find { raw in it || it.startsWith(raw) }
    }

    /**
     * Parse todo_list arg extractor output. Expects plain string = task text to add.
     * Also accepts JSON if the model returns it.
     */
    fun parseTodoListArgs(output: String?): Map<String, Any?> {
        if (output.isNullOrBlank()) return emptyMap()
        val trimmed = output.trim()
        if (trimmed.startsWith("{")) {
            return parseArgsFromOutput(output, "todo_list") ?: emptyMap()
        }
        return mapOf("item" to trimmed)
    }

    /**
     * Parse create_note arg extractor output. Expects plain string = note content.
     * Also accepts JSON if the model returns it.
     */
    fun parseCreateNoteArgs(output: String?): Map<String, Any?> {
        if (output.isNullOrBlank()) return emptyMap()
        val trimmed = output.trim()
        if (trimmed.startsWith("{")) {
            return parseArgsFromOutput(output, "create_note") ?: emptyMap()
        }
        return mapOf("content" to trimmed)
    }

    /**
     * Extract first JSON object from string and return as map.
     * Used for arg-extractor output. Returns null on parse failure.
     */
    fun parseArgsFromOutput(output: String?, tool: String): Map<String, Any?>? {
        if (output.isNullOrBlank()) return emptyMap()
        val trimmed = output.trim()
        val start = trimmed.indexOf('{')
        if (start == -1) return emptyMap()
        var depth = 0
        var end = start
        for (i in start until trimmed.length) {
            when (trimmed[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        end = i
                        break
                    }
                }
            }
        }
        if (end <= start) return null
        return try {
            val json = JSONObject(trimmed.substring(start, end + 1))
            json.keys().asSequence().associateWith { json.opt(it.toString()) }
        } catch (_: Exception) {
            null
        }
    }

    /** Tools that can have empty args (e.g. "open canvas" -> {}). */
    fun toolCanHaveEmptyArgs(tool: String): Boolean = tool in listOf("canvas", "todo_list")

    /** Expected arg keys per tool (for validation; we still accept whatever the LLM returns). */
    fun expectedArgKeys(tool: String): List<String> = when (tool) {
        "create_note" -> listOf("content")
        "todo_list" -> listOf("item", "action")
        "set_reminder" -> listOf("time", "message", "day")
        "canvas" -> listOf("name")
        "draw" -> listOf("description", "content")
        else -> emptyList()
    }
}
