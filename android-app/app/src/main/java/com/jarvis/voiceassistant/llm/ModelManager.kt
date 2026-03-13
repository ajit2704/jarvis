package com.jarvis.voiceassistant.llm

import android.content.Context
import com.jarvis.voiceassistant.data.ModelLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.SocketException
import java.util.concurrent.TimeUnit

/**
 * Manages LLM model download and loading.
 *
 * Default: SmolLM-1.7B-Instruct (Q4_K_M, ~1.06 GB) — smaller than 3B, better chance to load on Android.
 * Track download/load progress; provide model file location.
 *
 * @see https://huggingface.co/prithivMLmods/SmolLM-1.7B-Instruct-GGUF
 */
interface IModelManager {
    val modelLoadState: Flow<ModelLoadState>
    suspend fun ensureModelExists(): Result<File>
    suspend fun getModelFile(): File?
    suspend fun isModelDownloaded(): Boolean
    suspend fun deleteModel(): Result<Unit>
}

class ModelManager(
    private val context: Context,
    private val modelUrl: String = GGUF_URL_DEFAULT,
    private val modelFileName: String = GGUF_FILENAME_DEFAULT,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)   // ~2.1 GB; allow long read for slow networks
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
) : IModelManager {

    private val _modelLoadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotLoaded)
    override val modelLoadState: Flow<ModelLoadState> = _modelLoadState

    private val modelFile: File
        get() = File(context.filesDir, MODEL_SUBDIR).apply { mkdirs() }.let { File(it, modelFileName) }

    override suspend fun ensureModelExists(): Result<File> = withContext(Dispatchers.IO) {
        val file = modelFile
        // Reject truncated cache: must be at least EXPECTED_MIN_MODEL_BYTES (Q4 ~1.6GB, Q5 ~2.1GB)
        if (file.exists() && file.length() >= EXPECTED_MIN_MODEL_BYTES) {
            _modelLoadState.value = ModelLoadState.Ready(file.absolutePath)
            return@withContext Result.success(file)
        }
        if (file.exists() && file.length() < EXPECTED_MIN_MODEL_BYTES) {
            file.delete()
        }
        val result = runCatching {
            // Prefer copying from assets (app/src/main/assets/smolm-3b/...)
            context.assets.open(ASSET_GGUF_PATH).use { input ->
                _modelLoadState.value = ModelLoadState.Downloading(0f)
                val total = input.available().toLong().coerceAtLeast(1L)
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        copied += read
                        _modelLoadState.value = ModelLoadState.Downloading((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            validateModelSize(file, "asset copy")
            _modelLoadState.value = ModelLoadState.Ready(file.absolutePath)
            file
        }.recoverCatching {
            // Fallback: download from URL with retries (connection abort / timeouts are common on mobile)
            if (file.exists()) file.delete()
            var lastError: Throwable? = null
            repeat(DOWNLOAD_MAX_RETRIES) { attempt ->
                runCatching {
                    _modelLoadState.value = ModelLoadState.Downloading(0f)
                    downloadFile(modelUrl, file) { progress ->
                        _modelLoadState.value = ModelLoadState.Downloading(progress)
                    }
                    validateModelSize(file, "download")
                    file
                }.onSuccess { return@recoverCatching it }
                    .onFailure { lastError = it }
                if (file.exists()) file.delete()
                if (attempt < DOWNLOAD_MAX_RETRIES - 1) {
                    delay((1000L * (attempt + 1)).coerceAtMost(5000L))
                }
            }
            throw lastError ?: Exception("Download failed")
        }
        result.onFailure {
            if (file.exists() && file.length() < EXPECTED_MIN_MODEL_BYTES) file.delete()
            _modelLoadState.value = ModelLoadState.Error(userFriendlyMessage(it))
        }
        return@withContext result.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(Exception(userFriendlyMessage(it))) }
        )
    }

    /** Map network/IO errors to a message suitable for the UI. */
    private fun userFriendlyMessage(t: Throwable): String {
        val msg = t.message?.lowercase() ?: ""
        return when {
            msg.contains("connection abort") || msg.contains("connection reset") ||
            t is SocketException || msg.contains("socket") || msg.contains("connection") ->
                "Download failed. Check your connection and try again."
            msg.contains("timeout") || msg.contains("timed out") ->
                "Download timed out. Check your connection and try again."
            msg.contains("truncated") || msg.contains("incomplete") ->
                "Download incomplete. Try again."
            else -> t.message ?: "Model load failed"
        }
    }

    /** Throws if file is truncated; prevents -422 from loading incomplete GGUF. */
    private fun validateModelSize(file: File, source: String) {
        if (!file.exists() || file.length() < EXPECTED_MIN_MODEL_BYTES) {
            file.delete()
            throw IllegalStateException(
                "Model incomplete ($source): ${file.length()} bytes (need at least $EXPECTED_MIN_MODEL_BYTES). " +
                "Delete app data or the model file and try again."
            )
        }
    }

    override suspend fun getModelFile(): File? = withContext(Dispatchers.IO) {
        val file = modelFile
        if (file.exists() && file.length() >= EXPECTED_MIN_MODEL_BYTES) file else null
    }

    override suspend fun isModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        modelFile.exists() && modelFile.length() >= EXPECTED_MIN_MODEL_BYTES
    }

    override suspend fun deleteModel(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (modelFile.exists()) modelFile.delete()
            _modelLoadState.value = ModelLoadState.NotLoaded
        }
    }

    private fun downloadFile(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit
    ) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            onProgress((totalRead.toFloat() / contentLength).coerceIn(0f, 1f))
                        }
                    }
                    if (contentLength > 0 && totalRead < contentLength) {
                        throw IllegalStateException("Download truncated: $totalRead < $contentLength bytes")
                    }
                }
            }
        }
    }

    companion object {
        private const val MODEL_SUBDIR = "models"
        private const val DOWNLOAD_MAX_RETRIES = 3
        /** Minimum size for SmolLM-1.7B-Instruct Q4_K_M (~1.06 GB). Reject truncated files to avoid -422. */
        private const val EXPECTED_MIN_MODEL_BYTES = 900_000_000L
        /** Asset path for bundled GGUF (copy to filesDir); optional. */
        private const val ASSET_GGUF_PATH = "smolm-17b/SmolLM-1.7B-Instruct.Q4_K_M.gguf"
        /** Default: SmolLM-1.7B-Instruct Q4_K_M (~1.06 GB) — smaller, more likely to load on Android. */
        const val GGUF_URL_DEFAULT = "https://huggingface.co/unsloth/SmolLM2-1.7B-Instruct-GGUF/blob/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf"
        const val GGUF_FILENAME_DEFAULT = "SmolLM-1.7B-Instruct.Q4_K_M.gguf"
    }
}
