package com.mcqtracker.android.domain

import com.mcqtracker.android.data.HistoryRow
import com.mcqtracker.android.data.Question
import com.mcqtracker.android.data.QuestionMode
import java.time.LocalDate

/** Derived numbers for the Today, Questions and Progress screens. */
object Stats {

    data class Overview(
        val due: Int = 0,
        val new: Int = 0,
        val missed: Int = 0,
        val leeches: Int = 0,
        val attempts: Int = 0,
        val correct: Int = 0,
        val accuracy: Double = 0.0,
        val streak: Int = 0,
        val studyDays: Int = 0,
        val timeSpent: Int = 0,
        val readOnlyCount: Int = 0,
    )

    data class DayCount(val date: LocalDate, val count: Int)

    data class RetentionBucket(val label: String, val attempts: Int, val correct: Int) {
        val accuracy: Double get() = if (attempts <= 0) 0.0 else correct.toDouble() / attempts
    }

    data class SkillStat(val skill: String, val attempts: Int, val correct: Int) {
        val accuracy: Double get() = if (attempts <= 0) 0.0 else correct.toDouble() / attempts
    }

    /** A note is queued when its status allows it and its review date has arrived. */
    fun isQueueable(question: Question, today: LocalDate): Boolean {
        val status = question.studyStatus.trim().lowercase()
        if (status == "excluded" || status == "suspended") return false
        if (status == "known") {
            val knownUntil = question.knownUntil
            return knownUntil != null && !knownUntil.isAfter(today)
        }
        return true
    }

    fun isDue(question: Question, today: LocalDate): Boolean {
        if (!isQueueable(question, today)) return false
        val next = question.progress.nextReview ?: return question.progress.attempts > 0
        return !next.isAfter(today)
    }

    fun isNew(question: Question): Boolean = question.progress.attempts == 0

    fun isMissed(question: Question): Boolean = question.progress.lastResult == "incorrect"

    /** Due, then new, then everything queueable. */
    fun todayQueue(questions: List<Question>, today: LocalDate): List<Question> {
        val queueable = questions.filter { isQueueable(it, today) }
        return queueable.sortedWith(
            compareByDescending<Question> { isDue(it, today) }
                .thenByDescending { isMissed(it) && isDue(it, today) }
                .thenByDescending { isNew(it) }
                .thenBy { it.progress.nextReview ?: LocalDate.MIN },
        )
    }

    /**
     * Urgency weighting for the priority queue (Incremental Writing parity):
     * leeches first, then misses, then overdue reviews, then new questions,
     * with bonuses for overdue days and a recorded error tag.
     */
    fun priorityScore(question: Question, today: LocalDate): Int {
        var score = when {
            isLeech(question) -> 60
            isMissed(question) -> 40
            isDue(question, today) && !isNew(question) -> 30
            isNew(question) -> 20
            else -> 10
        }
        val next = question.progress.nextReview
        if (next != null && next.isBefore(today)) {
            score += minOf(today.toEpochDay() - next.toEpochDay(), 10L).toInt()
        }
        if (question.progress.lastErrorType.isNotBlank()) score += 5
        return score
    }

    /** Queueable questions ordered by [priorityScore], then by how overdue they are. */
    fun priorityQueue(questions: List<Question>, today: LocalDate): List<Question> =
        questions.filter { isQueueable(it, today) }
            .sortedWith(
                compareByDescending<Question> { priorityScore(it, today) }
                    .thenBy { it.progress.nextReview ?: LocalDate.MAX }
                    .thenBy { it.questionId },
            )

    fun overview(questions: List<Question>, today: LocalDate): Overview {
        val due = questions.count { isDue(it, today) }
        val fresh = questions.count { isNew(it) && isQueueable(it, today) }
        val missed = questions.count { isMissed(it) }
        val attempts = questions.sumOf { it.progress.attempts }
        val correct = questions.sumOf { it.progress.correctCount }
        val historyDates = questions.flatMap { q -> q.progress.history.mapNotNull { parseDay(it.timestamp) } }.toSet()
        return Overview(
            due = due,
            new = fresh,
            missed = missed,
            leeches = questions.count { isLeech(it) },
            attempts = attempts,
            correct = correct,
            accuracy = if (attempts == 0) 0.0 else correct.toDouble() / attempts,
            streak = streak(historyDates, today),
            studyDays = historyDates.size,
            timeSpent = questions.sumOf { it.progress.timeSpentTotal },
            readOnlyCount = questions.count { it.schedulerManaged },
        )
    }

