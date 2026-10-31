package com.mcqtracker.android.data

/**
 * Parses flashcard syntax from notes that are not MCQ Tracker question notes.
 *
 * Supported shapes (matching the MIT-licensed Obsidian Spaced Repetition plugin):
 * - `Front::Back` basic
 * - `Front:::Back` reversed (produces two cards)
 * - multi-line, where a line containing only `?` separates front and back
 * - `==highlighted==` cloze deletions (one card per highlight)
 *
 * Notes with a `question_id` are ignored entirely so MCQ Tracker notes never
 * become flashcards.
 */
object FlashcardParser {

    private val CLOZE = Regex("""==(.+?)==""")

    fun parse(text: String, sourceKey: String, fileUri: String? = null): List<Flashcard> {
        val frontmatter = NoteParser.parseFrontmatter(text)
        if (!frontmatter.scalars["question_id"].isNullOrBlank()) return emptyList()

        val body = if (frontmatter.hasFrontmatter) frontmatter.body else text
        val lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val cards = mutableListOf<Flashcard>()
        var index = 0

        fun add(kind: FlashcardKind, front: String, back: String, suffix: String = "") {
            val cleanFront = MarkdownLite.toSingleLine(front)
            val cleanBack = MarkdownLite.toSingleLine(back)
            if (cleanFront.isBlank() || cleanBack.isBlank()) return
            index += 1
            val hash = Integer.toHexString((cleanFront + "|" + cleanBack + "|" + kind.name).hashCode())
            cards += Flashcard(
                sourceKey = sourceKey,
                cardId = "$hash-$index$suffix",
                kind = kind,
                front = cleanFront,
                back = cleanBack,
                rawText = text,
                fileUri = fileUri,
            )
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("#")) {
                i++
                continue
            }

            // Multi-line: front, then a line that is only "?", then the back.
            if (i + 1 < lines.size && lines[i + 1].trim() == "?") {
                val answerLines = mutableListOf<String>()
                var j = i + 2
                while (j < lines.size && lines[j].isNotBlank() && !lines[j].trim().startsWith("#")) {
                    answerLines += lines[j].trim()
                    j++
                }
                if (answerLines.isNotEmpty()) {
                    add(FlashcardKind.MULTI_LINE, line, answerLines.joinToString(" "))
                    i = j
                    continue
                }
            }

            when {
                line.contains(":::") -> {
                    val parts = line.split(":::", limit = 2)
                    if (parts.size == 2) {
                        add(FlashcardKind.REVERSED, parts[0], parts[1], "-a")
                        add(FlashcardKind.REVERSED, parts[1], parts[0], "-b")
                    }
                }

                line.contains("::") -> {
                    val parts = line.split("::", limit = 2)
                    if (parts.size == 2) add(FlashcardKind.BASIC, parts[0], parts[1])
                }

                else -> {
                    val matches = CLOZE.findAll(line).toList()
                    // One card per deletion: hide this one, reveal the others as plain text.
                    matches.forEachIndexed { targetIndex, match ->
                        val front = StringBuilder()
                        var cursor = 0
                        matches.forEachIndexed { index, current ->
                            front.append(line, cursor, current.range.first)
                            front.append(if (index == targetIndex) "[...]" else current.groupValues[1])
                            cursor = current.range.last + 1
                        }
                        front.append(line.substring(cursor))
                        add(FlashcardKind.CLOZE, front.toString(), match.groupValues[1])
                    }
                }
            }
            i++
        }

        if (index == 0) return emptyList()
        return cards.distinctBy { it.cardId }
    }
}
