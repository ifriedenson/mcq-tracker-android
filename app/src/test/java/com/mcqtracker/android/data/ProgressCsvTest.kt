package com.mcqtracker.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressCsvTest {

    @Test
    fun `header comes first and every question gets a row`() {
        val csv = ProgressCsv.build(listOf(question("B-2"), question("A-1")))
        val lines = csv.trim().split("\n")

        assertEquals(3, lines.size)
        assertTrue(lines.first().startsWith("question_id,source,topic,mode,attempts"))
        // Sorted by source key then id, independent of input order.
        assertTrue(lines[1].startsWith("A-1,"))
        assertTrue(lines[2].startsWith("B-2,"))
    }

    @Test
    fun `commas quotes and newlines are escaped`() {
        val tricky = question("Q-1", topic = "Algebra, \"hard\"\nsecond line")
        val csv = ProgressCsv.build(listOf(tricky))

        assertTrue(csv.contains("\"Algebra, \"\"hard\"\"\nsecond line\""))
    }

    @Test
    fun `empty vault writes only the header`() {
        val csv = ProgressCsv.build(emptyList())

        assertEquals(ProgressCsv.HEADER.joinToString(",") + "\n", csv)
    }

    @Test
    fun `progress values are rendered`() {
        val progress = Progress(
            attempts = 3,
            correctCount = 2,
            streak = 1,
            nextReview = LocalDate.of(2026, 11, 2),
            lastResult = "correct",
            lastConfidence = 3,
            lastErrorType = "",
            timeSpentTotal = 90,
            lastTimeSpent = 12,
            lastAnswer = "C",
            history = listOf(
                HistoryRow("2026-11-01T10:00:00", "correct", 12, "C", 3, "", "a1"),
            ),
            flagged = true,
        )

        val row = ProgressCsv.build(listOf(question("Q-9", progress = progress)))
            .trim()
            .split("\n")[1]
        val cells = row.split(",")

        assertEquals("3", cells[4])
        assertEquals("2", cells[5])
        assertEquals("0.667", cells[6])
        assertEquals("2026-11-02", cells[8])
        assertEquals("true", cells[12])
        assertEquals("1", cells[16])
    }

    private fun question(
        id: String,
        topic: String = "Algebra",
        progress: Progress = Progress(),
    ) = Question(
        sourceKey = "vault/Questions/CSV",
        questionId = id,
        skill = topic,
        mode = QuestionMode.MCQ,
        answerKey = "A",
        prompt = "Prompt",
        progress = progress,
    )
}
