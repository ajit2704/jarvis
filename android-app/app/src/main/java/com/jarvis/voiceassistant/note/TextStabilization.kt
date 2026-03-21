package com.jarvis.voiceassistant.note

/**
 * Pure helpers for transcript stabilization (testable without Android).
 */
object TextStabilization {

    /** Collapse runs of whitespace; trim ends. */
    fun normalizeWhitespace(text: String): String =
        text.trim().replace(Regex("\\s+"), " ")

    /** Tokenize on whitespace for word-level algorithms. */
    fun tokenizeWords(text: String): List<String> =
        normalizeWhitespace(text).split(Regex("\\s+")).filter { it.isNotEmpty() }

    /**
     * Longest common **word** prefix across all strings (P1: avoids committing partial words like "brea").
     */
    fun longestCommonPrefixWords(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        val tokenLists = strings.map { tokenizeWords(it) }
        var prefix = tokenLists[0]
        for (i in 1 until tokenLists.size) {
            prefix = commonPrefixWords(prefix, tokenLists[i])
            if (prefix.isEmpty()) break
        }
        return prefix.joinToString(" ")
    }

    /** Longest common prefix across all strings (character-level; legacy). */
    fun longestCommonPrefix(strings: List<String>): String {
        if (strings.isEmpty()) return ""
        var prefix = strings[0]
        for (i in 1 until strings.size) {
            prefix = longestCommonPrefix(prefix, strings[i])
            if (prefix.isEmpty()) break
        }
        return prefix
    }

    fun longestCommonPrefix(a: String, b: String): String {
        val minLen = minOf(a.length, b.length)
        var i = 0
        while (i < minLen && a[i] == b[i]) i++
        return a.substring(0, i)
    }

    private fun commonPrefixWords(a: List<String>, b: List<String>): List<String> {
        val n = minOf(a.size, b.size)
        val out = ArrayList<String>(n)
        for (i in 0 until n) {
            if (a[i] == b[i]) out.add(a[i]) else break
        }
        return out
    }

    /**
     * Drops the last [keepUnstable] whitespace-separated tokens.
     * If there are fewer tokens, returns "" (nothing safe to commit yet).
     */
    fun trimLastWords(text: String, keepUnstable: Int): String {
        if (keepUnstable <= 0) return text.trim()
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size <= keepUnstable) return ""
        return words.dropLast(keepUnstable).joinToString(" ")
    }
}
