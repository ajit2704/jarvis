package com.jarvis.voiceassistant.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages STT model (Moonshine ONNX/.ort or safetensors).
 *
 * Default: [MoonshineVariant.TINY_STREAMING_EN] — uses .ort from
 * assets/tiny-streaming-en (encoder.ort, frontend.ort, decoder_kv.ort, tokenizer.bin, etc.).
 *
 * @see https://github.com/moonshine-ai/moonshine/tree/main/android
 */
class SttModelManager(
    private val context: Context,
    private val variant: MoonshineVariant = MoonshineVariant.TINY_STREAMING_EN,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val _downloadProgress = MutableStateFlow<SttModelState>(SttModelState.NotDownloaded)
    val downloadProgress: StateFlow<SttModelState> = _downloadProgress.asStateFlow()

    private val modelDir: File
        get() = File(context.filesDir, STT_MODEL_SUBDIR).apply { mkdirs() }.let {
            File(it, variant.dirName)
        }.apply { mkdirs() }

    companion object {
        /** Subdir under filesDir where STT model variant dirs live (e.g. moonshine-streaming-tiny). */
        private const val STT_MODEL_SUBDIR = "models"
    }

    /** Base URL for this variant (Hugging Face resolve). */
    private val baseUrl: String
        get() = "https://huggingface.co/${variant.repo}/resolve/main"

    /** Files to download for this variant (from HF repo tree); empty for asset-only variants. */
    private val filesToDownload: List<String>
        get() = variant.files

    /**
     * Ensures the STT model is available under [modelDir].
     * 1. If already present in filesDir, returns it.
     * 2. If bundled in assets (e.g. app/src/main/assets/tiny-streaming-en), copies to filesDir.
     * 3. Otherwise downloads from Hugging Face (safetensors variants only).
     */
    suspend fun ensureModelExists(): Result<File> = withContext(Dispatchers.IO) {
        val dir = modelDir
        if (isModelDownloaded(dir)) {
            _downloadProgress.value = SttModelState.Ready(dir.absolutePath)
            return@withContext Result.success(dir)
        }
        runCatching {
            if (copyFromAssetsIfPresent(dir)) {
                _downloadProgress.value = SttModelState.Ready(dir.absolutePath)
                return@withContext Result.success(dir)
            }
            if (variant.files.isEmpty()) {
                _downloadProgress.value = SttModelState.Error("No asset model and no download URLs for ${variant.dirName}")
                return@withContext Result.failure(IllegalStateException("STT model not in assets: ${variant.dirName}"))
            }
            _downloadProgress.value = SttModelState.Downloading(0f, 0, filesToDownload.size)
            filesToDownload.forEachIndexed { index, path ->
                val file = File(dir, path)
                if (!file.exists() || file.length() == 0L) {
                    val url = "$baseUrl/$path"
                    downloadFile(url, file)
                }
                val progress = (index + 1).toFloat() / filesToDownload.size
                _downloadProgress.value = SttModelState.Downloading(progress, index + 1, filesToDownload.size)
            }
            _downloadProgress.value = SttModelState.Ready(dir.absolutePath)
            dir
        }.onFailure {
            _downloadProgress.value = SttModelState.Error(it.message ?: "Download failed")
        }
    }

    /** Copy model from assets to [dir] if assets contain the variant. Returns true if copied. */
    private fun copyFromAssetsIfPresent(dir: File): Boolean {
        val assetPath = variant.dirName
        val list = try {
            context.assets.list(assetPath) ?: return false
        } catch (_: Exception) {
            return false
        }
        if (list.isEmpty()) return false
        dir.mkdirs()
        for (name in list) {
            val outFile = File(dir, name)
            if (outFile.exists() && outFile.length() > 0L) continue
            context.assets.open("$assetPath/$name").use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        // Sherpa-onnx layout used encoder_model.ort / decoder_model_merged.ort; create aliases for native lib
        if (variant == MoonshineVariant.SHERPA_ONNX_TINY_EN) {
            copyIfExists(File(dir, "encoder_model.ort"), File(dir, "encoder.ort"))
            copyIfExists(File(dir, "decoder_model_merged.ort"), File(dir, "decoder.ort"))
            copyIfExists(File(dir, "tokens.txt"), File(dir, "tokenizer.bin"))
        }
        // TINY_STREAMING_EN already has encoder.ort, tokenizer.bin, etc. — no renames needed
        return isModelDownloaded(dir)
    }

    private fun copyIfExists(src: File, dest: File) {
        if (src.exists() && src.length() > 0L) src.copyTo(dest, overwrite = true)
    }

    suspend fun getModelDir(): File? = withContext(Dispatchers.IO) {
        val dir = modelDir
        if (isModelDownloaded(dir)) dir else null
    }

    suspend fun isModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        isModelDownloaded(modelDir)
    }

    private fun isModelDownloaded(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val required = variant.requiredFiles().map { File(dir, it) }
        return required.all { it.exists() && it.length() > 0 }
    }

    private fun downloadFile(url: String, destination: File) {
        destination.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $url")
            response.body?.use { body ->
                destination.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
            } ?: throw Exception("Empty body: $url")
        }
    }

    enum class MoonshineVariant(
        val dirName: String,
        val repo: String,
        val files: List<String>,
        private val requiredFilesOverride: List<String>? = null
    ) {
        /**
         * Moonshine streaming tiny EN (.ort). Use assets/tiny-streaming-en.
         * Files: encoder.ort, frontend.ort, decoder_kv.ort, adapter.ort, cross_kv.ort, tokenizer.bin, streaming_config.json.
         */
        TINY_STREAMING_EN(
            dirName = "tiny-streaming-en",
            repo = "",
            files = emptyList(),
            requiredFilesOverride = listOf(
                "encoder.ort",
                "frontend.ort",
                "decoder_kv.ort",
                "adapter.ort",
                "cross_kv.ort",
                "tokenizer.bin",
                "streaming_config.json"
            )
        ),
        /**
         * Sherpa-ONNX Moonshine tiny EN (.ort). Use assets/sherpa-onnx-moonshine-tiny-en.
         * No download; copy from assets only.
         */
        SHERPA_ONNX_TINY_EN(
            dirName = "sherpa-onnx-moonshine-tiny-en",
            repo = "",
            files = emptyList(),
            requiredFilesOverride = listOf(
                "encoder_model.ort",
                "decoder_model_merged.ort",
                "tokens.txt"
            )
        ),
        /** ASR streaming sliding-window tiny (safetensors, from HF). */
        TINY(
            dirName = "moonshine-streaming-tiny",
            repo = "UsefulSensors/moonshine-streaming-tiny",
            files = listOf(
                "config.json",
                "generation_config.json",
                "model.safetensors",
                "preprocessor_config.json",
                "processor_config.json",
                "special_tokens_map.json",
                "tokenizer.json",
                "tokenizer_config.json"
            )
        ),
        /** ASR streaming sliding-window medium (safetensors, from HF). */
        MEDIUM(
            dirName = "moonshine-streaming-medium",
            repo = "UsefulSensors/moonshine-streaming-medium",
            files = listOf(
                "config.json",
                "generation_config.json",
                "model.safetensors",
                "preprocessor_config.json",
                "processor_config.json",
                "special_tokens_map.json",
                "tokenizer.json",
                "tokenizer_config.json"
            )
        );

        /** Files that must exist for model to be considered ready. */
        fun requiredFiles(): List<String> = requiredFilesOverride ?: files
    }
}

/** State of the Moonshine STT model (download / ready). */
sealed class SttModelState {
    object NotDownloaded : SttModelState()
    data class Downloading(val progress: Float, val fileIndex: Int = 0, val totalFiles: Int = 1) : SttModelState()
    data class Ready(val modelPath: String) : SttModelState()
    data class Error(val message: String) : SttModelState()
}
