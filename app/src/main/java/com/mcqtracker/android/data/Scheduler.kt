package com.mcqtracker.android.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The plugin's default ladder scheduler: 1, 3, 7, 21, 60 days.
 *
 * The formula is fixed by the handoff contract and reproduced exactly:
 * a correct answer with confidence 1 ("guessing") returns in 2 days without
 * advancing the streak; a miss resets the streak and returns tomorrow.
 */
object Scheduler {

    val INTERVALS: List<Int> = listOf(1, 3, 7, 21, 60)

    val HISTORY_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    val NOTE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    const val ERROR_TAG_AFTER_ATTEMPT = ""

    val ERROR_TAGS: List<String> = listOf(
        "careless",
        "content gap",
        "misread",
        "ran out of time",
        "guessed",
    )

    data class Outcome(
        val progress: Progress,
        val intervalDays: Int,
        val nextReview: LocalDate,
        val attemptId: String,
        val row: HistoryRow,
        val timestamp: String,
    )

    /**
     * Applies one attempt.
     *
     * @param previous progress before this attempt
     * @param correct whether the answer was graded correct
     * @param confidence 1 = guessing, 2 = unsure, 3 = confident (clamped)
     * @param seconds time spent on the question
     * @param chosen chosen letter(s), empty for self-graded
     */
    fun apply(
        previous: Progress,
        correct: Boolean,
        confidence: Int,
        seconds: Int,
        chosen: String,
        today: LocalDate,
        now: LocalDateTime,
        attemptId: String = UUID.randomUUID().toString(),
    ): Outcome {
        val conf = confidence.coerceIn(1, 3)
        val safeSeconds = seconds.coerceAtLeast(0)

        val streak: Int
        val intervalDays: Int
        if (correct) {
            streak = if (conf == 1) previous.streak else previous.streak + 1
            intervalDays = if (conf == 1) {
                2
            } else {
                INTERVALS[(maxOf(streak, 1) - 1).coerceAtMost(INTERVALS.size - 1)]
            }
        } else {
            streak = 0
            intervalDays = 1
        }

        val nextReview = today.plusDays(intervalDays.toLong())
        val timestamp = now.format(HISTORY_TIMESTAMP)

        val row = HistoryRow(
            timestamp = timestamp,
            result = if (correct) "correct" else "incorrect",
            seconds = safeSeconds,
            chosen = sanitizeChosen(chosen),
            confidence = conf,
            errorType = ERROR_TAG_AFTER_ATTEMPT,
            attemptId = attemptId,
        )

        val progress = previous.copy(
            attempts = previous.attempts + 1,
            correctCount = previous.correctCount + if (correct) 1 else 0,
            streak = streak,
            nextReview = nextReview,
            lastResult = if (correct) "correct" else "incorrect",
            lastConfidence = conf,
            lastErrorType = ERROR_TAG_AFTER_ATTEMPT,
            timeSpentTotal = previous.timeSpentTotal + safeSeconds,
            lastTimeSpent = safeSeconds,
            lastAnswer = sanitizeChosen(chosen),
            history = appendHistory(previous.history, row),
        )

        return Outcome(
            progress = progress,
            intervalDays = intervalDays,
            nextReview = nextReview,
            attemptId = attemptId,
            row = row,
            timestamp = timestamp,
        )
    }

    /** Appends a row unless an identical attempt id is already present. */
    fun appendHistory(history: List<HistoryRow>, row: HistoryRow): List<HistoryRow> {
        if (history.any { it.attemptId == row.attemptId }) return history
        return history + row
    }

    /** Applies an error tag to the matching attempt only, leaving other rows untouched. */
    fun tagError(progress: Progress, attemptId: String, errorType: String): Progress {
        val updated = progress.history.map { row ->
            if (row.attemptId == attemptId) row.copy(errorType = errorType) else row
        }
        return progress.copy(lastErrorType = errorType, history = updated)
    }

    private fun sanitizeChosen(chosen: String): String =
        chosen.replace('|', '/').replace("\r", " ").replace("\n", " ").trim()
}
