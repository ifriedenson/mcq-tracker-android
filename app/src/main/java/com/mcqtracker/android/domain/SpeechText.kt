package com.mcqtracker.android.domain

import com.mcqtracker.android.data.Question

/**
 * Builds the text handed to Android's text-to-speech engine.
 *
 * Kept pure so the wording and joining rules can be unit tested without an
 * Android runtime or a device TTS engine.
 */
object SpeechText {

    fun question(question: Question): String {
        val parts = mutableListOf<String>()
        if (question.stimulus.isNotBlank()) parts += question.stimulus.trim()
        if (question.prompt.isNotBlank()) parts += question.prompt.trim()
        if (question.choices.isNotEmpty()) {
            parts += question.choices.joinToString(", ") { choice ->
                "${choice.letter}. ${choice.text.trim()}"
            }
        }
        return join(parts)
    }

    fun result(question: Question, correctAnswer: String, explanation: String): String {
        val parts = mutableListOf<String>()
        if (correctAnswer.isNotBlank()) parts += "The correct answer is $correctAnswer."
        if (explanation.isNotBlank()) parts += explanation.trim()
        if (parts.isEmpty()) parts += "No explanation recorded for this question."
        return join(parts)
    }

    private fun join(parts: List<String>): String =
        parts.filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("""\.\s*\.+"""), ".")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
