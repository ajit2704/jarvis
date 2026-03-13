package com.jarvis.voiceassistant.assistant

import com.jarvis.voiceassistant.data.AssistantState
import com.jarvis.voiceassistant.audio.IAudioRecorder
import com.jarvis.voiceassistant.llm.IModelManager
import com.jarvis.voiceassistant.llm.ILLMEngine
import com.jarvis.voiceassistant.stt.ISTTEngine
import com.jarvis.voiceassistant.stt.SttModelManager
import com.jarvis.voiceassistant.ui.ChatViewModel

/**
 * Orchestrates the voice assistant pipeline
 *
 * Flow: Audio → STT → LLM → UI
 *
 * Responsibilities:
 * - Coordinate between AudioRecorder, STTEngine, LLMEngine
 * - Update ChatViewModel state
 * - Handle errors gracefully
 * - Manage the full conversation flow
 */
class AssistantController(
    private val audioRecorder: IAudioRecorder,
    private val sttModelManager: SttModelManager,
    private val sttEngine: ISTTEngine,
    private val llmEngine: ILLMEngine,
    private val modelManager: IModelManager,
    private val viewModel: ChatViewModel
) {

    /**
     * Handle user speech: record → transcribe → generate → update UI
     *
     * @param durationSeconds Recording duration
     * @return Result indicating success or failure
     */
    suspend fun handleUserSpeech(durationSeconds: Int = 5): Result<Unit> {
        viewModel.updateState(AssistantState.Listening)
        val recordResult = audioRecorder.record(durationSeconds)
        if (recordResult.isFailure) {
            viewModel.updateState(AssistantState.Error(recordResult.exceptionOrNull()?.message ?: "Recording failed"))
            return Result.failure(recordResult.exceptionOrNull() ?: Exception("Recording failed"))
        }
        val audioData = recordResult.getOrNull() ?: return Result.failure(Exception("No audio"))

        viewModel.updateState(AssistantState.ProcessingSTT)
        val transcribeResult = sttEngine.transcribe(audioData)
        if (transcribeResult.isFailure) {
            viewModel.updateState(AssistantState.Error(transcribeResult.exceptionOrNull()?.message ?: "Transcription failed"))
            return Result.failure(transcribeResult.exceptionOrNull() ?: Exception("Transcription failed"))
        }
        var transcript = transcribeResult.getOrNull()?.trim() ?: ""
        if (transcript.isBlank()) transcript = "(no speech detected)"

        viewModel.addUserMessage(transcript)

        viewModel.updateState(AssistantState.ProcessingLLM)
        val prompt = buildChatPrompt(transcript)
        val generateResult = llmEngine.generate(prompt, maxTokens = 256)
        if (generateResult.isFailure) {
            viewModel.updateState(AssistantState.Error(generateResult.exceptionOrNull()?.message ?: "Generation failed"))
            return Result.failure(generateResult.exceptionOrNull() ?: Exception("Generation failed"))
        }
        val response = generateResult.getOrNull()?.trim() ?: "(no response)"
        viewModel.addAssistantMessage(response)
        viewModel.updateState(AssistantState.Idle)
        return Result.success(Unit)
    }

    private fun buildChatPrompt(userText: String): String {
        val history = viewModel.messages.value.joinToString("\n") { msg ->
            if (msg.isUser) "User: ${msg.text}" else "Assistant: ${msg.text}"
        }
        return if (history.isBlank()) "User: $userText\nAssistant:" else "$history\nAssistant:"
    }

    /**
     * Initialize all components (STT and LLM). Ensures STT and LLM models exist, then inits engines.
     */
    suspend fun initialize(): Result<Unit> {
        val sttModelResult = sttModelManager.ensureModelExists()
        if (sttModelResult.isFailure) {
            return Result.failure(sttModelResult.exceptionOrNull() ?: Exception("STT model not available"))
        }
        val sttResult = sttEngine.initialize()
        if (sttResult.isFailure) {
            return Result.failure(sttResult.exceptionOrNull() ?: Exception("STT init failed"))
        }
        val modelResult = modelManager.ensureModelExists()
        if (modelResult.isFailure) {
            return Result.failure(modelResult.exceptionOrNull() ?: Exception("LLM model not available"))
        }
        val modelFile = modelResult.getOrNull() ?: return Result.failure(Exception("No model file"))
        val llmResult = llmEngine.initialize(modelFile)
        if (llmResult.isFailure) {
            return Result.failure(llmResult.exceptionOrNull() ?: Exception("LLM init failed"))
        }
        return Result.success(Unit)
    }

    /**
     * Release all resources
     */
    fun release() {
        sttEngine.release()
        llmEngine.release()
    }
}

