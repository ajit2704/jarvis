package com.jarvis.voiceassistant.llm

import android.content.Context
import android.util.Log
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeLLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LLM inference engine using RunAnywhere CppBridgeLLM (llama.cpp / GGUF).
 *
 * Responsibilities:
 * - Load GGUF model via CppBridgeLLM
 * - Generate responses from prompts
 * - Handle generation errors
 */
interface ILLMEngine {
    suspend fun initialize(modelFile: File): Result<Unit>
    suspend fun generate(prompt: String, maxTokens: Int = 100): Result<String>
    fun isLoaded(): Boolean
    fun release()
}

class LLMEngine(private val context: Context) : ILLMEngine {

    @Volatile
    private var loaded: Boolean = false

    private val runanywhereDir: File
        get() = File(context.filesDir, "runanywhere").also { it.mkdirs() }

    override suspend fun initialize(modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        if (!modelFile.exists() || !modelFile.isFile) {
            return@withContext Result.failure(IllegalArgumentException("Model file does not exist: ${modelFile.absolutePath}"))
        }
        runCatching {
            // RunAnywhere native loader can fail with -422 when loading outside base dir; copy into runanywhere base.
            val dest = File(runanywhereDir, modelFile.name)
            if (!dest.exists() || dest.length() != modelFile.length()) {
                modelFile.inputStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                if (dest.length() != modelFile.length()) {
                    throw IllegalStateException("Model copy incomplete: ${dest.length()} != ${modelFile.length()} bytes")
                }
            }
            val loadPath = dest.absolutePath
            if (!dest.exists() || dest.length() < MIN_GGUF_BYTES) {
                throw IllegalStateException("Model file missing or too small: ${dest.absolutePath} (${dest.length()} bytes)")
            }
            if (CppBridgeLLM.isLoaded) {
                if (CppBridgeLLM.getLoadedModelPath() == loadPath) {
                    loaded = true
                    return@withContext Result.success(Unit)
                }
                CppBridgeLLM.unload()
            }
            CppBridgeLLM.create()
            // Mobile-friendly: n_ctx=1024 (~2.1 GB RAM). SDK may not pass to native yet; when it does, helps avoid -422.
            val modelConfig = CppBridgeLLM.ModelConfig(
                contextLength = 256,
                batchSize = 64,
                useMemoryMap = true,
                threads = Runtime.getRuntime().availableProcessors(),
                gpuLayers = 0
            )
            val result = CppBridgeLLM.loadModel(
                modelPath = loadPath,
                modelId = MODEL_ID,
                modelName = "SmolLM-1.7B",
                config = modelConfig
            )
            if (result != 0) {
                Log.e(TAG, "loadModel failed: code=$result path=$loadPath size=${dest.length()} canRead=${dest.canRead()}")
                throw IllegalStateException("Model load failed: code $result")
            }
            loaded = true
        }.onFailure {
            loaded = false
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int): Result<String> = withContext(Dispatchers.IO) {
        if (!loaded || !CppBridgeLLM.isReady) {
            return@withContext Result.failure(IllegalStateException("LLM not loaded or not ready"))
        }
        runCatching {
            val config = CppBridgeLLM.GenerationConfig(
                maxTokens = maxTokens,
                temperature = 0.1f,
                topP = 0.9f,
            )
            val result = CppBridgeLLM.generate(prompt, config)
            result.text
        }
    }

    override fun isLoaded(): Boolean = loaded && CppBridgeLLM.isLoaded

    override fun release() {
        try {
            if (CppBridgeLLM.isLoaded) {
                CppBridgeLLM.unload()
            }
            CppBridgeLLM.destroy()
        } catch (_: Throwable) { }
        loaded = false
    }

    companion object {
        private const val TAG = "JarvisLLM"
        private const val MODEL_ID = "jarvis-llm"
        private const val MIN_GGUF_BYTES = 100L * 1024 * 1024 // 100 MB
    }
}
