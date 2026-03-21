package com.jarvis.voiceassistant.audio

import kotlin.math.sqrt

/**
 * Utilities for raw PCM 16-bit mono buffers (e.g. from [AudioRecorder]).
 */
object PcmAudioUtils {

    /** RMS of 16-bit signed mono PCM (amplitude 0–32767). */
    fun pcm16MonoRms(pcm: ByteArray): Double {
        if (pcm.size < 2) return 0.0
        val n = pcm.size / 2
        var sumSq = 0.0
        for (i in 0 until n) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val sample = (hi shl 8 or lo).toShort().toInt()
            val s = sample.toDouble()
            sumSq += s * s
        }
        return sqrt(sumSq / n)
    }

    /** True if this chunk is likely silence (no speech / room noise only). */
    fun isLikelySilent(pcm: ByteArray, rmsThreshold: Double = DEFAULT_SILENCE_RMS): Boolean =
        pcm.size < 2 || pcm16MonoRms(pcm) < rmsThreshold

    /**
     * Default RMS threshold for 16-bit mono (~quiet room vs speech).
     * Tune if auto-stop is too aggressive or too lax.
     */
    const val DEFAULT_SILENCE_RMS: Double = 350.0
}
