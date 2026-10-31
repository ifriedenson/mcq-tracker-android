package com.mcqtracker.android.data

import com.mcqtracker.android.domain.AnswerTypes
import com.mcqtracker.android.domain.Stats
import java.time.LocalDate

/**
 * Parses a MCQ Tracker question note: YAML frontmatter + Markdown sections.
 *
 * The format is a contract with the Obsidian plugin, so every alias, precedence
 * rule, and mode-inference branch in the handoff is implemented explicitly.
 */
object NoteParser {

    private val HEADING = Regex("""^\s{0,3}#{2}\s+(.+?)\s*$""")
    private val CHOICE = Regex("""^\s*(?:[-*+]\s+)?(?:\*\*)?([A-Ha-h])(?:\*\*)?\s*[.):]\s*(?:\*\*)?\s*(.*)$""")
    private val FENCE = Regex("""^\s*---\s*$""")

    private val ANSWER_PATTERNS = listOf(
        Regex("""(?i)correct\s+answers?\s*[:\-]\s*(.+)"""),
        Regex("""(?i)\*\*answer:?\*\*\s*[:\-]?\s*(.+)"""),
        Regex("""(?i)^\s*answer\s*[:\-]\s*(.+)"""),
        Regex("""(?i)the\s+correct\s+answer\s+is\s+(.+)"""),
    )

    private val STIMULUS_ALIASES = setOf("stimulus", "passage", "source", "excerpt", "reading", "text", "figure")
    private val QUESTION_ALIASES = setOf("question", "stem", "prompt")
    private val HINT_ALIASES = setOf("hint", "clue")
    private val CHOICES_ALIASES = setOf("answer choices", "choices", "options", "answers choices")
    private val ANSWER_ALIASES = setOf("answer", "answer key", "correct answer", "answer and rationale", "solution")
    private val NOTES_ALIASES = setOf("my notes", "notes")

    private val FRONTMATTER_ANSWER_KEYS = listOf("answer", "correct_answer", "correct", "answer_key", "key")

    /** Raw frontmatter split into scalars, list blocks, and the untouched body. */
    data class Frontmatter(
        val hasFrontmatter: Boolean,
        val lines: List<String>,
        val body: String,
        val eol: String,
        val scalars: Map<String, String>,
        val lists: Map<String, List<String>>,
    )

    fun parseFrontmatter(text: String): Frontmatter {
        val eol = if (text.contains("\r\n")) "\r\n" else "\n"
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split("\n")
        val start = lines.indexOfFirst { it.isNotBlank() }
        if (start == -1 || !FENCE.matches(lines[start])) {
            return Frontmatter(false, emptyList(), text, eol, emptyMap(), emptyMap())
        }
        var end = -1
        for (i in start + 1 until lines.size) {
            if (FENCE.matches(lines[i])) {
                end = i
                break
            }
        }
        if (end == -1) {
            return Frontmatter(false, emptyList(), text, eol, emptyMap(), emptyMap())
        }
        val fmLines = lines.subList(start + 1, end)
        val body = lines.subList(end + 1, lines.size).joinToString("\n")
        val scalars = LinkedHashMap<String, String>()
        val lists = LinkedHashMap<String, List<String>>()
        var currentKey: String? = null
        val currentItems = mutableListOf<String>()
        fun flush() {
            val key = currentKey
            if (key != null && currentItems.isNotEmpty()) {
                lists[key] = currentItems.toList()
                currentItems.clear()
            }
        }
        for (line in fmLines) {
            val item = Regex("""^\s*-\s+(.*)$""").find(line)
            if (item != null && currentKey != null) {
                currentItems += unquote(item.groupValues[1].trim())
                continue
            }
            val entry = Regex("""^([^:#][^:]*):\s*(.*)$""").find(line)
            if (entry != null) {
                flush()
                val key = entry.groupValues[1].trim().lowercase()
                currentKey = key
                val value = entry.groupValues[2].trim()
                if (value.isNotEmpty()) {
                    val inlineList = parseInlineList(value)
                    if (inlineList != null) {
                        lists[key] = inlineList
                    } else {
                        scalars[key] = unquote(value)
                    }
                }
                continue
            }
        }
        flush()
        return Frontmatter(true, fmLines, body, eol, scalars, lists)
    }

