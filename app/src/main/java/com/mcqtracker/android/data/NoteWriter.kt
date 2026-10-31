package com.mcqtracker.android.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Writes plugin-compatible review state back into a note's frontmatter.
 *
 * Guarantees, per the handoff contract:
 * - the Markdown body is never touched;
 * - unknown properties (`srs`, `sm2`, `flagged`, `study_status`, …) survive;
 * - line endings and the trailing newline are preserved;
 * - a history row is appended at most once per attempt id.
 */
object NoteWriter {

    private val KEY_LINE = Regex("""^([^:#][^:]*):\s*(.*)$""")
    private val LIST_ITEM = Regex("""^\s*-\s+.*$""")

    data class Attempt(
        val result: String,
        val dateAttempted: String,
        val attempts: Int,
        val correctCount: Int,
        val streak: Int,
        val timeSpentTotal: Int,
        val lastTimeSpent: Int,
        val lastAnswer: String,
        val lastConfidence: Int,
        val lastErrorType: String,
        val nextReview: String,
        val attemptId: String,
        val history: List<HistoryRow>,
    )

    /** Builds the write payload from a scheduler outcome. */
    fun attemptFrom(outcome: Scheduler.Outcome, now: LocalDateTime): Attempt = Attempt(
        result = if (outcome.row.result == "correct") "correct" else "incorrect",
        dateAttempted = now.format(Scheduler.NOTE_TIMESTAMP),
        attempts = outcome.progress.attempts,
        correctCount = outcome.progress.correctCount,
        streak = outcome.progress.streak,
        timeSpentTotal = outcome.progress.timeSpentTotal,
        lastTimeSpent = outcome.progress.lastTimeSpent,
        lastAnswer = outcome.progress.lastAnswer,
        lastConfidence = outcome.progress.lastConfidence ?: 2,
        lastErrorType = outcome.progress.lastErrorType,
        nextReview = outcome.nextReview.format(Scheduler.DATE),
        attemptId = outcome.attemptId,
        history = outcome.progress.history,
    )

    /** Applies an attempt to [rawText], returning the new note text. */
    fun applyAttempt(rawText: String, attempt: Attempt): String {
        val fm = NoteParser.parseFrontmatter(rawText)
        val scalars = LinkedHashMap<String, String>()
        scalars["result"] = attempt.result
        scalars["date attempted"] = attempt.dateAttempted
        scalars["attempts"] = attempt.attempts.toString()
        scalars["correct_count"] = attempt.correctCount.toString()
        scalars["streak"] = attempt.streak.toString()
        scalars["time_spent_total"] = attempt.timeSpentTotal.toString()
        scalars["last_time_spent"] = attempt.lastTimeSpent.toString()
        scalars["last_answer"] = attempt.lastAnswer
        scalars["last_confidence"] = attempt.lastConfidence.toString()
        scalars["last_error_type"] = attempt.lastErrorType
        scalars["next_review"] = attempt.nextReview
        scalars["last_attempt_id"] = attempt.attemptId

        val existingFirst = fm.scalars["first_attempted"]?.trim().orEmpty()
        if (existingFirst.isEmpty()) {
            scalars["first_attempted"] = attempt.dateAttempted
        }

        val merged = mergeHistory(fm.lists["history"] ?: emptyList(), attempt.history)
        return rewrite(rawText, scalars, merged)
    }

    /** Applies an error tag to the matching history row and `last_error_type`. */
    fun applyErrorTag(rawText: String, attemptId: String, errorType: String): String {
        val fm = NoteParser.parseFrontmatter(rawText)
        val rows = (fm.lists["history"] ?: emptyList()).mapNotNull { raw ->
            val row = HistoryRow.parse(raw) ?: return@mapNotNull null
            if (row.attemptId == attemptId) row.copy(errorType = errorType) else row
        }
        return rewrite(rawText, mapOf("last_error_type" to errorType), rows)
    }

