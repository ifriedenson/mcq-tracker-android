package com.mcqtracker.android.domain

import com.mcqtracker.android.data.HistoryRow
import com.mcqtracker.android.data.Progress
import com.mcqtracker.android.data.Question
import com.mcqtracker.android.data.QuestionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {

    private val today = LocalDate.of(2026, 10, 31)

    private fun question(
        id: String,
        skill: String = "",
        progress: Progress = Progress(),
        nextReview: LocalDate? = null,
        status: String = "",
    ) = Question(
        sourceKey = "src",
        questionId = id,
        skill = skill,
        mode = QuestionMode.MCQ,
        answerKey = "A",
        progress = progress.copy(nextReview = nextReview ?: progress.nextReview),
        studyStatus = status,
    )

    private fun row(day: String, result: String = "correct") = HistoryRow(
        timestamp = "${day}T10:00:00",
        result = result,
        seconds = 30,
        chosen = "A",
        confidence = 2,
        errorType = "",
        attemptId = "$day-$result",
    )

    @Test
    fun `due new and missed counts`() {
        val questions = listOf(
            question("new"),
            question(
                "due",
                progress = Progress(attempts = 2, correctCount = 1, history = listOf(row("2026-10-20"))),
                nextReview = today.minusDays(1),
            ),
            question(
                "future",
                progress = Progress(attempts = 1, correctCount = 0, lastResult = "incorrect", history = listOf(row("2026-10-30", "incorrect"))),
                nextReview = today.plusDays(5),
            ),
            question(
                "missed",
                progress = Progress(attempts = 1, correctCount = 0, lastResult = "incorrect", history = listOf(row("2026-10-29", "incorrect"))),
                nextReview = today.minusDays(1),
            ),
        )

        val overview = Stats.overview(questions, today)
        // both the overdue note and the overdue missed note are due
        assertEquals(2, overview.due)
        assertEquals(1, overview.new)
        assertEquals(2, overview.missed)
        assertEquals(4, overview.attempts)
        assertEquals(1, overview.correct)
    }

    @Test
    fun `excluded and suspended notes are not queued`() {
        val excluded = question("ex", status = "excluded", progress = Progress(attempts = 1))
        val suspended = question("su", status = "suspended", progress = Progress(attempts = 1))
        assertFalse(Stats.isQueueable(excluded, today))
        assertFalse(Stats.isQueueable(suspended, today))
        assertTrue(Stats.isQueueable(question("ok"), today))
    }

    @Test
    fun `known notes are queued only after known_until passes`() {
        val future = question("known-future", status = "known").copy(knownUntil = today.plusDays(3))
        val past = question("known-past", status = "known").copy(knownUntil = today.minusDays(1))
        assertFalse(Stats.isQueueable(future, today))
        assertTrue(Stats.isQueueable(past, today))
    }

    @Test
    fun `streak counts consecutive days ending today or yesterday`() {
        val days = setOf(
            today,
            today.minusDays(1),
            today.minusDays(2),
            today.minusDays(5),
        )
        assertEquals(3, Stats.streak(days, today))
        assertEquals(0, Stats.streak(setOf(today.minusDays(4)), today))
        assertEquals(1, Stats.streak(setOf(today.minusDays(1)), today))
    }

    @Test
    fun `last seven days returns a full window`() {
        val questions = listOf(
            question("a", progress = Progress(attempts = 2, correctCount = 1, history = listOf(row("2026-10-31"), row("2026-10-30")))),
        )
        val days = Stats.last7Days(questions, today)
        assertEquals(7, days.size)
        assertEquals(1, days.last().count)
        assertEquals(1, days[days.size - 2].count)
        assertEquals(0, days.first().count)
    }

    @Test
    fun `per skill accuracy groups attempts`() {
        val questions = listOf(
            question("a", skill = "Reading", progress = Progress(attempts = 2, correctCount = 2)),
            question("b", skill = "Reading", progress = Progress(attempts = 2, correctCount = 0)),
            question("c", skill = "Algebra", progress = Progress(attempts = 1, correctCount = 1)),
        )
        val stats = Stats.perSkill(questions)
        assertEquals(2, stats.size)
        assertEquals("Reading", stats[0].skill)
        assertEquals(0.5, stats[0].accuracy, 0.001)
        assertEquals(1.0, stats[1].accuracy, 0.001)
    }

    @Test
    fun `search and filters match the expected questions`() {
        val q = question("id-1", skill = "Reading").copy(course = "Sample Course", prompt = "What does inconclusive mean?")
        assertTrue(Stats.search(q, "inconclusive"))
        assertTrue(Stats.search(q, "READING"))
        assertTrue(Stats.search(q, "id-1"))
        assertFalse(Stats.search(q, "algebra"))
        assertTrue(Stats.matchesFilter(q, "new", today))
        assertFalse(Stats.matchesFilter(q, "missed", today))
    }

    @Test
    fun `flagged filter matches only locally flagged questions`() {
        val flagged = question("f1", progress = Progress(attempts = 1, flagged = true))
        val plain = question("f2", progress = Progress(attempts = 1))
        assertTrue(Stats.matchesFilter(flagged, "flagged", today))
        assertFalse(Stats.matchesFilter(plain, "flagged", today))
    }

    @Test
    fun `leeches need four consecutive misses`() {
        fun q(vararg results: String): Question {
            val rows = results.mapIndexed { index, result ->
                HistoryRow(
                    timestamp = "2026-10-2${index}T10:00:00",
                    result = result,
                    seconds = 10,
                    chosen = "A",
                    confidence = 2,
                    errorType = "",
                    attemptId = "id-$index",
                )
            }
            return question("leech-$results", progress = Progress(attempts = rows.size, history = rows))
        }

        assertEquals(4, Stats.consecutiveMisses(q("incorrect", "incorrect", "incorrect", "incorrect")))
        assertTrue(Stats.isLeech(q("incorrect", "incorrect", "incorrect", "incorrect")))
        assertFalse(Stats.isLeech(q("incorrect", "incorrect", "incorrect")))
        // a correct answer at the end breaks the run
        assertFalse(Stats.isLeech(q("incorrect", "incorrect", "incorrect", "incorrect", "correct")))
        assertEquals(0, Stats.consecutiveMisses(q("correct")))
    }

    @Test
    fun `overview counts leeches`() {
        val rows = (0 until 4).map {
            HistoryRow("2026-10-2${it}T10:00:00", "incorrect", 10, "A", 2, "", "id-$it")
        }
        val leech = question("leech", progress = Progress(attempts = 4, history = rows))
        val clean = question("clean", progress = Progress(attempts = 1))
        assertEquals(1, Stats.overview(listOf(leech, clean), today).leeches)
    }

    @Test
    fun `activity window has the requested length and counts attempts`() {
        val rows = listOf(row("2026-10-31"), row("2026-10-22"))
        val q = question("activity", progress = Progress(attempts = 2, correctCount = 2, history = rows))
        val month = Stats.activity(listOf(q), today, 30)
        assertEquals(30, month.size)
        assertEquals(2, month.sumOf { it.count })
        assertEquals(1, month.last().count)
        assertEquals(0, month.first().count)
    }

    @Test
    fun `retention buckets attempts into weeks`() {
        val rows = listOf(
            row("2026-10-31"),
            row("2026-10-23", "incorrect"),
            row("2026-10-11"),
        )
        val q = question("retention", progress = Progress(attempts = 3, correctCount = 2, history = rows))
        val buckets = Stats.retentionByWeek(listOf(q), today, 4)
        assertEquals(4, buckets.size)
        assertEquals(0, buckets[0].attempts)
        assertEquals(1, buckets[1].attempts)
        assertEquals(1.0, buckets[1].accuracy, 0.001)
        assertEquals(1, buckets[2].attempts)
        assertEquals(0.0, buckets[2].accuracy, 0.001)
        assertEquals("This week", buckets[3].label)
        assertEquals(1, buckets[3].attempts)
    }

    @Test
    fun `priority queue ranks leeches then missed then due then new`() {
        val leechRows = (0 until 4).map { index ->
            HistoryRow(
                timestamp = "2026-10-2${index}T10:00:00",
                result = "incorrect",
                seconds = 10,
                chosen = "A",
                confidence = 2,
                errorType = "",
                attemptId = "leech-$index",
            )
        }
        val leech = question(
            "leech",
            progress = Progress(attempts = 4, history = leechRows, lastResult = "incorrect"),
        )
        val missed = question(
            "missed",
            progress = Progress(
                attempts = 2,
                correctCount = 0,
                lastResult = "incorrect",
                history = listOf(row("2026-10-29", "incorrect")),
            ),
            nextReview = today.minusDays(1),
        )
        val due = question(
            "due",
            progress = Progress(attempts = 2, correctCount = 2, history = listOf(row("2026-10-20"))),
            nextReview = today.minusDays(2),
        )
        val fresh = question("new")
        val future = question(
            "future",
            progress = Progress(attempts = 1, correctCount = 1, history = listOf(row("2026-10-30"))),
            nextReview = today.plusDays(9),
        )

        val order = Stats.priorityQueue(listOf(future, fresh, due, missed, leech), today).map { it.questionId }
        assertEquals(listOf("leech", "missed", "due", "new", "future"), order)
    }

    @Test
    fun `priority queue skips excluded questions`() {
        val excluded = question(
            "excluded",
            status = "excluded",
            progress = Progress(
                attempts = 2,
                lastResult = "incorrect",
                history = listOf(row("2026-10-29", "incorrect")),
            ),
        )
        val normal = question("normal")
        assertEquals(
            listOf("normal"),
            Stats.priorityQueue(listOf(excluded, normal), today).map { it.questionId },
        )
    }

    @Test
    fun `priority score rewards error tags`() {
        val plain = question(
            "plain",
            progress = Progress(
                attempts = 1,
                lastResult = "incorrect",
                history = listOf(row("2026-10-30", "incorrect")),
            ),
            nextReview = today.minusDays(2),
        )
        val tagged = plain.copy(
            questionId = "tagged",
            progress = plain.progress.copy(lastErrorType = "careless"),
        )
        assertTrue(Stats.priorityScore(tagged, today) > Stats.priorityScore(plain, today))
    }

    @Test
    fun `upcoming schedule folds overdue and new questions into today`() {
        val overdue = question(
            "overdue",
            progress = Progress(attempts = 2, correctCount = 1, nextReview = today.minusDays(3)),
        )
        val brandNew = question("brand-new")
        val later = question(
            "later",
            progress = Progress(attempts = 2, correctCount = 2, nextReview = today.plusDays(3)),
        )
        val beyond = question(
            "beyond",
            progress = Progress(attempts = 2, correctCount = 2, nextReview = today.plusDays(30)),
        )
        val excluded = question(
            "excluded",
            status = "excluded",
            progress = Progress(attempts = 2, nextReview = today.minusDays(1)),
        )

        val days = Stats.upcoming(listOf(overdue, brandNew, later, beyond, excluded), today, days = 7)

        assertEquals(7, days.size)
        assertEquals(today, days.first().date)
        assertEquals(2, days.first().count)
        assertEquals(0, days[1].count)
        assertEquals(1, days[3].count)
        assertEquals(3, days.sumOf { it.count })
    }

    @Test
    fun `scheduler management is detected from the property or existing state`() {
        assertTrue(Stats.isSchedulerManaged("fsrs", false))
        assertTrue(Stats.isSchedulerManaged("SM2", false))
        assertTrue(Stats.isSchedulerManaged("", true))
        assertFalse(Stats.isSchedulerManaged("ladder", false))
    }
}
