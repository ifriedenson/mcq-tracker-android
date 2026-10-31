package com.mcqtracker.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressStoreTest {

    @Test
    fun `progress survives an encode and decode round trip`() {
        val progress = Progress(
            attempts = 3,
            correctCount = 2,
            streak = 1,
            nextReview = LocalDate.of(2026, 11, 3),
            lastResult = "correct",
            lastConfidence = 2,
            lastErrorType = "careless",
            timeSpentTotal = 120,
            lastTimeSpent = 40,
            lastAnswer = "C",
            flagged = true,
            history = listOf(
                HistoryRow("2026-10-30T09:00:00", "incorrect", 50, "A", 2, "careless", "id-1"),
                HistoryRow("2026-10-31T09:00:00", "correct", 40, "C", 2, "", "id-2", "variant-b"),
            ),
        )
        val encoded = ProgressStore.encode(mapOf("sample::Q1" to progress))
        val decoded = ProgressStore.decode(encoded)
        assertEquals(progress, decoded["sample::Q1"])
    }

    @Test
    fun `decoding blank or corrupt data yields an empty map`() {
        assertTrue(ProgressStore.decode(null).isEmpty())
        assertTrue(ProgressStore.decode("").isEmpty())
        assertTrue(ProgressStore.decode("not json at all").isEmpty())
    }

    @Test
    fun `merge prefers the copy with more attempts`() {
        val note = Progress(attempts = 1, correctCount = 1)
        val local = Progress(attempts = 2, correctCount = 1)
        assertEquals(local, ProgressMerge.merge(note, local))
        assertEquals(note, ProgressMerge.merge(note, Progress(attempts = 0)))
    }

    @Test
    fun `merge falls back to history size then local error tag`() {
        val shortHistory = Progress(attempts = 1, history = listOf(HistoryRow("2026-10-30T09:00:00", "correct", 10, "A", 2, "", "1")))
        val longHistory = shortHistory.copy(
            history = shortHistory.history + HistoryRow("2026-10-31T09:00:00", "correct", 10, "A", 2, "", "2"),
        )
        assertEquals(longHistory, ProgressMerge.merge(shortHistory, longHistory))

        val emptyTag = Progress(attempts = 1)
        val tagged = emptyTag.copy(lastErrorType = "misread")
        assertEquals(tagged, ProgressMerge.merge(emptyTag, tagged))
    }

    @Test
    fun `merge without a local copy keeps the note`() {
        val note = Progress(attempts = 5)
        assertEquals(note, ProgressMerge.merge(note, null))
    }
}
