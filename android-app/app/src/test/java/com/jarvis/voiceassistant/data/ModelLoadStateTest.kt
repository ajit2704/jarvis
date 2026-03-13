package com.jarvis.voiceassistant.data

import org.junit.Assert.*
import org.junit.Test

class ModelLoadStateTest {
    
    @Test
    fun `not loaded state is correct`() {
        val state = ModelLoadState.NotLoaded
        assertTrue(state is ModelLoadState.NotLoaded)
    }
    
    @Test
    fun `downloading state contains progress`() {
        val progress = 0.5f
        val state = ModelLoadState.Downloading(progress)
        
        assertTrue(state is ModelLoadState.Downloading)
        assertEquals(progress, (state as ModelLoadState.Downloading).progress, 0.01f)
    }
    
    @Test
    fun `downloading progress is between 0 and 1`() {
        val state1 = ModelLoadState.Downloading(0.0f)
        val state2 = ModelLoadState.Downloading(1.0f)
        val state3 = ModelLoadState.Downloading(0.75f)
        
        assertEquals(0.0f, (state1 as ModelLoadState.Downloading).progress, 0.01f)
        assertEquals(1.0f, (state2 as ModelLoadState.Downloading).progress, 0.01f)
        assertEquals(0.75f, (state3 as ModelLoadState.Downloading).progress, 0.01f)
    }
    
    @Test
    fun `loading state is correct`() {
        val state = ModelLoadState.Loading
        assertTrue(state is ModelLoadState.Loading)
    }
    
    @Test
    fun `ready state contains model path`() {
        val path = "/path/to/model.gguf"
        val state = ModelLoadState.Ready(path)
        
        assertTrue(state is ModelLoadState.Ready)
        assertEquals(path, (state as ModelLoadState.Ready).modelPath)
    }
    
    @Test
    fun `error state contains message`() {
        val errorMessage = "Download failed"
        val state = ModelLoadState.Error(errorMessage)
        
        assertTrue(state is ModelLoadState.Error)
        assertEquals(errorMessage, (state as ModelLoadState.Error).message)
    }
}

