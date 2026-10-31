package com.mcqtracker.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mcqtracker.android.QuestionFilter
import com.mcqtracker.android.data.Choice
import com.mcqtracker.android.data.HistoryRow
import com.mcqtracker.android.data.Progress
import com.mcqtracker.android.data.Question
import com.mcqtracker.android.data.QuestionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Composes real screens on the JVM through Robolectric. This is the closest
 * substitute available here for an emulator: it proves the UI builds a
 * composition and that taps reach the expected callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposeRenderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `question row renders its title and routes taps`() {
        var rowClicks = 0
        var flagClicks = 0
        val question = question("Q-1", prompt = "Which bucket leaks first?")

        compose.setContent {
            MCQTrackerTheme {
                QuestionRow(
                    question = question,
                    onClick = { rowClicks++ },
                    onFlag = { flagClicks++ },
                )
            }
        }

        compose.onNodeWithText("Which bucket leaks first?").assertIsDisplayed()
        compose.onNodeWithText("Q-1 · Multiple choice").assertIsDisplayed()

        compose.onNodeWithText("☆ Flag").performClick()
        assertEquals(1, flagClicks)

        compose.onNodeWithText("Which bucket leaks first?").performClick()
        assertEquals(1, rowClicks)
    }

    @Test
    fun `status pill distinguishes new due and leech questions`() {
        val today = LocalDate.now()
        compose.setContent {
            MCQTrackerTheme {
                Column {
                    StatusPill(question("NEW", progress = Progress()))
                    StatusPill(
                        question(
                            "DUE",
                            progress = Progress(
                                attempts = 2,
                                correctCount = 2,
                                nextReview = today.minusDays(1),
                            ),
                        ),
                    )
                    StatusPill(question("LEECH", progress = leechProgress()))
                }
            }
        }

        compose.onNodeWithText("New").assertIsDisplayed()
        compose.onNodeWithText("Due").assertIsDisplayed()
        compose.onNodeWithText("Leech").assertIsDisplayed()
    }

    @Test
    fun `filter row reports the tapped filter`() {
        var chosen: QuestionFilter? = null

        compose.setContent {
            MCQTrackerTheme {
                FilterRow(current = QuestionFilter.ALL, onSelect = { chosen = it })
            }
        }

        compose.onNodeWithText("Due").performClick()
        assertEquals(QuestionFilter.DUE, chosen)
        assertTrue(QuestionFilter.entries.size >= 5)
    }

    private fun leechProgress(): Progress {
        val rows = (0 until 4).map { index ->
            HistoryRow(
                timestamp = "2026-10-2${index}T10:00:00",
                result = "incorrect",
                seconds = 10,
                chosen = "B",
                confidence = 1,
                errorType = "",
                attemptId = "attempt-$index",
            )
        }
        return Progress(
            attempts = 4,
            correctCount = 0,
            lastResult = "incorrect",
            history = rows,
        )
    }

    private fun question(
        id: String,
        prompt: String = "Sample prompt",
        progress: Progress = Progress(),
    ) = Question(
        sourceKey = "vault/Questions/TEST",
        questionId = id,
        skill = "Leaky buckets",
        mode = QuestionMode.MCQ,
        choices = listOf(Choice("A", "Alpha"), Choice("B", "Beta")),
        answerKey = "A",
        prompt = prompt,
        progress = progress,
    )
}
