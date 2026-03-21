package com.jarvis.voiceassistant.assistant

import com.jarvis.voiceassistant.data.Block
import com.jarvis.voiceassistant.data.TodoItem

/**
 * Turns (tool, args) into a block to append to the document. No side effects; just builds the block.
 * Used by AssistantController after classifier + arg extraction.
 */
object ToolExecutor {

    /**
     * Execute tool with parsed args. Returns a block to append, or null if no block.
     * Errors (e.g. missing content) return Result.failure.
     */
    fun execute(tool: String, args: Map<String, Any?>): Result<Block?> {
        return when (tool) {
            "create_note" -> executeCreateNote(args)
            "todo_list" -> executeTodoList(args)
            else -> Result.success(null)
        }
    }

    private fun executeCreateNote(args: Map<String, Any?>): Result<Block?> {
        // LLM may return "content", "text", or "note"
        val content = listOf("content", "text", "note")
            .firstNotNullOfOrNull { args[it]?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
        if (content == null) {
            return Result.failure(IllegalArgumentException("create_note requires non-empty content"))
        }
        return Result.success(Block.Text(content))
    }

    private fun executeTodoList(args: Map<String, Any?>): Result<Block?> {
        // LLM may return "item", "task", "content", or "text" for the todo text
        val item = listOf("item", "task", "content", "text")
            .firstNotNullOfOrNull { args[it]?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
        if (item == null) {
            return Result.success(null)
        }
        return Result.success(Block.Todo(mutableListOf(TodoItem(text = item, completed = false))))
    }
}
