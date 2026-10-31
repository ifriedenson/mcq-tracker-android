package com.mcqtracker.android.domain

import com.mcqtracker.android.data.Choice
import com.mcqtracker.android.data.QuestionMode

/**
 * Choice-set and true/false key handling, mirroring the plugin's `src/answer-types.js`.
 *
 * Everything here is pure so it can be unit tested on the JVM.
 */
object AnswerTypes {

    private val VALID_LETTERS = ('A'..'H').map { it.toString() }

    private val TRUE_WORDS = setOf("TRUE", "T", "YES", "Y")
    private val FALSE_WORDS = setOf("FALSE", "F", "NO", "N")

    /** Extracts letters A–H from strings like `A, C`, `A/C`, `A and C`, `AC`. */
    fun letters(raw: String): List<String> {
        val tokens = raw.uppercase().split(Regex("[\\s,/&+]+")).filter { it.isNotBlank() }
        val found = LinkedHashSet<String>()
        for (token in tokens) {
            val clean = token.trim('.', ':', ';', ')', ']', '"', '\'')
            when {
                clean.length == 1 && clean in VALID_LETTERS -> found += clean
                clean.length in 2..4 && clean.all { it in 'A'..'H' } -> clean.forEach { found += it.toString() }
            }
        }
        return found.sorted()
    }

    /** True when [raw] names two or more choices. */
    fun isChoiceSet(raw: String): Boolean = letters(raw).size >= 2

    /** True when [raw] is (or maps to) a boolean answer. */
    fun trueFalseWord(raw: String): Boolean? {
        val clean = raw.trim().trim('.', ':', ';').uppercase()
        return when {
            clean in TRUE_WORDS -> true
            clean in FALSE_WORDS -> false
            else -> null
        }
    }

    /** Maps a letter such as `A` through the note's own choices to a canonical True/False. */
    fun trueFalseFromLetter(letter: String, choices: List<Choice>): Boolean? {
        val choice = choices.firstOrNull { it.letter.equals(letter.trim(), ignoreCase = true) } ?: return null
        return trueFalseWord(choice.text)
    }

    /** Sorts and de-duplicates a set of letters into the canonical `A, C` form. */
    fun formatSet(letters: Collection<String>): String =
        letters.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct().sorted().joinToString(", ")

    /** Canonicalizes a raw key for the given mode, using the note's choices when needed. */
    fun canonicalKey(mode: QuestionMode, raw: String, choices: List<Choice>): String = when (mode) {
        QuestionMode.MCQ -> letters(raw).firstOrNull() ?: raw.trim().uppercase()
        QuestionMode.SELECT_ALL -> formatSet(letters(raw))
        QuestionMode.TRUE_FALSE -> {
            val word = trueFalseWord(raw)
            when {
                word != null -> if (word) "True" else "False"
                else -> {
                    val letter = letters(raw).firstOrNull()
                    val mapped = letter?.let { trueFalseFromLetter(it, choices) }
                    if (mapped != null) {
                        if (mapped) "True" else "False"
                    } else {
                        raw.trim().ifEmpty { "True" }
                    }
                }
            }
        }
        QuestionMode.SELF_GRADED -> raw.trim()
    }

    /** True when the two sets of letters are exactly equal. */
    fun sameLetterSet(expected: String, chosen: Collection<String>): Boolean {
        val want = letters(expected).toSet()
        val got = chosen.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
        return want == got
    }
}