    /**
     * Parses a note into a [Question], or null when it is not a question note
     * (no frontmatter or an empty `question_id`).
     */
    fun parse(text: String, sourceKey: String, fileUri: String? = null): Question? {
        val fm = parseFrontmatter(text)
        if (!fm.hasFrontmatter) return null
        val questionId = fm.scalars["question_id"]?.trim().orEmpty()
        if (questionId.isEmpty()) return null

        val sections = parseSections(fm.body)
        val choices = parseChoices(sections.choices())
        val rawKey = resolveAnswerKey(fm, sections.answer())
        val mode = inferMode(fm.scalars["question_type"].orEmpty(), rawKey, choices)
        val answerKey = normalizeKey(mode, rawKey, choices)

        val scheduler = fm.scalars["scheduler"].orEmpty()
        val hasSrsState = fm.scalars.containsKey("srs") || fm.scalars.containsKey("sm2")
        val stimulusSection = sections.stimulus()
        val prompt = MarkdownLite.toPlainText(questionText(sections, fm.body))

        return Question(
            sourceKey = sourceKey,
            questionId = questionId,
            course = plain(fm.scalars["course"]),
            test = plain(fm.scalars["test"]),
            section = plain(fm.scalars["section"]),
            domain = plain(fm.scalars["domain"]),
            skill = plain(fm.scalars["skill"]),
            difficulty = plain(fm.scalars["difficulty"]),
            unit = plain(fm.scalars["unit"]),
            questionType = plain(fm.scalars["question_type"]),
            mode = mode,
            choices = choices,
            answerKey = answerKey,
            stimulus = MarkdownLite.toPlainText(stimulusSection),
            prompt = prompt,
            hint = MarkdownLite.toPlainText(sections.hint()),
            explanation = MarkdownLite.toPlainText(sections.answer()),
            notes = MarkdownLite.toPlainText(sections.notes()),
            stimulusId = plain(fm.scalars["stimulus_id"]),
            targetSeconds = fm.scalars["target_seconds"]?.trim()?.toIntOrNull(),
            scheduler = scheduler,
            studyStatus = plain(fm.scalars["study_status"]),
            knownUntil = parseDate(fm.scalars["known_until"]),
            schedulerManaged = Stats.isSchedulerManaged(scheduler, hasSrsState),
            progress = parseProgress(fm),
            rawText = text,
            fileUri = fileUri,
        )
    }

    /** Fills a missing `## Stimulus` from another question with the same `stimulus_id`. */
    fun borrowStimuli(questions: List<Question>): List<Question> {
        val donors = questions
            .filter { it.stimulusId.isNotBlank() && it.stimulus.isNotBlank() }
            .associateBy { it.stimulusId }
        return questions.map { question ->
            if (question.stimulus.isBlank() && question.stimulusId.isNotBlank()) {
                val donor = donors[question.stimulusId]
                if (donor != null) question.copy(stimulus = donor.stimulus) else question
            } else {
                question
            }
        }
    }

    // ---- sections -------------------------------------------------------

    private class Sections(private val map: Map<String, String>) {
        fun find(aliases: Set<String>): String =
            aliases.firstNotNullOfOrNull { map[it] } ?: ""

        fun stimulus() = find(STIMULUS_ALIASES)
        fun question() = find(QUESTION_ALIASES)
        fun hint() = find(HINT_ALIASES)
        fun choices() = find(CHOICES_ALIASES)
        fun answer() = find(ANSWER_ALIASES)
        fun notes() = find(NOTES_ALIASES)
    }

