package com.jarvis.voiceassistant.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.voiceassistant.audio.AudioRecorder
import com.jarvis.voiceassistant.audio.PcmAudioUtils
import com.jarvis.voiceassistant.note.TranscriptStabilizer
import com.jarvis.voiceassistant.stt.STTEngine
import com.jarvis.voiceassistant.stt.SttModelManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Init state (STT only for note session). */
sealed class NoteSessionInitState {
    object Loading : NoteSessionInitState()
    object Ready : NoteSessionInitState()
    data class Error(val message: String) : NoteSessionInitState()
}

/** Capture loop state for Phase 1 (no LLM). */
sealed class NoteCaptureState {
    object Idle : NoteCaptureState()
    /** Recording a chunk of audio. */
    object Listening : NoteCaptureState()
    /** Running Moonshine on the chunk. */
    object Transcribing : NoteCaptureState()
    /** User tapped stop. */
    object EndedByUser : NoteCaptureState()
    /** No sound above threshold for [SILENCE_TIMEOUT_MS]. */
    object EndedBySilence : NoteCaptureState()
}

/**
 * Phase 1: continuous STT note session — raw transcript + optional stabilization.
 * Chunks audio → transcribe → [TranscriptStabilizer] → stable / pending + raw log.
 * Stops when user stops, or after [SILENCE_TIMEOUT_MS] of silence (RMS + empty STT).
 */
class NoteSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.applicationContext
    private val sttModelManager = SttModelManager(app)
    private val sttEngine = STTEngine(app) { sttModelManager.getModelDir() }
    private val audioRecorder = AudioRecorder()

    private val stabilizer = TranscriptStabilizer()

    private val _initState = MutableStateFlow<NoteSessionInitState>(NoteSessionInitState.Loading)
    val initState: StateFlow<NoteSessionInitState> = _initState.asStateFlow()

    /** Concatenated STT segments (debug / optional UI). */
    private val _rawTranscript = MutableStateFlow("")
    val rawTranscript: StateFlow<String> = _rawTranscript.asStateFlow()

    /** Committed stable prefix (safe for Phase 2 LLM deltas). */
    private val _committedStable = MutableStateFlow("")
    val committedStable: StateFlow<String> = _committedStable.asStateFlow()

    /** Unstable tail after [committedStable]. */
    private val _pendingTranscript = MutableStateFlow("")
    val pendingTranscript: StateFlow<String> = _pendingTranscript.asStateFlow()

    /** Phase 2: characters of [committedStable] already sent to LLM. */
    private val _llmProcessedOffset = MutableStateFlow(0)
    val llmProcessedOffset: StateFlow<Int> = _llmProcessedOffset.asStateFlow()

    /** Top pane: placeholder until Phase 2 LLM markdown. */
    private val _processedPlaceholder = MutableStateFlow(PROCESSED_PLACEHOLDER)
    val processedPlaceholder: StateFlow<String> = _processedPlaceholder.asStateFlow()

    private val _captureState = MutableStateFlow<NoteCaptureState>(NoteCaptureState.Idle)
    val captureState: StateFlow<NoteCaptureState> = _captureState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    @Volatile
    private var stopRequested: Boolean = false

    private var captureJob: Job? = null

    init {
        viewModelScope.launch {
            val sttOk = sttModelManager.ensureModelExists()
            if (sttOk.isFailure) {
                _initState.value = NoteSessionInitState.Error(sttOk.exceptionOrNull()?.message ?: "STT model missing")
                return@launch
            }
            sttEngine.initialize()
                .onSuccess { _initState.value = NoteSessionInitState.Ready }
                .onFailure { _initState.value = NoteSessionInitState.Error(it.message ?: "STT init failed") }
        }
    }

    /** Start continuous capture until [stopNoteSession] or silence timeout. */
    fun startNoteSession() {
        if (_initState.value !is NoteSessionInitState.Ready) return
        if (captureJob?.isActive == true) return

        stopRequested = false
        var lastNonSilentMs = System.currentTimeMillis()
        resetStabilizer()
        _captureState.value = NoteCaptureState.Listening

        captureJob = viewModelScope.launch {
            var endedBySilence = false
            try {
                while (!stopRequested) {
                    _captureState.value = NoteCaptureState.Listening
                    val recordResult = audioRecorder.record(CHUNK_SECONDS)
                    if (stopRequested) break

                    val pcm = recordResult.getOrNull()
                    if (pcm == null || pcm.isEmpty()) continue

                    val rmsSilent = PcmAudioUtils.isLikelySilent(pcm, PcmAudioUtils.DEFAULT_SILENCE_RMS)

                    _captureState.value = NoteCaptureState.Transcribing
                    val text = if (pcm.size >= STTEngine.MIN_AUDIO_BYTES) {
                        sttEngine.transcribe(pcm).getOrNull()?.trim().orEmpty()
                    } else {
                        ""
                    }

                    val end = System.currentTimeMillis()
                    if (!rmsSilent || text.isNotBlank()) {
                        lastNonSilentMs = end
                    }
                    if (text.isNotBlank()) {
                        appendRaw(text)
                        applyStabilization(text)
                    }

                    if (end - lastNonSilentMs >= SILENCE_TIMEOUT_MS) {
                        Log.d(TAG, "note_session ended: ${SILENCE_TIMEOUT_MS}ms silence")
                        endedBySilence = true
                        _captureState.value = NoteCaptureState.EndedBySilence
                        showSnackbar("Stopped after 2 min silence.")
                        break
                    }

                    if (!stopRequested) {
                        _captureState.value = NoteCaptureState.Listening
                    }
                }
                when {
                    endedBySilence -> { /* already set */ }
                    stopRequested -> _captureState.value = NoteCaptureState.EndedByUser
                    else -> _captureState.value = NoteCaptureState.Idle
                }
            } catch (t: Throwable) {
                Log.e(TAG, "note_session error", t)
                showSnackbar(t.message ?: "Capture error")
                _captureState.value = NoteCaptureState.Idle
            } finally {
                captureJob = null
            }
        }
    }

    private fun appendRaw(segment: String) {
        val cur = _rawTranscript.value
        _rawTranscript.value = if (cur.isBlank()) segment else "$cur $segment"
    }

    private fun applyStabilization(segment: String) {
        val snap = stabilizer.onSegment(segment)
        _committedStable.value = snap.committedStable
        _pendingTranscript.value = snap.pending
    }

    private fun resetStabilizer() {
        stabilizer.reset()
        _rawTranscript.value = ""
        _committedStable.value = ""
        _pendingTranscript.value = ""
        _llmProcessedOffset.value = 0
    }

    fun stopNoteSession() {
        stopRequested = true
        audioRecorder.stop()
        if (_captureState.value == NoteCaptureState.Listening ||
            _captureState.value == NoteCaptureState.Transcribing
        ) {
            _captureState.value = NoteCaptureState.EndedByUser
        }
    }

    fun resetEndedStateToIdle() {
        when (_captureState.value) {
            is NoteCaptureState.EndedBySilence, is NoteCaptureState.EndedByUser ->
                _captureState.value = NoteCaptureState.Idle
            else -> { }
        }
    }

    fun showSnackbar(text: String) {
        if (text.isNotBlank()) _snackbarMessage.value = text
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearRawTranscript() {
        resetStabilizer()
    }

    override fun onCleared() {
        stopRequested = true
        audioRecorder.stop()
        sttEngine.release()
    }

    companion object {
        private const val TAG = "NoteSession"
        private const val CHUNK_SECONDS = 5
        private const val SILENCE_TIMEOUT_MS = 2 * 60 * 1000L // 2 minutes
        private const val PROCESSED_PLACEHOLDER =
            "Processed note (markdown) will appear here in Phase 2."
    }
}
