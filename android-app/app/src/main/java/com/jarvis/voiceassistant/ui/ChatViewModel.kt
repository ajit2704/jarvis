package com.jarvis.voiceassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.voiceassistant.assistant.AssistantController
import com.jarvis.voiceassistant.audio.AudioRecorder
import com.jarvis.voiceassistant.data.AssistantState
import com.jarvis.voiceassistant.data.Block
import com.jarvis.voiceassistant.data.Document
import com.jarvis.voiceassistant.llm.LLMEngine
import com.jarvis.voiceassistant.llm.ModelManager
import com.jarvis.voiceassistant.stt.STTEngine
import com.jarvis.voiceassistant.stt.SttModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

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

    private val _document = MutableStateFlow(
        Document(id = UUID.randomUUID().toString(), title = "My Workspace", blocks = mutableListOf())
    )
    val document: StateFlow<Document> = _document.asStateFlow()

    /** One-off message for Snackbar (e.g. "Added todo", list summary, error). No chat. */
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

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
            try {
                assistantController.handleUserSpeech(durationSeconds)
            } catch (t: Throwable) {
                _assistantState.value = AssistantState.Error(t.message ?: "Voice pipeline failed")
            }
        }
    }

    /** Show feedback in Snackbar (replaces chat). */
    fun showSnackbar(text: String) {
        if (text.isNotBlank()) _snackbarMessage.value = text
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun updateState(state: AssistantState) {
        _assistantState.value = state
    }

    fun isListening(): Boolean = _assistantState.value == AssistantState.Listening

    fun isProcessing(): Boolean = _assistantState.value is AssistantState.ProcessingSTT ||
        _assistantState.value is AssistantState.ProcessingLLM

    /** Append a block to the workspace document (e.g. from ToolExecutor). Emits new document so UI updates. */
    fun appendBlock(block: Block) {
        val doc = _document.value
        _document.value = Document(
            id = doc.id,
            title = doc.title,
            blocks = (doc.blocks + block).toMutableList()
        )
    }

    /** Toggle completed state of a todo item. blockIndex = index in document.blocks; itemIndex = index in Todo.items. */
    fun toggleTodo(blockIndex: Int, itemIndex: Int) {
        val doc = _document.value
        val block = doc.blocks.getOrNull(blockIndex) as? Block.Todo ?: return
        val item = block.items.getOrNull(itemIndex) ?: return
        val newItems = block.items.toMutableList().apply { set(itemIndex, item.copy(completed = !item.completed)) }
        val newBlocks = doc.blocks.toMutableList().apply { set(blockIndex, Block.Todo(newItems)) }
        _document.value = Document(id = doc.id, title = doc.title, blocks = newBlocks)
    }

    /** Summary of all todo items (for "show my list" voice → Snackbar). */
    fun getTodoListSummary(): String {
        val todos = _document.value.blocks
            .filterIsInstance<Block.Todo>()
            .flatMap { it.items }
        if (todos.isEmpty()) return "Your list is empty."
        return todos.mapIndexed { i, t -> "${i + 1}. ${if (t.completed) "✓" else "○"} ${t.text}" }.joinToString("\n")
    }

    override fun onCleared() {
        assistantController.release()
    }
}