    private fun parseSections(body: String): Sections {
        val map = LinkedHashMap<String, String>()
        var current: String? = null
        val buffer = StringBuilder()
        fun flush() {
            val key = current
            if (key != null) map[key] = buffer.toString().trim()
            buffer.clear()
        }
        for (line in body.replace("\r\n", "\n").split("\n")) {
            val heading = HEADING.find(line)
            if (heading != null) {
                flush()
                current = normalizeHeading(heading.groupValues[1])
                continue
            }
            if (current != null) buffer.append(line).append('\n')
        }
        flush()
        return Sections(map)
    }

    private fun normalizeHeading(raw: String): String =
        raw.replace(Regex("""[*_`]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()

    private fun questionText(sections: Sections, body: String): String {
        val explicit = sections.question()
        if (explicit.isNotBlank()) return explicit
        // No `## Question`: use the first prose paragraph before the first heading.
        val firstSection = body.replace("\r\n", "\n").split("\n")
            .takeWhile { !it.trimStart().startsWith("## ") }
            .joinToString("\n")
            .trim()
        return firstSection
    }

    // ---- choices --------------------------------------------------------

    fun parseChoices(section: String): List<Choice> {
        if (section.isBlank()) return emptyList()
        val choices = mutableListOf<Choice>()
        var lastIndex = -1
        for (rawLine in section.replace("\r\n", "\n").split("\n")) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val match = CHOICE.find(line)
            if (match != null) {
                val letter = match.groupValues[1].uppercase()
                val text = cleanChoiceText(match.groupValues[2])
                choices += Choice(letter, text)
                lastIndex = choices.lastIndex
            } else if (lastIndex >= 0) {
                // Wrapped continuation line of the previous choice.
                val previous = choices[lastIndex]
                val continuation = cleanChoiceText(line)
                if (continuation.isNotEmpty()) {
                    choices[lastIndex] = previous.copy(
                        text = (previous.text + " " + continuation).trim(),
                    )
                }
            }
        }
        return choices.distinctBy { it.letter }.sortedBy { it.letter }
    }

    private fun cleanChoiceText(raw: String): String =
        raw.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""^\s*[-*+]\s*"""), "")
            .replace(Regex("""^\*+\s*"""), "")
            .trim()

    // ---- answer key -----------------------------------------------------

    fun resolveAnswerKey(fm: Frontmatter, sectionText: String): String? {
        for (key in FRONTMATTER_ANSWER_KEYS) {
            val value = fm.scalars[key]
            if (!value.isNullOrBlank()) return value.trim()
        }
        return answerFromSection(sectionText)
    }

    private fun answerFromSection(section: String): String? {
        if (section.isBlank()) return null
        val plainLines = section.replace("\r\n", "\n").split("\n")
        for (line in plainLines) {
            val cleaned = line.replace(Regex("""\*\*"""), "").trim()
            if (cleaned.isEmpty()) continue
            for (pattern in ANSWER_PATTERNS) {
                val match = pattern.find(cleaned)
                if (match != null) {
                    val value = match.groupValues[1]
                        .replace(Regex("""\*\*"""), "")
                        .trim()
                        .trimEnd('.', ':', ';')
                    if (value.isNotEmpty()) return value
                }
            }
        }
        return null
    }

    // ---- mode inference -------------------------------------------------

    fun inferMode(questionType: String, rawKey: String?, choices: List<Choice>): QuestionMode {
        val type = questionType.lowercase()
        val inferred = when {
            listOf("spr", "student-produced", "grid-in", "free-response", "short-answer", "numeric", "fill")
                .any { type.contains(it) } -> QuestionMode.SELF_GRADED

            listOf("select_all", "select all", "sata", "multi-select", "multi select", "multi-answer", "checkbox")
                .any { type.contains(it) } -> QuestionMode.SELECT_ALL

            listOf("true_false", "true/false", "true false", "boolean", "tf").any { type.contains(it) } ->
                QuestionMode.TRUE_FALSE

            listOf("mcq", "multiple", "choice", "select").any { type.contains(it) } ->
                if (rawKey != null && AnswerTypes.isChoiceSet(rawKey)) QuestionMode.SELECT_ALL else QuestionMode.MCQ

            else -> inferFromKey(rawKey, choices)
        }
        return downgradeIfUnanswerable(inferred, choices)
    }

