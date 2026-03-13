package com.jarvis.voiceassistant.data

/**
 * Represents the current state of the assistant
 */
sealed class AssistantState {
    object Idle : AssistantState()
    object Listening : AssistantState()
    object ProcessingSTT : AssistantState()
    object ProcessingLLM : AssistantState()
    data class Error(val message: String) : AssistantState()
}

