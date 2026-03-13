package com.jarvis.voiceassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.voiceassistant.assistant.AssistantController
import com.jarvis.voiceassistant.audio.AudioRecorder
import com.jarvis.voiceassistant.data.AssistantState
import com.jarvis.voiceassistant.data.Message
import com.jarvis.voiceassistant.llm.LLMEngine
import com.jarvis.voiceassistant.llm.ModelManager
import com.jarvis.voiceassistant.stt.STTEngine
import com.jarvis.voiceassistant.stt.SttModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Pipeline init state for chat screen. */
sealed class ChatInitState {
    object Loading : ChatInitState()
    object Ready : ChatInitState()
    data class Error(val message: String) : ChatInitState()
}

/**
 * ViewModel for chat UI: messages, assistant state, and voice pipeline.
 * Creates and owns AssistantController, STT/LLM engines, and recorder.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.applicationContext

    private val modelManager = ModelManager(app)
    private val sttModelManager = SttModelManager(app)
    private val sttEngine = STTEngine(app) { sttModelManager.getModelDir() }
    private val audioRecorder = AudioRecorder()
    private val llmEngine = LLMEngine(app)
    private val assistantController = AssistantController(
        audioRecorder = audioRecorder,
        sttModelManager = sttModelManager,
        sttEngine = sttEngine,
        llmEngine = llmEngine,
        modelManager = modelManager,
        viewModel = this
    )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _initState = MutableStateFlow<ChatInitState>(ChatInitState.Loading)
    val initState: StateFlow<ChatInitState> = _initState.asStateFlow()

    init {
        viewModelScope.launch {
            assistantController.initialize()
                .onSuccess { _initState.value = ChatInitState.Ready }
                .onFailure { _initState.value = ChatInitState.Error(it.message ?: "Init failed") }
        }
    }

    /** Start recording → STT → LLM and update messages. */
    fun startVoiceInput(durationSeconds: Int = 5) {
        viewModelScope.launch {
            assistantController.handleUserSpeech(durationSeconds)
        }
    }

    fun addUserMessage(text: String) {
        if (text.isNotBlank()) {
            val message = Message(text = text.trim(), isUser = true)
            _messages.value = _messages.value + message
        }
    }

    fun addAssistantMessage(text: String) {
        if (text.isNotBlank()) {
            val message = Message(text = text.trim(), isUser = false)
            _messages.value = _messages.value + message
        }
    }

    fun updateState(state: AssistantState) {
        _assistantState.value = state
    }

    fun isListening(): Boolean = _assistantState.value == AssistantState.Listening

    fun isProcessing(): Boolean = _assistantState.value is AssistantState.ProcessingSTT ||
        _assistantState.value is AssistantState.ProcessingLLM

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun getLastMessage(): Message? = _messages.value.lastOrNull()

    override fun onCleared() {
        assistantController.release()
    }
}
