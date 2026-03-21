package com.jarvis.voiceassistant.ui

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jarvis.voiceassistant.data.AssistantState
import com.jarvis.voiceassistant.data.Block
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        viewModel = ChatViewModel(app)
    }

    @Test
    fun `initial document has no blocks`() = runTest {
        viewModel.document.test {
            val doc = awaitItem()
            assertTrue(doc.blocks.isEmpty())
        }
    }

    @Test
    fun `initial state is idle`() = runTest {
        viewModel.assistantState.test {
            val state = awaitItem()
            assertTrue(state is AssistantState.Idle)
        }
    }

    @Test
    fun `appendBlock adds text block to document`() = runTest {
        viewModel.document.test {
            skipItems(1) // skip initial
            viewModel.appendBlock(Block.Text("Hello note"))
            val doc = awaitItem()
            assertEquals(1, doc.blocks.size)
            assertTrue(doc.blocks[0] is Block.Text)
            assertEquals("Hello note", (doc.blocks[0] as Block.Text).content)
        }
    }

    @Test
    fun `appendBlock adds todo block to document`() = runTest {
        viewModel.document.test {
            skipItems(1)
            viewModel.appendBlock(Block.Todo(mutableListOf(com.jarvis.voiceassistant.data.TodoItem("Buy milk", false))))
            val doc = awaitItem()
            assertEquals(1, doc.blocks.size)
            assertTrue(doc.blocks[0] is Block.Todo)
            assertEquals(1, (doc.blocks[0] as Block.Todo).items.size)
            assertEquals("Buy milk", (doc.blocks[0] as Block.Todo).items[0].text)
        }
    }

    @Test
    fun `showSnackbar sets snackbar message`() = runTest {
        viewModel.snackbarMessage.test {
            skipItems(1)
            viewModel.showSnackbar("Added todo.")
            assertEquals("Added todo.", awaitItem())
        }
    }

    @Test
    fun `clearSnackbar clears message`() = runTest {
        viewModel.snackbarMessage.test {
            skipItems(1)
            viewModel.showSnackbar("Test")
            assertEquals("Test", awaitItem())
            viewModel.clearSnackbar()
            assertNull(awaitItem())
        }
    }

    @Test
    fun `getTodoListSummary returns empty when no todos`() {
        assertEquals("Your list is empty.", viewModel.getTodoListSummary())
    }

    @Test
    fun `getTodoListSummary returns list when document has todos`() {
        viewModel.appendBlock(Block.Todo(mutableListOf(
            com.jarvis.voiceassistant.data.TodoItem("One", false),
            com.jarvis.voiceassistant.data.TodoItem("Two", true)
        )))

        val summary = viewModel.getTodoListSummary()
        assertTrue(summary.contains("One"))
        assertTrue(summary.contains("Two"))
        assertTrue(summary.contains("✓") || summary.contains("○"))
    }

    @Test
    fun `updateState changes assistant state`() = runTest {
        viewModel.updateState(AssistantState.Listening)

        viewModel.assistantState.test {
            val state = awaitItem()
            assertTrue(state is AssistantState.Listening)
        }
    }

    @Test
    fun `toggleTodo flips completed state`() = runTest {
        viewModel.appendBlock(Block.Todo(mutableListOf(com.jarvis.voiceassistant.data.TodoItem("Task", false))))

        viewModel.document.test {
            skipItems(2) // initial + after appendBlock
            viewModel.toggleTodo(0, 0)
            val doc = awaitItem()
            val todo = doc.blocks[0] as Block.Todo
            assertTrue(todo.items[0].completed)
        }
    }
}