    private fun inferFromKey(rawKey: String?, choices: List<Choice>): QuestionMode {
        if (rawKey.isNullOrBlank()) return QuestionMode.SELF_GRADED

        if (choices.size == 2) {
            val first = AnswerTypes.trueFalseWord(choices[0].text)
            val second = AnswerTypes.trueFalseWord(choices[1].text)
            if (first != null && second != null && first != second) return QuestionMode.TRUE_FALSE
        }
        if (AnswerTypes.trueFalseWord(rawKey) != null) {
            return if (choices.isEmpty()) QuestionMode.TRUE_FALSE else QuestionMode.TRUE_FALSE
        }
        if (AnswerTypes.isChoiceSet(rawKey)) return QuestionMode.SELECT_ALL
        val letters = AnswerTypes.letters(rawKey)
        if (letters.size == 1) {
            val mapped = if (choices.isNotEmpty()) AnswerTypes.trueFalseFromLetter(letters.first(), choices) else null
            if (mapped != null) return QuestionMode.TRUE_FALSE
            return QuestionMode.MCQ
        }
        return QuestionMode.SELF_GRADED
    }

    private fun downgradeIfUnanswerable(mode: QuestionMode, choices: List<Choice>): QuestionMode =
        when {
            (mode == QuestionMode.MCQ || mode == QuestionMode.SELECT_ALL) && choices.size < 2 ->
                QuestionMode.SELF_GRADED
            else -> mode
        }

    private fun normalizeKey(mode: QuestionMode, rawKey: String?, choices: List<Choice>): String {
        val raw = rawKey.orEmpty()
        if (mode == QuestionMode.SELF_GRADED) return raw.trim()
        if (raw.isBlank()) return ""
        return AnswerTypes.canonicalKey(mode, raw, choices)
    }

    // ---- progress -------------------------------------------------------

    fun parseProgress(fm: Frontmatter): Progress {
        val rows = (fm.lists["history"] ?: emptyList()).mapNotNull { HistoryRow.parse(it) }
        return Progress(
            attempts = fm.scalars["attempts"]?.toIntOrNull() ?: rows.size,
            correctCount = fm.scalars["correct_count"]?.toIntOrNull()
                ?: rows.count { it.result == "correct" },
            streak = fm.scalars["streak"]?.toIntOrNull() ?: 0,
            nextReview = parseDate(fm.scalars["next_review"]),
            lastResult = fm.scalars["result"]?.trim()?.takeIf { it.isNotEmpty() },
            lastConfidence = fm.scalars["last_confidence"]?.toIntOrNull(),
            lastErrorType = fm.scalars["last_error_type"]?.trim().orEmpty(),
            timeSpentTotal = fm.scalars["time_spent_total"]?.toIntOrNull() ?: 0,
            lastTimeSpent = fm.scalars["last_time_spent"]?.toIntOrNull() ?: 0,
            lastAnswer = fm.scalars["last_answer"]?.trim().orEmpty(),
            history = rows,
            flagged = fm.scalars["flagged"]?.trim().equals("true", ignoreCase = true),
        )
    }

    fun parseDate(raw: String?): LocalDate? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    }

    // ---- helpers --------------------------------------------------------

    private fun plain(raw: String?): String = raw?.trim().orEmpty()

    private fun unquote(raw: String): String {
        val value = raw.trim()
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
        }
        if (value.length >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length - 1).replace("''", "'")
        }
        return value
    }

    private fun parseInlineList(raw: String): List<String>? {
        val value = raw.trim()
        if (!value.startsWith("[") || !value.endsWith("]")) return null
        val inner = value.substring(1, value.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        return inner.split(",").map { unquote(it.trim()) }
    }
}
