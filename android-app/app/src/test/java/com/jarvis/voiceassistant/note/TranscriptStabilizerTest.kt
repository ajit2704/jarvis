package com.jarvis.voiceassistant.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranscriptStabilizerTest {

    private lateinit var stabilizer: TranscriptStabilizer

    @Before
    fun setup() {
        stabilizer = TranscriptStabilizer(
            TranscriptStabilizerConfig(keepUnstableWords = 2)
        )
    }

    @Test
    fun `monotonic commit grows stable`() {
        stabilizer.onSegment("one two three four five")
        val s1 = stabilizer.onSegment("six seven eight")
        assertTrue(s1.committedStable.isNotEmpty())
        assertTrue(s1.displayLine.contains("one"))
        assertEquals(
            "one two three four five six seven eight",
            stabilizer.runningSnapshot()
        )
    }

    @Test
    fun reset_clears() {
        stabilizer.onSegment("hello world test")
        stabilizer.reset()
        val s = stabilizer.onSegment("")
        assertEquals("", s.committedStable)
        assertEquals("", s.displayLine)
        assertEquals("", stabilizer.runningSnapshot())
    }

    @Test
    fun blank_segment_unchanged() {
        stabilizer.onSegment("one two three four five six seven")
        val before = stabilizer.onSegment("eight nine ten")
        val after = stabilizer.onSegment("   ")
        assertEquals(before.committedStable, after.committedStable)
        assertEquals(before.pending, after.pending)
    }

    /** P1: running transcript is cumulative (chunks appended), not isolated per-chunk only. */
    @Test
    fun running_snapshot_is_cumulative() {
        stabilizer.onSegment("alpha beta")
        stabilizer.onSegment("gamma delta")
        assertEquals("alpha beta gamma delta", stabilizer.runningSnapshot())
    }

    /**
     * Cumulative snapshots are nested prefixes; LCP across them would equal only the first chunk.
     * Stable candidate must follow full running text (minus unstable tail), not that broken LCP.
     */
    @Test
    fun stable_covers_all_but_unstable_tail() {
        stabilizer.onSegment("first sentence here")
        stabilizer.onSegment("second sentence follows now")
        val snap = stabilizer.onSegment("third sentence end")
        val running = stabilizer.runningSnapshot()
        assertEquals(
            "first sentence here second sentence follows now third sentence end",
            running
        )
        // 8 + 8 + 4 = 20 words; trim last 2 -> 18 words stable if monotonic accepts full candidate
        assertTrue(snap.committedStable.isNotEmpty())
        assertTrue(
            "stable should be almost full note, not first chunk only",
            snap.committedStable.length > "first sentence here".length
        )
        assertTrue(running.startsWith(snap.committedStable))
    }
}
