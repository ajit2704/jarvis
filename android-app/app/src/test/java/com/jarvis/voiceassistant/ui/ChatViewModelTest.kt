package com.jarvis.voiceassistant.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.jarvis.voiceassistant.data.AssistantState
import com.jarvis.voiceassistant.data.Message
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var viewModel: ChatViewModel
    
    @Before
    fun setup() {
        viewModel = ChatViewModel()
    }
    
    @Test
    fun `initial state has no messages`() = runTest {
        viewModel.messages.test {
            val messages = awaitItem()
            assertTrue(messages.isEmpty())
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
    fun `addUserMessage adds message to list`() = runTest {
        viewModel.addUserMessage("Hello")
        
        viewModel.messages.test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("Hello", messages[0].text)
            assertTrue(messages[0].isUser)
        }
    }
    
    @Test
    fun `addAssistantMessage adds message to list`() = runTest {
        viewModel.addAssistantMessage("Hi there!")
        
        viewModel.messages.test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("Hi there!", messages[0].text)
            assertFalse(messages[0].isUser)
        }
    }
    
    @Test
    fun `messages are added in order`() = runTest {
        viewModel.addUserMessage("First")
        viewModel.addAssistantMessage("Second")
        viewModel.addUserMessage("Third")
        
        viewModel.messages.test {
            val messages = awaitItem()
            assertEquals(3, messages.size)
            assertEquals("First", messages[0].text)
            assertEquals("Second", messages[1].text)
            assertEquals("Third", messages[2].text)
        }
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
    fun `clearMessages removes all messages`() = runTest {
        viewModel.addUserMessage("Test")
        viewModel.addAssistantMessage("Test 2")
        viewModel.clearMessages()
        
        viewModel.messages.test {
            val messages = awaitItem()
            assertTrue(messages.isEmpty())
        }
    }
    
    @Test
    fun `getLastMessage returns null when empty`() {
        assertNull(viewModel.getLastMessage())
    }
    
    @Test
    fun `getLastMessage returns last message`() {
        viewModel.addUserMessage("First")
        viewModel.addAssistantMessage("Last")
        
        val lastMessage = viewModel.getLastMessage()
        assertNotNull(lastMessage)
        assertEquals("Last", lastMessage?.text)
    }
    
    @Test
    fun `state transitions work correctly`() = runTest {
        viewModel.updateState(AssistantState.Listening)
        viewModel.updateState(AssistantState.ProcessingSTT)
        viewModel.updateState(AssistantState.ProcessingLLM)
        viewModel.updateState(AssistantState.Idle)
        
        viewModel.assistantState.test {
            // Should receive all state updates
            assertEquals(AssistantState.Idle, awaitItem())
        }
    }
}

