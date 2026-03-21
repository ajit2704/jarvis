package com.jarvis.voiceassistant.note

import org.junit.Assert.assertEquals
import org.junit.Test

class TextStabilizationTest {

    @Test
    fun longestCommonPrefix_empty() {
        assertEquals("", TextStabilization.longestCommonPrefix(emptyList()))
    }

    @Test
    fun longestCommonPrefix_single() {
        assertEquals("hello world", TextStabilization.longestCommonPrefix(listOf("hello world")))
    }

    @Test
    fun longestCommonPrefix_two_charLevel() {
        assertEquals("hello ", TextStabilization.longestCommonPrefix(listOf("hello world", "hello there")))
    }

    @Test
    fun longestCommonPrefixWords_two() {
        assertEquals("hello", TextStabilization.longestCommonPrefixWords(listOf("hello world", "hello there")))
    }

    /** P1: word LCP stops at "and" — does not commit partial word "brea". */
    @Test
    fun longestCommonPrefixWords_breaBread() {
        assertEquals(
            "buy milk and",
            TextStabilization.longestCommonPrefixWords(
                listOf("buy milk and brea", "buy milk and bread")
            )
        )
    }

    @Test
    fun trimLastWords_dropsLastTwo() {
        assertEquals("buy milk", TextStabilization.trimLastWords("buy milk and bread", 2))
    }

    @Test
    fun trimLastWords_shortReturnsEmpty() {
        assertEquals("", TextStabilization.trimLastWords("buy milk", 2))
    }

    @Test
    fun normalizeWhitespace() {
        assertEquals("a b c", TextStabilization.normalizeWhitespace("  a   b  c  "))
    }
}
