package com.jarvis.voiceassistant.data

import org.junit.Assert.*
import org.junit.Test

class MessageTest {
    
    @Test
    fun `message creation with user flag`() {
        val message = Message(
            text = "Hello",
            isUser = true
        )
        
        assertEquals("Hello", message.text)
        assertTrue(message.isUser)
        assertTrue(message.timestamp > 0)
    }
    
    @Test
    fun `message creation with assistant flag`() {
        val message = Message(
            text = "Hi there!",
            isUser = false
        )
        
        assertEquals("Hi there!", message.text)
        assertFalse(message.isUser)
    }
    
    @Test
    fun `message timestamp is set automatically`() {
        val before = System.currentTimeMillis()
        val message = Message(text = "Test", isUser = true)
        val after = System.currentTimeMillis()
        
        assertTrue(message.timestamp >= before)
        assertTrue(message.timestamp <= after)
    }
    
    @Test
    fun `message equality`() {
        val message1 = Message(text = "Hello", isUser = true, timestamp = 1000L)
        val message2 = Message(text = "Hello", isUser = true, timestamp = 1000L)
        val message3 = Message(text = "Hello", isUser = false, timestamp = 1000L)
        
        assertEquals(message1, message2)
        assertNotEquals(message1, message3)
    }
}

