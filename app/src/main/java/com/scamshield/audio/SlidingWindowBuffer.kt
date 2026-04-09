package com.scamshield.audio

import java.util.LinkedList

/**
 * Maintains an ephemeral sliding window of transcript text.
 * Used to provide context to the ML model without storing full call logs.
 * All data is in-memory only.
 */
class SlidingWindowBuffer(
    private val maxWords: Int = 100,
    private val maxSegments: Int = 20
) {

    private val segments = LinkedList<String>()
    private var wordCount = 0

    fun append(text: String) {
        if (text.isBlank()) return

        val words = text.trim().split("\\s+".toRegex())
        segments.addLast(text.trim())
        wordCount += words.size

        // Remove oldest segments if over limit
        while (wordCount > maxWords && segments.isNotEmpty()) {
            val oldest = segments.removeFirst()
            wordCount -= oldest.split("\\s+".toRegex()).size
        }

        // Keep segment count bounded
        while (segments.size > maxSegments) {
            segments.removeFirst()
        }
    }

    fun getWindow(): String = segments.joinToString(" ")

    fun getRecentWords(count: Int): String {
        val allWords = getWindow().split("\\s+".toRegex())
        return allWords.takeLast(count).joinToString(" ")
    }

    fun clear() {
        segments.clear()
        wordCount = 0
    }

    fun size(): Int = wordCount
}
