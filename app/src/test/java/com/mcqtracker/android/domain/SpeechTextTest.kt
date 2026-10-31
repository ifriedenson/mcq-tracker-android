package com.mcqtracker.android.domain

import com.mcqtracker.android.data.Choice
import com.mcqtracker.android.data.Question
import com.mcqtracker.android.data.QuestionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {

    private val question = Question(
        sourceKey = "src",
        questionId = "Q1",
        mode = QuestionMode.MCQ,
        answerKey = "C",
        stimulus = "Stimulus.",
        prompt = "Prompt.",
        choices = listOf(Choice("A", "One"), Choice("B", "Two")),
    )

    @Test
    fun `question reads stimulus prompt and choices in order`() {
        assertEquals(
            "Stimulus. Prompt. A. One, B. Two",
            SpeechText.question(question),
        )
    }

    @Test
    fun `blank stimulus and choices are skipped`() {
        val bare = question.copy(stimulus = "", choices = emptyList())
        assertEquals("Prompt.", SpeechText.question(bare))
    }

    @Test
    fun `feedback reads the answer then the explanation`() {
        assertEquals(
            "The correct answer is C. Because reasons.",
            SpeechText.result(question, "C", "Because reasons."),
        )
    }

    @Test
    fun `missing explanation still reads the answer`() {
        val text = SpeechText.result(question, "C", "")
        assertTrue(text.contains("The correct answer is C."))
        assertFalse(text.contains("  "))
    }

    @Test
    fun `no double periods or repeated whitespace`() {
        val text = SpeechText.question(question.copy(prompt = "Prompt..", stimulus = "Stim. "))
        assertFalse(text.contains(".."))
        assertFalse(text.contains("  "))
    }
}
