package com.mcqtracker.android.data

/** Card shapes borrowed from the MIT-licensed Obsidian Spaced Repetition plugin. */
enum class FlashcardKind {
    BASIC,
    REVERSED,
    MULTI_LINE,
    CLOZE,
}

/**
 * A flashcard discovered in a note that is **not** a MCQ Tracker question note.
 *
 * Flashcard notes never carry `question_id`, so adding this module cannot change
 * how the Obsidian plugin sees MCQ Tracker notes.
 */
data class Flashcard(
    val sourceKey: String,
    val cardId: String,
    val kind: FlashcardKind,
    val front: String,
    val back: String,
    val rawText: String = "",
    val fileUri: String? = null,
    val progress: Progress = Progress(),
) {
    val key: String get() = "$sourceKey::$cardId"

    val isNew: Boolean get() = progress.attempts == 0

    val kindLabel: String
        get() = when (kind) {
            FlashcardKind.BASIC -> "Basic"
            FlashcardKind.REVERSED -> "Reversed"
            FlashcardKind.MULTI_LINE -> "Multi-line"
            FlashcardKind.CLOZE -> "Cloze"
        }

    fun withProgress(newProgress: Progress): Flashcard = copy(progress = newProgress)
}
