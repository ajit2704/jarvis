package com.jarvis.voiceassistant.assistant

/**
 * Prompts for Jarvis: tool classifier (1 token) + per-tool arg extractor.
 * Phase 1 low-latency flow: classifier → arg extractor → show in chat.
 */
object JarvisSystemPrompt {

    /** Tool classifier: respond with ONLY one tool name. */
    val TOOL_CLASSIFIER_SYSTEM: String = """
You are Jarvis, a voice assistant.
Choose the best tool for the user request.
Input is from speech recognition and may have errors.

Tools (respond with ONLY one name, nothing else):
- create_note   (save a note, write note, remember X, note down)
- todo_list    (add todo, add to list, my todos, show list)
- set_reminder (set alarm, wake me at X, remind me to X)
- canvas       (open canvas, new canvas, whiteboard)
- draw         (draw X, sketch X)
""".trimIndent()

    /** Build prompt for tool classification. Max tokens ~5. */
    fun buildClassifierPrompt(userText: String) =
        """
Choose one tool name.

create_note
todo_list
set_reminder
canvas
draw

User: $userText
Tool:
"""

    /** Build prompt for argument extraction for the given tool. Max tokens ~30. */
    fun buildArgExtractorPrompt(tool: String, userText: String): String {

        val instruction = when(tool) {

            "create_note" ->
                "Return only the note content. Remove words like create note, note down, remember."

            "todo_list" ->
                """
Return only the task text.
Example:
User: add todo buy milk
Output: buy milk

user: add bananas to the list
output: bananas
"""

            "set_reminder" ->
                "Return JSON {\"time\":\"...\",\"message\":\"...\"}. Remove words like remind me."

            "canvas" ->
                "Return canvas name only. Remove words like open canvas."

            "draw" ->
                "Return drawing description only."

            else ->
                "Return only the argument."
        }

        return """
$instruction
User: $userText
Output:
"""
    }

    /** Legacy full-JSON prompt (kept for reference / fallback). */
    val VALUE: String = """
You are Jarvis, a voice assistant.
Input is from speech recognition and may have errors (e.g. "uh", wrong word).

Use ONLY one of these tool names:
- create_note  : save a note (create note X, write note, remember X, note down)
- todo_list    : add to or show to-do list (add todo X, add to list, my todos, show list)
- set_reminder : alarm or reminder at a time (set alarm, wake me at X, remind me to X, reminder for...)
- canvas       : create or open a canvas / whiteboard
- draw         : draw something (draw X, sketch X)

Respond with ONLY valid JSON. Format: {"tool": "<name>", "args": { ... }}
""".trimIndent()
}
