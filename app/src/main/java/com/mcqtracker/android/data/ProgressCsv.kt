package com.mcqtracker.android.data

import java.util.Locale

/**
 * Builds a portable CSV snapshot of question progress.
 *
 * Kept pure so it can be unit tested without Android. This is a one-way
 * export: it reads nothing, writes no notes and never touches the network.
 */
object ProgressCsv {

    val HEADER = listOf(
        "question_id",
        "source",
        "topic",
        "mode",
        "attempts",
        "correct",
        "accuracy",
        "streak",
        "next_review",
        "last_result",
        "last_confidence",
        "last_error_type",
        "flagged",
        "time_spent_total",
        "last_time_spent",
        "last_answer",
        "history_rows",
    )

    fun build(questions: List<Question>): String {
        val rows = questions
            .sortedWith(compareBy({ it.sourceKey }, { it.questionId }))
            .map { question ->
                val progress = question.progress
                listOf(
                    question.questionId,
                    question.sourceKey,
                    question.topic,
                    question.mode.name,
                    progress.attempts.toString(),
                    progress.correctCount.toString(),
                    String.format(Locale.US, "%.3f", progress.accuracy),
                    progress.streak.toString(),
                    progress.nextReview?.toString().orEmpty(),
                    progress.lastResult.orEmpty(),
                    progress.lastConfidence?.toString().orEmpty(),
                    progress.lastErrorType,
                    progress.flagged.toString(),
                    progress.timeSpentTotal.toString(),
                    progress.lastTimeSpent.toString(),
                    progress.lastAnswer,
                    progress.history.size.toString(),
                )
            }

        return buildString {
            appendLine(HEADER.joinToString(","))
            rows.forEach { row ->
                appendLine(row.joinToString(",") { escape(it) })
            }
        }
    }

    /** RFC 4180-style quoting: a field containing a comma, quote or newline is wrapped. */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
