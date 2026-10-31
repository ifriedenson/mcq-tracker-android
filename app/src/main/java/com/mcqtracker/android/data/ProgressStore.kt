package com.mcqtracker.android.data

import android.content.Context
import java.time.LocalDate

/**
 * Local mirror of every question's progress, stored as JSON in SharedPreferences.
 *
 * Progress is kept locally even for sample notes, read-only notes and failed
 * writes, so a review is never lost. Keys are `sourceKey::question_id`.
 */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAll(): Map<String, Progress> = decode(prefs.getString(KEY_PROGRESS, null))

    fun save(questionKey: String, progress: Progress) {
        val all = loadAll().toMutableMap()
        all[questionKey] = progress
        prefs.edit().putString(KEY_PROGRESS, encode(all)).apply()
    }

    fun replaceAll(map: Map<String, Progress>) {
        prefs.edit().putString(KEY_PROGRESS, encode(map)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PROGRESS).apply()
    }

    companion object {
        private const val PREFS = "mcq_tracker_progress"
        private const val KEY_PROGRESS = "progress_v1"

        /** Serializes the whole map; public so it can be unit tested without Android. */
        fun encode(map: Map<String, Progress>): String {
            val root = map.mapValues { (_, progress) -> progressToMap(progress) }
            return Json.encode(root)
        }

        fun decode(raw: String?): Map<String, Progress> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                Json.decodeObject(raw).mapValues { (_, value) ->
                    progressFromMap(value as? Map<*, *> ?: emptyMap<String, Any?>())
                }
            }.getOrDefault(emptyMap())
        }

        private fun progressToMap(progress: Progress): Map<String, Any?> = mapOf(
            "attempts" to progress.attempts,
            "correctCount" to progress.correctCount,
            "streak" to progress.streak,
            "nextReview" to progress.nextReview?.format(Scheduler.DATE),
            "lastResult" to progress.lastResult,
            "lastConfidence" to progress.lastConfidence,
            "lastErrorType" to progress.lastErrorType,
            "timeSpentTotal" to progress.timeSpentTotal,
            "lastTimeSpent" to progress.lastTimeSpent,
            "lastAnswer" to progress.lastAnswer,
            "flagged" to progress.flagged,
            "history" to progress.history.map { it.serialize() },
        )

        private fun progressFromMap(map: Map<*, *>): Progress {
            val history = (map["history"] as? List<*>)
                ?.mapNotNull { it?.toString()?.let(HistoryRow::parse) }
                ?: emptyList()
            return Progress(
                attempts = (map["attempts"] as? Number)?.toInt() ?: history.size,
                correctCount = (map["correctCount"] as? Number)?.toInt()
                    ?: history.count { it.result == "correct" },
                streak = (map["streak"] as? Number)?.toInt() ?: 0,
                nextReview = (map["nextReview"] as? String)?.let { parseDate(it) },
                lastResult = map["lastResult"] as? String,
                lastConfidence = (map["lastConfidence"] as? Number)?.toInt(),
                lastErrorType = map["lastErrorType"] as? String ?: "",
                timeSpentTotal = (map["timeSpentTotal"] as? Number)?.toInt() ?: 0,
                lastTimeSpent = (map["lastTimeSpent"] as? Number)?.toInt() ?: 0,
                lastAnswer = map["lastAnswer"] as? String ?: "",
                history = history,
                flagged = map["flagged"] as? Boolean ?: false,
            )
        }

        private fun parseDate(raw: String): LocalDate? =
            runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }
}

/**
 * Merge rule from the handoff: prefer the copy with more attempts, then more
 * history rows, then a non-empty local error tag; otherwise keep the note's state.
 */
object ProgressMerge {
    fun merge(note: Progress, local: Progress?): Progress {
        if (local == null) return note
        return when {
            local.attempts > note.attempts -> local
            local.attempts < note.attempts -> note
            local.history.size > note.history.size -> local
            local.history.size < note.history.size -> note
            note.lastErrorType.isBlank() && local.lastErrorType.isNotBlank() -> local
            else -> note
        }
    }
}
