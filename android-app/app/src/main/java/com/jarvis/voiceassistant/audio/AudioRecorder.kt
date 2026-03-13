package com.jarvis.voiceassistant.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Records audio from the device microphone.
 *
 * PCM 16-bit mono 16 kHz, suitable for Moonshine STT and similar pipelines.
 * Caller must hold RECORD_AUDIO permission.
 *
 * Step 5: Audio Recording
 */
interface IAudioRecorder {
    /**
     * Record for up to [durationSeconds] (or until [stop] is called).
     * @return Result with PCM ByteArray, or failure if recording could not start or was stopped early.
     */
    suspend fun record(durationSeconds: Int = 5): Result<ByteArray>

    /** Stop recording immediately. Safe to call from any thread. */
    fun stop()

    /** True while recording is active. */
    fun isRecording(): Boolean

    /** Elapsed seconds since recording started (0 if not recording). */
    fun getRecordingDuration(): Float
}

class AudioRecorder : IAudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var recordingStartTimeNs: Long = 0
    @Volatile
    private var stopRequested: Boolean = false

    override suspend fun record(durationSeconds: Int): Result<ByteArray> = withContext(Dispatchers.IO) {
        stopRequested = false
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize < 0) return@withContext Result.failure(IllegalStateException("Invalid buffer size: $bufferSize"))

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize.coerceAtLeast(MIN_BUFFER_BYTES)
            )
        } catch (e: SecurityException) {
            return@withContext Result.failure(e)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext Result.failure(IllegalStateException("AudioRecord not initialized"))
        }

        audioRecord = recorder
        recordingStartTimeNs = System.nanoTime()
        val maxBytes = (SAMPLE_RATE * durationSeconds * BYTES_PER_SAMPLE).toLong()
        val buffer = ByteArray(min(bufferSize, maxBytes.toInt().coerceAtLeast(1)))
        val audioData = mutableListOf<Byte>()

        try {
            recorder.startRecording()
            var totalRead = 0L
            while (totalRead < maxBytes && !stopRequested) {
                val toRead = min(buffer.size, (maxBytes - totalRead).toInt())
                val read = recorder.read(buffer, 0, toRead)
                when {
                    read > 0 -> {
                        audioData.addAll(buffer.sliceArray(0 until read).toList())
                        totalRead += read
                    }
                    read == AudioRecord.ERROR_INVALID_OPERATION -> break
                    read == AudioRecord.ERROR_BAD_VALUE -> break
                }
            }
            Result.success(audioData.toByteArray())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: Exception) { }
            recorder.release()
            audioRecord = null
        }
    }

    override fun stop() {
        stopRequested = true
        audioRecord?.apply {
            try {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
            } catch (_: Exception) { }
            release()
        }
        audioRecord = null
    }

    override fun isRecording(): Boolean = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    override fun getRecordingDuration(): Float {
        if (recordingStartTimeNs == 0L || !isRecording()) return 0f
        return (System.nanoTime() - recordingStartTimeNs) / 1_000_000_000f
    }

    companion object {
        /** 16 kHz, matches typical STT (e.g. Moonshine) requirements. */
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        private const val MIN_BUFFER_BYTES = 4096
    }
}
