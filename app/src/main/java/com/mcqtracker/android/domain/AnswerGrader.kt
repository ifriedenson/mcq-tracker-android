package com.mcqtracker.android.domain

import com.mcqtracker.android.data.Choice
import com.mcqtracker.android.data.Question
import com.mcqtracker.android.data.QuestionMode

/** Grading for every supported question mode. Pure and unit-testable. */
object AnswerGrader {

    data class Result(
        val correct: Boolean,
        /** What to store in `last_answer` / the history `chosen` field. */
        val chosen: String,
    )

    /**
     * Grades one submission.
     *
     * @param selectedLetters the letters the user picked (empty for self-graded)
     * @param selfGrade for [QuestionMode.SELF_GRADED] only: true = "I got it right"
     */
    fun grade(
        question: Question,
        selectedLetters: Collection<String>,
        selfGrade: Boolean? = null,
        typedAnswer: String = "",
        matching: Map<String, String> = emptyMap(),
    ): Result {
        val picked = selectedLetters.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
        return when (question.mode) {
            QuestionMode.MCQ -> {
                val key = question.answerKey.trim().uppercase()
                Result(correct = picked.size == 1 && picked.first() == key, chosen = picked.joinToString(""))
            }

            QuestionMode.SELECT_ALL -> {
                val correct = AnswerTypes.sameLetterSet(question.answerKey, picked)
                Result(correct = correct, chosen = picked.joinToString(", "))
            }

            QuestionMode.TRUE_FALSE -> {
                val chosenValue = when {
                    picked.isEmpty() -> null
                    // Map through the note's own choices when they are custom labels.
                    question.choices.isNotEmpty() ->
                        AnswerTypes.trueFalseFromLetter(picked.first(), question.choices)
                    // Bare True/False buttons: letters are not meaningful.
                    else -> null
                }
                val correct = when {
                    chosenValue != null -> canonical(chosenValue) == question.answerKey
                    picked.size == 1 -> {
                        val asWord = AnswerTypes.trueFalseWord(picked.first())
                        asWord != null && canonical(asWord) == question.answerKey
                    }
                    else -> false
                }
                Result(correct = correct, chosen = picked.joinToString(""))
            }

            QuestionMode.SELF_GRADED -> Result(
                correct = selfGrade == true,
                chosen = "",
            )

            QuestionMode.FILL_IN -> {
                // The key may list alternatives separated by "|" or ";".
                val accepted = question.answerKey
                    .split("|", ";")
                    .map { normalize(it) }
                    .filter { it.isNotEmpty() }
                val given = normalize(typedAnswer)
                Result(
                    correct = given.isNotEmpty() && accepted.contains(given),
                    chosen = typedAnswer.trim(),
                )
            }

            QuestionMode.MATCHING -> {
                val expected = question.pairs.associate { it.left to it.right }
                val complete = expected.isNotEmpty() && expected.keys.all { matching.containsKey(it) }
                val correct = complete && expected.all { (left, right) ->
                    normalize(matching[left].orEmpty()) == normalize(right)
                }
                val chosen = matching.entries
                    .sortedBy { it.key }
                    .joinToString("; ") { "${it.key}=${it.value}" }
                Result(correct = correct, chosen = chosen)
            }
        }
    }

    /** Case, whitespace, quotes and trailing punctuation do not change an answer. */
    private fun normalize(value: String): String {
        var text = value.trim()
        text = text.removeSurrounding("\"").removeSurrounding("'")
        return text.lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.', '!', ',', ';', ':', '?')
            .trim()
    }

    private fun canonical(value: Boolean): String = if (value) "True" else "False"

    /** Letters of the choices that make up the correct answer, for highlighting. */
    fun correctChoiceLetters(question: Question): List<String> = when (question.mode) {
        QuestionMode.MCQ -> listOf(question.answerKey.trim().uppercase())
        QuestionMode.SELECT_ALL -> AnswerTypes.letters(question.answerKey)
        QuestionMode.TRUE_FALSE -> question.choices.filter {
            val word = AnswerTypes.trueFalseWord(it.text.trim()) ?: return@filter false
            canonical(word) == question.answerKey
        }.map { it.letter }
        QuestionMode.SELF_GRADED -> emptyList()
        QuestionMode.FILL_IN -> emptyList()
        QuestionMode.MATCHING -> emptyList()
    }

    /** Human-readable correct answer for the result card. */
    fun correctAnswerLabel(question: Question): String {
        if (question.mode == QuestionMode.FILL_IN) {
            return question.answerKey.ifBlank { "No accepted answer recorded" }
        }
        if (question.mode == QuestionMode.MATCHING) {
            return question.pairs.joinToString(", ") { "${it.left} → ${it.right}" }
        }
        if (question.mode == QuestionMode.SELF_GRADED) {
            return question.answerKey.ifBlank { "Self-graded" }
        }
        if (question.mode == QuestionMode.TRUE_FALSE) return question.answerKey
        val letters = correctChoiceLetters(question)
        if (letters.isEmpty()) return question.answerKey
        return letters.joinToString(", ") { letter ->
            val choice: Choice? = question.choices.firstOrNull { it.letter == letter }
            if (choice != null) "$letter. ${choice.text}" else letter
        }
    }
}
