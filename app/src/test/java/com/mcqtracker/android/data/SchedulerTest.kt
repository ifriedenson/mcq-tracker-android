package com.mcqtracker.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SchedulerTest {

    private val today = LocalDate.of(2026, 10, 31)
    private val now = LocalDateTime.of(2026, 10, 31, 9, 15, 30)

    private fun apply(
        previous: Progress,
        correct: Boolean,
        confidence: Int = 2,
        seconds: Int = 42,
        chosen: String = "C",
    ) = Scheduler.apply(
        previous = previous,
        correct = correct,
        confidence = confidence,
        seconds = seconds,
        chosen = chosen,
        today = today,
        now = now,
        attemptId = "attempt-fixed",
    )

    @Test
    fun `first second third fourth and fifth correct answers follow the ladder`() {
        assertEquals(1, apply(Progress(), true).intervalDays)
        assertEquals(3, apply(Progress(streak = 1), true).intervalDays)
        assertEquals(7, apply(Progress(streak = 2), true).intervalDays)
        assertEquals(21, apply(Progress(streak = 3), true).intervalDays)
        assertEquals(60, apply(Progress(streak = 4), true).intervalDays)
        assertEquals(60, apply(Progress(streak = 9), true).intervalDays)
    }

    @Test
    fun `a correct guess returns in two days and does not advance the streak`() {
        val outcome = apply(Progress(streak = 3), correct = true, confidence = 1)
        assertEquals(2, outcome.intervalDays)
        assertEquals(3, outcome.progress.streak)
    }

    @Test
    fun `a miss returns tomorrow and resets the streak`() {
        val outcome = apply(Progress(streak = 4), correct = false)
        assertEquals(1, outcome.intervalDays)
        assertEquals(0, outcome.progress.streak)
        assertEquals(today.plusDays(1), outcome.nextReview)
    }

    @Test
    fun `confidence is clamped to one through three`() {
        assertEquals(3, apply(Progress(), true, confidence = 9).progress.lastConfidence)
        assertEquals(1, apply(Progress(), true, confidence = 0).progress.lastConfidence)
        assertEquals(2, apply(Progress(), true, confidence = 2).progress.lastConfidence)
    }

    @Test
    fun `counters accumulate and the last result is recorded`() {
        val outcome = apply(Progress(attempts = 4, correctCount = 3, timeSpentTotal = 100), true, seconds = 42)
        assertEquals(5, outcome.progress.attempts)
        assertEquals(4, outcome.progress.correctCount)
        assertEquals(142, outcome.progress.timeSpentTotal)
        assertEquals(42, outcome.progress.lastTimeSpent)
        assertEquals("correct", outcome.progress.lastResult)
        // previous streak was 0, so this correct answer makes streak 1 -> first rung of the ladder
        assertEquals(1, outcome.progress.streak)
        assertEquals(today.plusDays(1), outcome.nextReview)
    }

    @Test
    fun `history row uses the exact plugin format`() {
        val outcome = apply(Progress(), true)
        val row = outcome.row.serialize()
        assertTrue(
            "unexpected row format: $row",
            Regex("""^2026-10-31T09:15:30\|correct\|42\|C\|2\|\|attempt-fixed$""").matches(row),
        )
        assertEquals("2026-10-31T09:15:30", outcome.timestamp)
    }

    @Test
    fun `chosen values are sanitized for the pipe delimited format`() {
        val outcome = apply(Progress(), true, chosen = "A|C\nD")
        assertTrue(outcome.row.chosen.none { it == '|' || it == '\n' })
    }

    @Test
    fun `history appends once per attempt id`() {
        val first = apply(Progress(), true)
        val again = Scheduler.appendHistory(first.progress.history, first.row)
        assertEquals(1, again.size)
    }

    @Test
    fun `error tags update only the matching attempt`() {
        val first = apply(Progress(), correct = false)
        val second = Scheduler.apply(
            previous = first.progress,
            correct = true,
            confidence = 2,
            seconds = 10,
            chosen = "B",
            today = today,
            now = now.plusMinutes(5),
            attemptId = "attempt-2",
        )
        val tagged = Scheduler.tagError(second.progress, "attempt-2", "misread")
        assertEquals("", tagged.history[0].errorType)
        assertEquals("misread", tagged.history[1].errorType)
        assertEquals("misread", tagged.lastErrorType)
    }

    @Test
    fun `history row parses back into an equal record`() {
        val row = HistoryRow(
            timestamp = "2026-10-31T09:15:30",
            result = "incorrect",
            seconds = 30,
            chosen = "A, C",
            confidence = 1,
            errorType = "guessed",
            attemptId = "abc",
            variant = "v2",
        )
        assertEquals(row, HistoryRow.parse(row.serialize()))
    }
}