    private fun mergeHistory(existing: List<String>, rows: List<HistoryRow>): List<HistoryRow> {
        val parsed = existing.mapNotNull { HistoryRow.parse(it) }.toMutableList()
        for (row in rows) {
            if (parsed.none { it.attemptId == row.attemptId }) parsed += row
        }
        return parsed
    }

    /**
     * Rewrites the frontmatter keeping every unknown line, the key order, the body
     * and the file's line endings.
     */
    private fun rewrite(
        rawText: String,
        scalarUpdates: Map<String, String>,
        history: List<HistoryRow>,
    ): String {
        val eol = if (rawText.contains("\r\n")) "\r\n" else "\n"
        val trailingNewline = rawText.endsWith("\n")
        val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
        var lines = normalized.split("\n").toMutableList()

        val hasFrontmatter = lines.isNotEmpty() && lines.first().trim() == "---" &&
            lines.drop(1).any { it.trim() == "---" }

        if (!hasFrontmatter) {
            val header = mutableListOf("---")
            scalarUpdates.forEach { (key, value) -> header += formatScalar(key, value) }
            header += "history:"
            history.forEach { header += "  - ${quote(it.serialize())}" }
            header += "---"
            val body = normalized.trimStart('\n')
            lines = (header + body).toMutableList()
            var output = lines.joinToString("\n")
            if (trailingNewline) output += "\n"
            return output.replace("\n", eol)
        }

        val end = lines.drop(1).indexOfFirst { it.trim() == "---" } + 1
        val fmLines = lines.subList(1, end).toMutableList()
        val bodyLines = lines.subList(end + 1, lines.size)

        val pending = LinkedHashMap<String, String>()
        scalarUpdates.forEach { (key, value) -> pending[key.lowercase()] = value }
        val originalKeyCase = HashMap<String, String>()

        val output = mutableListOf<String>()
        var i = 0
        var sawHistory = false
        while (i < fmLines.size) {
            val line = fmLines[i]
            val match = KEY_LINE.find(line)
            if (match == null) {
                output += line
                i++
                continue
            }
            val keyRaw = match.groupValues[1].trim()
            val key = keyRaw.lowercase()
            originalKeyCase[key] = keyRaw

            if (key == "history") {
                sawHistory = true
                var j = i + 1
                while (j < fmLines.size && LIST_ITEM.matches(fmLines[j])) j++
                output += "history:"
                history.forEach { output += "  - ${quote(it.serialize())}" }
                pending.remove("history")
                i = j
                continue
            }

            val newValue = pending.remove(key)
            if (newValue != null) {
                output += formatScalar(keyRaw, newValue)
            } else {
                output += line
            }
            i++
        }

        // Append anything new (including a missing history list) before the closing fence.
        pending.forEach { (key, value) ->
            val display = originalKeyCase[key] ?: key
            output += formatScalar(display, value)
        }
        if (!sawHistory) {
            output += "history:"
            history.forEach { output += "  - ${quote(it.serialize())}" }
        }

        val result = mutableListOf<String>()
        result += "---"
        result += output
        result += "---"
        result += bodyLines
        var text = result.joinToString("\n")
        if (trailingNewline) text += "\n"
        return text.replace("\n", eol)
    }

    private fun formatScalar(key: String, value: String): String = "$key: ${quoteIfNeeded(value)}"

    private fun quoteIfNeeded(value: String): String {
        val needsQuotes = value.isEmpty() ||
            value.startsWith(" ") || value.endsWith(" ") ||
            value.contains(": ") || value.contains("#") ||
            value.startsWith("{") || value.startsWith("[") ||
            value.equals("true", true) || value.equals("false", true) ||
            value.equals("null", true)
        return if (needsQuotes) quote(value) else value
    }

    private fun quote(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", " ")
            .replace("\n", "\\n") + "\""

    // ---- helpers used by the ViewModel ---------------------------------

    fun nextAttemptId(): String = UUID.randomUUID().toString()

    fun todayString(now: LocalDateTime): String = now.format(Scheduler.DATE)

    fun today(now: LocalDateTime): LocalDate = now.toLocalDate()
}
