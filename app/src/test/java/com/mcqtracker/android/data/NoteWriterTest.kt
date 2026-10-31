package com.mcqtracker.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class NoteWriterTest {

    private val baseNote = """
        ---
        question_id: WRITE-1
        answer: C
        srs: custom-state
        study_status: known
        ---

        ## Question

        Keep this body exactly as it is.

        ## Answer Choices

        - A. One
        - B. Two
        - C. Three
    """.trimIndent()

    private fun attempt(
        attempts: Int = 1,
        correctCount: Int = 1,
        streak: Int = 1,
        attemptId: String = "attempt-1",
        history: List<HistoryRow>,
        nextReview: LocalDate = LocalDate.of(2026, 11, 1),
    ) = NoteWriter.Attempt(
        result = "correct",
        dateAttempted = "2026-10-31T09:15",
        attempts = attempts,
        correctCount = correctCount,
        streak = streak,
        timeSpentTotal = 42,
        lastTimeSpent = 42,
        lastAnswer = "C",
        lastConfidence = 2,
        lastErrorType = "",
        nextReview = nextReview.toString(),
        attemptId = attemptId,
        history = history,
    )

    private fun row(attemptId: String = "attempt-1") = HistoryRow(
        timestamp = "2026-10-31T09:15:30",
        result = "correct",
        seconds = 42,
        chosen = "C",
        confidence = 2,
        errorType = "",
        attemptId = attemptId,
    )

    @Test
    fun `body is preserved and scalar fields are written`() {
        val output = NoteWriter.applyAttempt(baseNote, attempt(history = listOf(row())))
        assertTrue(output.contains("Keep this body exactly as it is."))
        assertTrue(output.contains("attempts: 1"))
        assertTrue(output.contains("correct_count: 1"))
        assertTrue(output.contains("streak: 1"))
        assertTrue(output.contains("next_review: 2026-11-01"))
        assertTrue(output.contains("last_answer: C"))
        assertTrue(output.contains("last_confidence: 2"))
        assertTrue(output.contains("last_attempt_id: attempt-1"))
        assertTrue(output.contains("first_attempted: 2026-10-31T09:15"))
        assertTrue(output.contains("date attempted: 2026-10-31T09:15"))
    }

    @Test
    fun `unknown properties such as srs and study_status are untouched`() {
        val output = NoteWriter.applyAttempt(baseNote, attempt(history = listOf(row())))
        assertTrue(output.contains("srs: custom-state"))
        assertTrue(output.contains("study_status: known"))
    }

    @Test
    fun `first_attempted is preserved when present`() {
        val withFirst = baseNote.replace("answer: C\n", "answer: C\nfirst_attempted: 2020-01-01T08:00\n")
        val output = NoteWriter.applyAttempt(withFirst, attempt(history = listOf(row())))
        assertTrue(output.contains("first_attempted: 2020-01-01T08:00"))
        assertFalse(output.contains("first_attempted: 2026-10-31T09:15"))
    }

    @Test
    fun `history is appended once and duplicate attempt ids are ignored`() {
        val once = NoteWriter.applyAttempt(baseNote, attempt(history = listOf(row())))
        val twice = NoteWriter.applyAttempt(once, attempt(history = listOf(row())))
        val written = NoteParser.parseFrontmatter(twice)
        assertEquals(1, written.lists["history"]?.size ?: 0)
    }

    @Test
    fun `existing quoted history rows survive a write`() {
        val withHistory = baseNote.replace(
            "answer: C\n",
            "answer: C\nhistory:\n  - \"2026-10-30T10:00:00|incorrect|20|A|2|careless|old-1\"\n",
        )
        val output = NoteWriter.applyAttempt(withHistory, attempt(history = listOf(row())))
        assertTrue(output.contains("old-1"))
        assertTrue(output.contains("attempt-1"))
    }

    @Test
    fun `error tags update the matching row and last_error_type`() {
        val written = NoteWriter.applyAttempt(baseNote, attempt(history = listOf(row())))
        val tagged = NoteWriter.applyErrorTag(written, "attempt-1", "content gap")
        assertTrue(tagged.contains("content gap"))
        assertTrue(tagged.contains("last_error_type: content gap"))
    }

    @Test
    fun `crlf line endings and trailing newline are preserved`() {
        val crlf = baseNote.replace("\n", "\r\n") + "\r\n"
        val output = NoteWriter.applyAttempt(crlf, attempt(history = listOf(row())))
        assertTrue(output.contains("\r\n"))
        assertEquals(0, output.replace("\r\n", "").count { it == '\n' })
        assertTrue(output.endsWith("\r\n"))
    }

    @Test
    fun `a note without frontmatter gains a valid one and keeps its body`() {
        val raw = "Just some text without frontmatter.\n"
        val output = NoteWriter.applyAttempt(raw, attempt(history = listOf(row())))
        assertTrue(output.startsWith("---"))
        assertTrue(output.contains("attempts: 1"))
        assertTrue(output.contains("Just some text without frontmatter."))
        assertTrue(output.contains("history:"))
    }

    @Test
    fun `next review and interval formatting match the contract`() {
        val outcome = Scheduler.apply(
            previous = Progress(),
            correct = true,
            confidence = 2,
            seconds = 30,
            chosen = "C",
            today = LocalDate.of(2026, 10, 31),
            now = LocalDateTime.of(2026, 10, 31, 9, 15, 0),
            attemptId = "abc",
        )
        val payload = NoteWriter.attemptFrom(outcome, LocalDateTime.of(2026, 10, 31, 9, 15, 0))
        assertEquals("2026-11-01", payload.nextReview)
        assertEquals("2026-10-31T09:15", payload.dateAttempted)
        val output = NoteWriter.applyAttempt(baseNote, payload)
        assertTrue(output.contains("date attempted: 2026-10-31T09:15"))
        assertTrue(output.contains("next_review: 2026-11-01"))
    }
}
