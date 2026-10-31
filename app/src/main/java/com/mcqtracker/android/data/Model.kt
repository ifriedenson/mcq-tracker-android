package com.mcqtracker.android.data

import java.time.LocalDate

/** How a question is answered. Mirrors the plugin's mode inference. */
enum class QuestionMode {
    MCQ,
    SELECT_ALL,
    TRUE_FALSE,
    SELF_GRADED,

    /** Typed answer compared against the note's key. */
    FILL_IN,

    /** Left/right pairs, all of which must be matched correctly. */
    MATCHING,
}

data class Choice(
    val letter: String,
    val text: String,
)

/** One left/right pairing for [QuestionMode.MATCHING]. */
data class MatchPair(
    val left: String,
    val right: String,
)

/**
 * One row of the plugin's `history` frontmatter list.
 *
 * Serialized as `yyyy-MM-dd'T'HH:mm:ss|correct|seconds|chosen|confidence|error_type|attempt_id`
 * with an optional eighth `variant` field that must be preserved when present.
 */
data class HistoryRow(
    val timestamp: String,
    val result: String,
    val seconds: Int,
    val chosen: String,
    val confidence: Int,
    val errorType: String,
    val attemptId: String,
    val variant: String? = null,
) {
    fun serialize(): String {
        val base = listOf(
            timestamp,
            result,
            seconds.toString(),
            chosen,
            confidence.toString(),
            errorType,
            attemptId,
        ).joinToString("|")
        return if (variant.isNullOrEmpty()) base else "$base|$variant"
    }

    companion object {
        fun parse(raw: String): HistoryRow? {
            val parts = raw.split("|")
            if (parts.size < 7) return null
            return HistoryRow(
                timestamp = parts[0].trim(),
                result = parts[1].trim(),
                seconds = parts[2].trim().toIntOrNull() ?: 0,
                chosen = parts[3],
                confidence = parts[4].trim().toIntOrNull() ?: 2,
                errorType = parts[5].trim(),
                attemptId = parts[6].trim(),
                variant = if (parts.size > 7) parts[7] else null,
            )
        }
    }
}

/** Review state for a question, either read from the note or held locally. */
data class Progress(
    val attempts: Int = 0,
    val correctCount: Int = 0,
    val streak: Int = 0,
    val nextReview: LocalDate? = null,
    val lastResult: String? = null,
    val lastConfidence: Int? = null,
    val lastErrorType: String = "",
    val timeSpentTotal: Int = 0,
    val lastTimeSpent: Int = 0,
    val lastAnswer: String = "",
    val history: List<HistoryRow> = emptyList(),
    val flagged: Boolean = false,
) {
    val accuracy: Double
        get() = if (attempts <= 0) 0.0 else correctCount.toDouble() / attempts

    val isNew: Boolean get() = attempts == 0
}

/** A parsed MCQ Tracker question note. */
data class Question(
    val sourceKey: String,
    val questionId: String,
    val course: String = "",
    val test: String = "",
    val section: String = "",
    val domain: String = "",
    val skill: String = "",
    val difficulty: String = "",
    val unit: String = "",
    val questionType: String = "",
    val mode: QuestionMode,
    val choices: List<Choice> = emptyList(),
    /** Populated for matching questions; empty otherwise. */
    val pairs: List<MatchPair> = emptyList(),
    /** Canonical key: `C`, `A, C`, `True`, `False`, or the raw key for self-graded. */
    val answerKey: String,
    val stimulus: String = "",
    val prompt: String = "",
    val hint: String = "",
    val explanation: String = "",
    val notes: String = "",
    val stimulusId: String = "",
    val targetSeconds: Int? = null,
    val scheduler: String = "",
    val studyStatus: String = "",
    val knownUntil: LocalDate? = null,
    /** True when the note carries FSRS/SM-2 state and must never be written. */
    val schedulerManaged: Boolean = false,
    val progress: Progress = Progress(),
    /** Raw note text as loaded, kept until a successful write-back. */
    val rawText: String = "",
    /** SAF document URI, or null for bundled samples. */
    val fileUri: String? = null,
) {
    val key: String get() = "$sourceKey::$questionId"

    val isSample: Boolean get() = sourceKey == SAMPLE_SOURCE

    val topic: String
        get() = listOf(skill, domain, section, test, course)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: "General"

    /** Compact label for list rows. */
    val displayTitle: String
        get() = prompt
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(140)
            ?: questionId

    val confidenceLabel: String
        get() = when (mode) {
            QuestionMode.MCQ -> "Multiple choice"
            QuestionMode.SELECT_ALL -> "Select all that apply"
            QuestionMode.TRUE_FALSE -> "True / false"
            QuestionMode.SELF_GRADED -> "Self-graded"
            QuestionMode.FILL_IN -> "Fill in the blank"
            QuestionMode.MATCHING -> "Matching"
        }

    fun withProgress(newProgress: Progress): Question = copy(progress = newProgress)

    fun withRawText(newRawText: String, newProgress: Progress): Question =
        copy(rawText = newRawText, progress = newProgress)

    companion object {
        const val SAMPLE_SOURCE = "sample"
    }
}
