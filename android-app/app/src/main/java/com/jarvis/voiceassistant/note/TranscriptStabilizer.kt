package com.jarvis.voiceassistant.note

/**
 * Configuration for [TranscriptStabilizer].
 */
data class TranscriptStabilizerConfig(
    /** Do not commit the last N words as stable (they change most often). */
    val keepUnstableWords: Int = 2
)

/**
 * Snapshot after processing one STT segment (or empty refresh).
 *
 * @property committedStable Monotonic prefix safe for Phase 2 LLM deltas.
 * @property pending Unstable tail after [committedStable] on the latest running text.
 * @property displayLine Bottom-pane line: stable + space + pending (for convenience).
 */
data class StabilizationSnapshot(
    val committedStable: String,
    val pending: String,
    val displayLine: String
)

/**
 * Streaming STT stabilizer:
 * - **Cumulative** [runningTranscript]: each chunk is appended (normalized spacing).
 * - **Stable candidate** = current running text with the last [keepUnstableWords] words trimmed off,
 *   then [growCommittedMonotonic] so [committedStable] only grows.
 *
 * ### Why not LCP across a history of cumulative snapshots?
 * If each history entry is a **prefix chain** (chunk1, chunk1+chunk2, …), then every snapshot agrees on
 * the **shortest** entry’s prefix. Word-LCP across the whole set is therefore **that shortest string**
 * (usually **only the first chunk**), not “everything agreed so far” for the full note. So stable would
 * stay stuck at ~first sentence forever. [longestCommonPrefixWords] is still useful for **alternative**
 * full hypotheses (e.g. two strings “… brea” vs “… bread”); this class uses **trim tail on running** for
 * the append-only cumulative model instead.
 */
class TranscriptStabilizer(
    private val config: TranscriptStabilizerConfig = TranscriptStabilizerConfig()
) {

    /** Cumulative note text: chunk1 + chunk2 + … (normalized spaces). */
    private var runningTranscript: String = ""

    private var committedStable: String = ""

    fun reset() {
        runningTranscript = ""
        committedStable = ""
    }

    fun committedLength(): Int = committedStable.length

    /** Exposed for tests / debug — equals cumulative concat of chunks. */
    fun runningSnapshot(): String = runningTranscript

    /**
     * Feed one STT chunk; it is **appended** to the running transcript (cumulative model).
     */
    fun onSegment(segment: String): StabilizationSnapshot {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) {
            return snapshot(extractPending(runningTranscript, committedStable))
        }

        runningTranscript = if (runningTranscript.isEmpty()) {
            trimmed
        } else {
            TextStabilization.normalizeWhitespace("$runningTranscript $trimmed")
        }

        val normalized = TextStabilization.normalizeWhitespace(runningTranscript)
        val candidate = TextStabilization.trimLastWords(normalized, config.keepUnstableWords)

        committedStable = growCommittedMonotonic(candidate)

        val pending = extractPending(runningTranscript, committedStable)
        return snapshot(pending)
    }

    /**
     * Only grows [committedStable]; never shrinks (STT corrections stay in [pending]).
     */
    private fun growCommittedMonotonic(candidate: String): String {
        if (candidate.isEmpty()) return committedStable
        if (committedStable.isEmpty()) return candidate
        if (candidate.length > committedStable.length && candidate.startsWith(committedStable)) {
            return candidate
        }
        return committedStable
    }

    private fun extractPending(latest: String, committed: String): String {
        if (latest.isEmpty()) return ""
        if (committed.isEmpty()) return latest
        return if (latest.startsWith(committed)) {
            latest.removePrefix(committed).trimStart()
        } else {
            latest
        }
    }

    private fun snapshot(pending: String = ""): StabilizationSnapshot {
        val pend = pending.trim()
        val display = buildDisplay(committedStable, pend)
        return StabilizationSnapshot(
            committedStable = committedStable,
            pending = pend,
            displayLine = display
        )
    }

    private fun buildDisplay(stable: String, pending: String): String {
        return when {
            stable.isNotEmpty() && pending.isNotEmpty() -> "$stable $pending"
            stable.isNotEmpty() -> stable
            pending.isNotEmpty() -> pending
            else -> ""
        }
    }
}
