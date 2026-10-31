package com.mcqtracker.android.domain

import com.mcqtracker.android.data.Choice
import com.mcqtracker.android.data.Question
import com.mcqtracker.android.data.QuestionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerGraderTest {

    private fun question(
        mode: QuestionMode,
        key: String,
        choices: List<Choice> = emptyList(),
    ) = Question(
        sourceKey = "test",
        questionId = "Q1",
        mode = mode,
        choices = choices,
        answerKey = key,
    )

    private val fourChoices = listOf(
        Choice("A", "Dangerous"),
        Choice("B", "Expensive"),
        Choice("C", "Not decisive"),
        Choice("D", "Carefully planned"),
    )

    @Test
    fun `mcq compares a single letter case insensitively`() {
        val q = question(QuestionMode.MCQ, "C", fourChoices)
        assertTrue(AnswerGrader.grade(q, listOf("C")).correct)
        assertTrue(AnswerGrader.grade(q, listOf("c")).correct)
        assertFalse(AnswerGrader.grade(q, listOf("A")).correct)
        assertFalse(AnswerGrader.grade(q, listOf("A", "C")).correct)
    }

    @Test
    fun `select all requires an exact set`() {
        val q = question(QuestionMode.SELECT_ALL, "A, C", fourChoices)
        assertTrue(AnswerGrader.grade(q, listOf("A", "C")).correct)
        assertTrue(AnswerGrader.grade(q, listOf("C", "A")).correct)
        assertFalse(AnswerGrader.grade(q, listOf("A")).correct)
        assertFalse(AnswerGrader.grade(q, listOf("A", "C", "D")).correct)
    }

    @Test
    fun `true false maps letters through custom labels`() {
        val q = question(
            QuestionMode.TRUE_FALSE,
            "True",
            listOf(Choice("A", "Yes"), Choice("B", "No")),
        )
        assertTrue(AnswerGrader.grade(q, listOf("A")).correct)
        assertFalse(AnswerGrader.grade(q, listOf("B")).correct)
    }

    @Test
    fun `self graded uses the button the user pressed`() {
        val q = question(QuestionMode.SELF_GRADED, "0.5")
        assertTrue(AnswerGrader.grade(q, emptyList(), selfGrade = true).correct)
        assertFalse(AnswerGrader.grade(q, emptyList(), selfGrade = false).correct)
        assertEquals("", AnswerGrader.grade(q, emptyList(), selfGrade = true).chosen)
    }

    @Test
    fun `correct answer label includes the choice text`() {
        val q = question(QuestionMode.MCQ, "C", fourChoices)
        assertEquals("C. Not decisive", AnswerGrader.correctAnswerLabel(q))
        assertEquals(listOf("C"), AnswerGrader.correctChoiceLetters(q))
    }

    @Test
    fun `true false label is the canonical word`() {
        val q = question(QuestionMode.TRUE_FALSE, "False", listOf(Choice("A", "True"), Choice("B", "False")))
        assertEquals("False", AnswerGrader.correctAnswerLabel(q))
        assertEquals(listOf("B"), AnswerGrader.correctChoiceLetters(q))
    }

    @Test
    fun `select all reports every correct letter`() {
        val q = question(QuestionMode.SELECT_ALL, "A, C", fourChoices)
        assertEquals(listOf("A", "C"), AnswerGrader.correctChoiceLetters(q))
        assertEquals("A. Dangerous, C. Not decisive", AnswerGrader.correctAnswerLabel(q))
    }
}
