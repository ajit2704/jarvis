package com.jarvis.voiceassistant.data

import org.junit.Assert.*
import org.junit.Test

class AssistantStateTest {
    
    @Test
    fun `idle state is correct`() {
        val state = AssistantState.Idle
        assertTrue(state is AssistantState.Idle)
    }
    
    @Test
    fun `listening state is correct`() {
        val state = AssistantState.Listening
        assertTrue(state is AssistantState.Listening)
    }
    
    @Test
    fun `processing STT state is correct`() {
        val state = AssistantState.ProcessingSTT
        assertTrue(state is AssistantState.ProcessingSTT)
    }
    
    @Test
    fun `processing LLM state is correct`() {
        val state = AssistantState.ProcessingLLM
        assertTrue(state is AssistantState.ProcessingLLM)
    }
    
    @Test
    fun `error state contains message`() {
        val errorMessage = "Test error"
        val state = AssistantState.Error(errorMessage)
        
        assertTrue(state is AssistantState.Error)
        assertEquals(errorMessage, (state as AssistantState.Error).message)
    }
}

