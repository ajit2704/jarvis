package com.jarvis.voiceassistant.assistant

import android.util.Log
import com.jarvis.voiceassistant.data.Block
import com.jarvis.voiceassistant.data.AssistantState
import com.jarvis.voiceassistant.audio.IAudioRecorder
import com.jarvis.voiceassistant.llm.IModelManager
import com.jarvis.voiceassistant.llm.ILLMEngine
import com.jarvis.voiceassistant.stt.ISTTEngine
import com.jarvis.voiceassistant.stt.SttModelManager
import com.jarvis.voiceassistant.ui.ChatViewModel

/**
 * Orchestrates the voice assistant pipeline.
 *
 * Phase 1 low-latency: Voice → STT → tool classifier (1 token) → arg extractor (~5 tokens) → show in chat.
 */
class AssistantController(
    private val audioRecorder: IAudioRecorder,
    private val sttModelManager: SttModelManager,
    private val sttEngine: ISTTEngine,
    private val llmEngine: ILLMEngine,
    private val modelManager: IModelManager,
    private val viewModel: ChatViewModel
) {

    companion object {
        private const val LATENCY_TAG = "JarvisLatency"
    }

    /**
     * Handle user speech: record → transcribe → generate → update UI
     *
     * @param durationSeconds Recording duration
     * @return Result indicating success or failure
     */
    suspend fun handleUserSpeech(durationSeconds: Int = 5): Result<Unit> = runCatching {
        viewModel.updateState(AssistantState.Listening)
        val recordResult = audioRecorder.record(durationSeconds)
        if (recordResult.isFailure) {
            viewModel.updateState(AssistantState.Error(recordResult.exceptionOrNull()?.message ?: "Recording failed"))
            return@runCatching Result.failure(recordResult.exceptionOrNull() ?: Exception("Recording failed"))
        }
        val audioData = recordResult.getOrNull() ?: run {
            viewModel.updateState(AssistantState.Error("No audio"))
            return@runCatching Result.failure(Exception("No audio"))
        }

        viewModel.updateState(AssistantState.ProcessingSTT)
        val transcribeResult = sttEngine.transcribe(audioData)
        if (transcribeResult.isFailure) {
            viewModel.updateState(AssistantState.Error(transcribeResult.exceptionOrNull()?.message ?: "Transcription failed"))
            return@runCatching Result.failure(transcribeResult.exceptionOrNull() ?: Exception("Transcription failed"))
        }
        var transcript = transcribeResult.getOrNull()?.trim() ?: ""
        if (transcript.isBlank()) transcript = "(no speech detected)"

        viewModel.updateState(AssistantState.ProcessingLLM)
        val classifierPrompt = JarvisSystemPrompt.buildClassifierPrompt(transcript)
        val t0Classifier = System.currentTimeMillis()
        Log.d(LATENCY_TAG, "classifier entry")
        val classifierResult = llmEngine.generate(classifierPrompt, maxTokens = 5)
        val classifierMs = System.currentTimeMillis() - t0Classifier
        Log.d(LATENCY_TAG, "classifier done ${classifierMs}ms")
        if (classifierResult.isFailure) {
            viewModel.updateState(AssistantState.Error(classifierResult.exceptionOrNull()?.message ?: "Tool selection failed"))
            return@runCatching Result.failure(classifierResult.exceptionOrNull() ?: Exception("Tool selection failed"))
        }
        val toolOutput = classifierResult.getOrNull()?.trim() ?: ""
        val tool = JarvisTools.parseToolFromOutput(toolOutput)
        if (tool == null) {
            viewModel.showSnackbar("Could not determine action.")
            viewModel.updateState(AssistantState.Idle)
            return@runCatching Result.success(Unit)
        }

        val argPrompt = JarvisSystemPrompt.buildArgExtractorPrompt(tool, transcript)
        val t0Args = System.currentTimeMillis()
        Log.d(LATENCY_TAG, "arg_extract entry tool=$tool")
        val argResult = llmEngine.generate(argPrompt, maxTokens = 30)
        val argMs = System.currentTimeMillis() - t0Args
        Log.d(LATENCY_TAG, "arg_extract done ${argMs}ms")
        val raw = argResult.getOrNull()?.trim()
        val args = when {
            argResult.isFailure -> emptyMap<String, Any?>()
            tool == "todo_list" -> JarvisTools.parseTodoListArgs(raw)
            tool == "create_note" -> JarvisTools.parseCreateNoteArgs(raw)
            else -> JarvisTools.parseArgsFromOutput(raw, tool) ?: emptyMap<String, Any?>()
        }

        val execResult = ToolExecutor.execute(tool, args)
        execResult.fold(
            onSuccess = { block ->
                when {
                    block != null -> {
                        viewModel.appendBlock(block)
                        val confirmMsg = when (block) {
                            is Block.Text -> "Added note."
                            is Block.Todo -> "Added todo: ${block.items.singleOrNull()?.text ?: "..."}"
                        }
                        viewModel.showSnackbar(confirmMsg)
                    }
                    else -> viewModel.showSnackbar(formatToolResponse(tool, args))
                }
            },
            onFailure = { t ->
                viewModel.showSnackbar(t.message ?: formatToolResponse(tool, args))
            }
        )
        viewModel.updateState(AssistantState.Idle)
        Result.success(Unit)
    }.fold(
        onSuccess = { it },
        onFailure = { t ->
            viewModel.updateState(AssistantState.Error(t.message ?: "Unexpected error"))
            Result.failure<Unit>(t)
        }
    )

    private fun formatToolResponse(tool: String, args: Map<String, Any?>): String {
        if (args.isEmpty()) return "Tool: $tool"
        val argsStr = args.entries.joinToString(", ") { (k, v) -> "$k=${v?.toString()?.take(50) ?: "null"}" }
        return "Tool: $tool\nArgs: $argsStr"
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

