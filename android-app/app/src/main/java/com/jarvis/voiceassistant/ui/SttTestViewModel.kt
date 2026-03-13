package com.jarvis.voiceassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.voiceassistant.audio.AudioRecorder
import com.jarvis.voiceassistant.stt.STTEngine
import com.jarvis.voiceassistant.stt.SttModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * ViewModel for the simple STT test screen.
 * Loads the model from assets, then record → transcribe on button press.
 */
class SttTestViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.applicationContext
    private val sttModelManager = SttModelManager(app)
    private val sttEngine = STTEngine(app) {
        withContext(Dispatchers.IO) { sttModelManager.ensureModelExists().getOrNull() }
    }
    private val audioRecorder = AudioRecorder()

    private val _modelStatus = MutableStateFlow<SttTestModelStatus>(SttTestModelStatus.Loading)
    val modelStatus: StateFlow<SttTestModelStatus> = _modelStatus.asStateFlow()

    private val _recordState = MutableStateFlow<SttTestRecordState>(SttTestRecordState.Idle)
    val recordState: StateFlow<SttTestRecordState> = _recordState.asStateFlow()

    private val _transcript = MutableStateFlow<String?>(null)
    val transcript: StateFlow<String?> = _transcript.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            loadModelAndEngine()
        }
    }

    private suspend fun loadModelAndEngine() {
        _modelStatus.value = SttTestModelStatus.Loading
        _errorMessage.value = null
        val modelResult = sttModelManager.ensureModelExists()
        if (modelResult.isFailure) {
            _modelStatus.value = SttTestModelStatus.Error(modelResult.exceptionOrNull()?.message ?: "Model load failed")
            return
        }
        val initResult = sttEngine.initialize()
        if (initResult.isFailure) {
            _modelStatus.value = SttTestModelStatus.Error(initResult.exceptionOrNull()?.message ?: "STT init failed")
            return
        }
        _modelStatus.value = SttTestModelStatus.Ready
    }

    /** Record for [durationSeconds] then transcribe. Call from UI when mic permission is granted. */
    fun recordAndTranscribe(durationSeconds: Int = 5) {
        viewModelScope.launch {
            if (_modelStatus.value !is SttTestModelStatus.Ready) {
                _errorMessage.value = "Model not ready"
                return@launch
            }
            _recordState.value = SttTestRecordState.Recording
            _errorMessage.value = null
            _transcript.value = null
            val recordResult = audioRecorder.record(durationSeconds)
            if (recordResult.isFailure) {
                _recordState.value = SttTestRecordState.Idle
                _errorMessage.value = "Recording failed: ${recordResult.exceptionOrNull()?.message}"
                return@launch
            }
            _recordState.value = SttTestRecordState.Transcribing
            val audioData = recordResult.getOrNull()!!
            val transcribeResult = sttEngine.transcribe(audioData)
            _recordState.value = SttTestRecordState.Idle
            if (transcribeResult.isSuccess) {
                _transcript.value = transcribeResult.getOrNull()?.ifBlank { "(empty)" } ?: "(empty)"
            } else {
                _errorMessage.value = "Transcribe failed: ${transcribeResult.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearTranscript() {
        _transcript.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        audioRecorder.stop()
        sttEngine.release()
    }
}

sealed class SttTestModelStatus {
    object Loading : SttTestModelStatus()
    object Ready : SttTestModelStatus()
    data class Error(val message: String) : SttTestModelStatus()
}

sealed class SttTestRecordState {
    object Idle : SttTestRecordState()
    object Recording : SttTestRecordState()
    object Transcribing : SttTestRecordState()
}