    /** Consecutive study days ending today or yesterday. */
    fun streak(studyDays: Set<LocalDate>, today: LocalDate): Int {
        if (studyDays.isEmpty()) return 0
        val anchor = when {
            studyDays.contains(today) -> today
            studyDays.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        var cursor = anchor
        while (studyDays.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Attempt counts for the last [days] days, oldest first. */
    fun activity(questions: List<Question>, today: LocalDate, days: Int = 30): List<DayCount> {
        val counts = HashMap<LocalDate, Int>()
        for (question in questions) {
            for (row in question.progress.history) {
                val day = parseDay(row.timestamp) ?: continue
                counts[day] = (counts[day] ?: 0) + 1
            }
        }
        return ((days - 1) downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            DayCount(day, counts[day] ?: 0)
        }
    }

    fun last7Days(questions: List<Question>, today: LocalDate): List<DayCount> =
        activity(questions, today, 7)

    /** Accuracy per week for the last [weeks] weeks, oldest first. */
    fun retentionByWeek(
        questions: List<Question>,
        today: LocalDate,
        weeks: Int = 4,
    ): List<RetentionBucket> {
        val entries = questions.flatMap { question ->
            question.progress.history.mapNotNull { row ->
                parseDay(row.timestamp)?.let { day -> day to row.result }
            }
        }
        return ((weeks - 1) downTo 0).map { offset ->
            val end = today.minusDays(offset * 7L)
            val start = end.minusDays(6)
            val inWeek = entries.filter { (day, _) -> !day.isBefore(start) && !day.isAfter(end) }
            RetentionBucket(
                label = if (offset == 0) "This week" else "${offset}w ago",
                attempts = inWeek.size,
                correct = inWeek.count { it.second == "correct" },
            )
        }
    }

    fun perSkill(questions: List<Question>): List<SkillStat> =
        questions.filter { it.skill.isNotBlank() }
            .groupBy { it.skill.trim() }
            .map { (skill, group) ->
                SkillStat(
                    skill = skill,
                    attempts = group.sumOf { it.progress.attempts },
                    correct = group.sumOf { it.progress.correctCount },
                )
            }
            .filter { it.attempts > 0 }
            .sortedByDescending { it.attempts }

    fun accuracyOf(questions: List<Question>): Double {
        val attempts = questions.sumOf { it.progress.attempts }
        if (attempts == 0) return 0.0
        return questions.sumOf { it.progress.correctCount }.toDouble() / attempts
    }

    fun parseDay(timestamp: String): LocalDate? = runCatching {
        LocalDate.parse(timestamp.substring(0, 10))
    }.getOrNull()

    /** Number of consecutive most-recent misses, newest first. */
    fun consecutiveMisses(question: Question): Int {
        var count = 0
        for (row in question.progress.history.asReversed()) {
            if (row.result == "incorrect") count++ else break
        }
        return count
    }

    const val LEECH_THRESHOLD = 4

    /** A leech is a question missed four or more times in a row. */
    fun isLeech(question: Question, threshold: Int = LEECH_THRESHOLD): Boolean =
        consecutiveMisses(question) >= threshold

    fun matchesFilter(question: Question, filter: String, today: LocalDate): Boolean = when (filter) {
        "due" -> isDue(question, today)
        "new" -> isNew(question) && isQueueable(question, today)
        "missed" -> isMissed(question)
        "flagged" -> question.progress.flagged
        else -> true
    }

    fun search(question: Question, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val haystack = listOf(
            question.prompt,
            question.questionId,
            question.skill,
            question.course,
            question.domain,
            question.section,
            question.test,
            question.notes,
        ).joinToString(" ")
        return haystack.contains(q, ignoreCase = true)
    }

    fun historyRows(question: Question): List<HistoryRow> = question.progress.history

    fun isSchedulerManaged(scheduler: String, hasSrsState: Boolean): Boolean {
        val s = scheduler.trim().lowercase()
        return hasSrsState || s == "fsrs" || s == "sm2" || s == "srs"
    }
}
