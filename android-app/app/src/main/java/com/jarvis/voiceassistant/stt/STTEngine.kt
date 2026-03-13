package com.jarvis.voiceassistant.stt

import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcript
import ai.moonshine.voice.Transcriber
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Speech-to-Text engine using Moonshine (ai.moonshine.voice SDK).
 *
 * Prefers .ort models from assets/sherpa-onnx-moonshine-tiny-en (encoder_model.ort,
 * decoder_model_merged.ort, tokens.txt). Uses model directory from [SttModelManager].
 * Audio format: PCM 16-bit mono 16 kHz.
 *
 * @see https://github.com/moonshine-ai/moonshine/tree/main/android
 * @see https://github.com/k2-fsa/sherpa-onnx
 */
interface ISTTEngine {
    suspend fun initialize(): Result<Unit>
    suspend fun transcribe(audioData: ByteArray): Result<String>
    fun isInitialized(): Boolean
    fun release()
}

class STTEngine(
    private val context: Context,
    private val modelDirProvider: suspend () -> File?
) : ISTTEngine {

    private var transcriber: Transcriber? = null

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = modelDirProvider() ?: throw IllegalStateException("STT model directory not available")
            if (!dir.isDirectory || !dir.exists()) throw IllegalStateException("Model path is not a directory: ${dir.absolutePath}")
            val transcriber = Transcriber()
            // Sherpa-onnx tiny-en .ort is a streaming model; use TINY_STREAMING so transcribeWithoutStreaming works
            transcriber.loadFromFiles(dir.absolutePath, JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING)
            this@STTEngine.transcriber = transcriber
        }
    }

    override suspend fun transcribe(audioData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val t = transcriber
        if (t == null) return@withContext Result.failure(IllegalStateException("STTEngine not initialized"))
        if (audioData.size < MIN_AUDIO_BYTES) {
            return@withContext Result.failure(
                IllegalArgumentException("Audio too short: ${audioData.size} bytes (min ${MIN_AUDIO_BYTES} for ~1s at 16kHz)")
            )
        }
        runCatching {
            val floatAudio = pcm16ToFloat(audioData)
            val transcript: Transcript? = t.transcribeWithoutStreaming(floatAudio, SAMPLE_RATE)
            if (transcript == null || transcript.lines == null) return@runCatching ""
            transcript.text()?.trim() ?: ""
        }.map { it.ifBlank { "" } }
    }

    override fun isInitialized(): Boolean = transcriber != null

    override fun release() {
        transcriber = null
    }

    private fun pcm16ToFloat(pcm: ByteArray): FloatArray {
        val numSamples = pcm.size / 2
        val out = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo
            out[i] = (sample.toShort().toInt() / 32768f)
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 16000
        /** Minimum bytes (PCM 16-bit mono 16kHz) ~1 second to avoid native "vector" errors. */
        private const val MIN_AUDIO_BYTES = SAMPLE_RATE * 2
    }
}
