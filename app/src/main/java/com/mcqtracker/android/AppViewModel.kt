package com.mcqtracker.android

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mcqtracker.android.data.NoteWriter
import com.mcqtracker.android.data.Progress
import com.mcqtracker.android.data.ProgressStore
import com.mcqtracker.android.data.Question
import com.mcqtracker.android.data.QuestionMode
import com.mcqtracker.android.data.Scheduler
import com.mcqtracker.android.data.VaultRepository
import com.mcqtracker.android.domain.AnswerGrader
import com.mcqtracker.android.domain.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

enum class Screen { TODAY, PRACTICE, SUMMARY, QUESTIONS, PROGRESS, SETTINGS }

enum class QuestionFilter(val label: String) {
    ALL("All"),
    DUE("Due"),
    NEW("New"),
    MISSED("Missed"),
}

enum class StorageStatus(val label: String) {
    SAVED_TO_NOTE("Saved to note"),
    APP_ONLY("Saved in app only"),
    NOTE_CHANGED("Note changed elsewhere — saved in app only"),
}

data class AnswerRecord(
    val key: String,
    val title: String,
    val correct: Boolean,
    val chosen: String,
    val seconds: Int,
)

data class Submission(
    val correct: Boolean,
    val chosen: String,
    val intervalDays: Int,
    val nextReview: LocalDate,
    val storage: StorageStatus,
    val attemptId: String,
    val correctAnswer: String,
    val explanation: String,
    val errorType: String,
)

data class Summary(
    val answered: Int,
    val correct: Int,
    val totalSeconds: Int,
    val missed: List<AnswerRecord>,
) {
    val accuracy: Double get() = if (answered == 0) 0.0 else correct.toDouble() / answered
    val averageSeconds: Int get() = if (answered == 0) 0 else totalSeconds / answered
}

data class UiState(
    val screen: Screen = Screen.TODAY,
    val questions: List<Question> = emptyList(),
    val sampleMode: Boolean = true,
    val vaultUri: String? = null,
    val vaultLabel: String = "Sample vault",
    val writeBackEnabled: Boolean = false,
    val readOnlyVault: Boolean = true,
    val loading: Boolean = false,
    val truncated: Boolean = false,
    val message: String? = null,
    val search: String = "",
    val filter: QuestionFilter = QuestionFilter.ALL,
    val practiceKeys: List<String> = emptyList(),
    val practiceIndex: Int = 0,
    val selected: Set<String> = emptySet(),
    val confidence: Int = 2,
    val showHint: Boolean = false,
    val outcome: Submission? = null,
    val elapsedSeconds: Int = 0,
    val startedAtMillis: Long = 0L,
    val session: List<AnswerRecord> = emptyList(),
    val summary: Summary? = null,
) {
    val currentKey: String? get() = practiceKeys.getOrNull(practiceIndex)
    val currentQuestion: Question? get() = currentKey?.let { key -> questions.firstOrNull { it.key == key } }
    val overlay: Boolean get() = screen == Screen.PRACTICE || screen == Screen.SUMMARY
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = VaultRepository(application)
    private val store = ProgressStore(application)
    private val prefs = application.getSharedPreferences("mcq_tracker_settings", Context.MODE_PRIVATE)

    var state: UiState by mutableStateOf(
        UiState(writeBackEnabled = prefs.getBoolean(KEY_WRITE_BACK, false)),
    )
        private set

    private var lastTickSecond = 0

    init {
        val savedVault = prefs.getString(KEY_VAULT_URI, null)
        if (savedVault != null) {
            openVault(Uri.parse(savedVault))
        } else {
            loadSamples()
        }
    }

    // ---- vault loading --------------------------------------------------

    fun loadSamples() {
        state = state.copy(loading = true, message = null)
        viewModelScope.launch {
            val samples = repo.loadSamples(store)
            state = state.copy(
                questions = samples,
                sampleMode = true,
                vaultUri = null,
                vaultLabel = "Sample vault",
                loading = false,
                readOnlyVault = true,
                truncated = false,
            )
        }
    }

    fun openVault(uri: Uri) {
        state = state.copy(loading = true, message = null, vaultUri = uri.toString())
        viewModelScope.launch {
            val result = runCatching { repo.scanTree(uri, store, state.writeBackEnabled) }.getOrNull()
            if (result == null) {
                state = state.copy(loading = false, message = "Could not open that folder.")
                return@launch
            }
            prefs.edit().putString(KEY_VAULT_URI, uri.toString()).apply()
            val label = runCatching {
                android.provider.DocumentsContract.getTreeDocumentId(uri)
                    ?.substringAfterLast(':')
                    ?.substringAfterLast('/')
                    ?.ifBlank { null }
            }.getOrNull() ?: uri.lastPathSegment ?: "Vault"
            state = state.copy(
                questions = result.questions,
                sampleMode = false,
                vaultUri = uri.toString(),
                vaultLabel = label,
                loading = false,
                truncated = result.truncated,
                readOnlyVault = result.readOnly || !state.writeBackEnabled,
                message = when {
                    result.questions.isEmpty() -> "No question notes found in that folder."
                    result.truncated -> "Stopped after ${VaultRepository.MAX_FILES} files."
                    else -> null
                },
            )
        }
    }

    fun useSampleVault() {
        prefs.edit().remove(KEY_VAULT_URI).apply()
        loadSamples()
    }

    fun rescan() {
        val uri = state.vaultUri
        if (uri != null) openVault(Uri.parse(uri)) else loadSamples()
    }

    fun setWriteBack(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WRITE_BACK, enabled).apply()
        state = state.copy(writeBackEnabled = enabled, readOnlyVault = !enabled || state.sampleMode)
        // Refresh the write capability flag of the current vault.
        if (!state.sampleMode && state.vaultUri != null) {
            state = state.copy(readOnlyVault = !enabled)
        }
    }

    // ---- navigation -----------------------------------------------------

    fun goTo(screen: Screen) {
        state = state.copy(screen = screen)
    }

    fun setSearch(query: String) {
        state = state.copy(search = query)
    }

    fun setFilter(filter: QuestionFilter) {
        state = state.copy(filter = filter)
    }

    // ---- practice -------------------------------------------------------

    fun startPractice(keys: List<String>) {
        if (keys.isEmpty()) {
            state = state.copy(message = "Nothing to practise yet.")
            return
        }
        state = state.copy(
            screen = Screen.PRACTICE,
            practiceKeys = keys,
            practiceIndex = 0,
            selected = emptySet(),
            confidence = 2,
            showHint = false,
            outcome = null,
            session = emptyList(),
            summary = null,
            elapsedSeconds = 0,
            startedAtMillis = System.currentTimeMillis(),
            message = null,
        )
        lastTickSecond = 0
    }

    fun practiseOne(key: String) = startPractice(listOf(key))

    fun startTodayQueue() {
        val keys = Stats.todayQueue(state.questions, LocalDate.now()).map { it.key }
        startPractice(keys)
    }

    fun startAllQuestions() = startPractice(state.questions.map { it.key })

    fun startMissed() {
        val keys = state.questions.filter { Stats.isMissed(it) }.map { it.key }
        if (keys.isEmpty()) {
            state = state.copy(message = "No missed questions yet.")
        } else {
            startPractice(keys)
        }
    }

    fun toggleChoice(letter: String) {
        if (state.outcome != null) return
        val question = state.currentQuestion ?: return
        state = when (question.mode) {
            QuestionMode.SELECT_ALL -> {
                val next = state.selected.toMutableSet()
                if (!next.add(letter)) next.remove(letter)
                state.copy(selected = next)
            }
            else -> state.copy(selected = setOf(letter))
        }
    }

    fun setConfidence(value: Int) {
        if (state.outcome != null) return
        state = state.copy(confidence = value.coerceIn(1, 3))
    }

    fun toggleHint() {
        state = state.copy(showHint = !state.showHint)
    }

    fun tick() {
        if (state.screen != Screen.PRACTICE || state.startedAtMillis == 0L) return
        val elapsed = ((System.currentTimeMillis() - state.startedAtMillis) / 1000L).toInt()
        if (elapsed != lastTickSecond) {
            lastTickSecond = elapsed
            state = state.copy(elapsedSeconds = elapsed)
        }
    }

    fun canSubmit(): Boolean = when (state.currentQuestion?.mode) {
        QuestionMode.SELF_GRADED -> true
        null -> false
        else -> state.selected.isNotEmpty()
    }

    fun submit(selfGrade: Boolean? = null) {
        val question = state.currentQuestion ?: return
        if (state.outcome != null) return
        if (!canSubmit()) return

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val seconds = maxOf(1, state.elapsedSeconds)
        val grade = AnswerGrader.grade(question, state.selected, selfGrade)
        val outcome = Scheduler.apply(
            previous = question.progress,
            correct = grade.correct,
            confidence = state.confidence,
            seconds = seconds,
            chosen = grade.chosen,
            today = today,
            now = now,
        )

        viewModelScope.launch {
            val (newRaw, storage) = writeBackIfPossible(question, outcome, now)
            val updated = question.withRawText(newRaw, outcome.progress)
            val newQuestions = state.questions.map { if (it.key == updated.key) updated else it }
            store.save(updated.key, outcome.progress)

            val record = AnswerRecord(
                key = updated.key,
                title = updated.displayTitle,
                correct = grade.correct,
                chosen = grade.chosen,
                seconds = seconds,
            )

            state = state.copy(
                questions = newQuestions,
                outcome = Submission(
                    correct = grade.correct,
                    chosen = grade.chosen,
                    intervalDays = outcome.intervalDays,
                    nextReview = outcome.nextReview,
                    storage = storage,
                    attemptId = outcome.attemptId,
                    correctAnswer = AnswerGrader.correctAnswerLabel(updated),
                    explanation = updated.explanation,
                    errorType = outcome.progress.lastErrorType,
                ),
                session = state.session + record,
            )
        }
    }

    private suspend fun writeBackIfPossible(
        question: Question,
        outcome: Scheduler.Outcome,
        now: LocalDateTime,
    ): Pair<String, StorageStatus> = withContext(Dispatchers.IO) {
        val uriString = question.fileUri
        val eligible = state.writeBackEnabled &&
            !question.isSample &&
            !question.schedulerManaged &&
            !uriString.isNullOrBlank()

        if (!eligible) return@withContext question.rawText to StorageStatus.APP_ONLY

        val uri = Uri.parse(uriString)
        val current = repo.readText(uri) ?: return@withContext question.rawText to StorageStatus.APP_ONLY
        if (repo.signature(current) != repo.signature(question.rawText)) {
            return@withContext question.rawText to StorageStatus.NOTE_CHANGED
        }
        val newText = NoteWriter.applyAttempt(question.rawText, NoteWriter.attemptFrom(outcome, now))
        if (repo.writeText(uri, newText)) {
            newText to StorageStatus.SAVED_TO_NOTE
        } else {
            question.rawText to StorageStatus.APP_ONLY
        }
    }

    fun tagError(tag: String) {
        val question = state.currentQuestion ?: return
        val outcome = state.outcome ?: return
        val tagged = Scheduler.tagError(question.progress, outcome.attemptId, tag)
        val updatedQuestion = question.withProgress(tagged)
        val newQuestions = state.questions.map { if (it.key == updatedQuestion.key) updatedQuestion else it }
        store.save(updatedQuestion.key, tagged)

        state = state.copy(
            questions = newQuestions,
            outcome = outcome.copy(errorType = tag),
        )

        if (!question.isSample && state.writeBackEnabled && !question.schedulerManaged) {
            viewModelScope.launch {
                val uriString = updatedQuestion.fileUri ?: return@launch
                val taggedRaw = NoteWriter.applyErrorTag(updatedQuestion.rawText, outcome.attemptId, tag)
                repo.writeText(Uri.parse(uriString), taggedRaw)
                val refreshed = updatedQuestion.withRawText(taggedRaw, tagged)
                state = state.copy(
                    questions = state.questions.map { if (it.key == refreshed.key) refreshed else it },
                )
            }
        }
    }

    fun nextQuestion() {
        val nextIndex = state.practiceIndex + 1
        if (nextIndex >= state.practiceKeys.size) {
            finishSession()
            return
        }
        state = state.copy(
            practiceIndex = nextIndex,
            selected = emptySet(),
            confidence = 2,
            showHint = false,
            outcome = null,
            elapsedSeconds = 0,
            startedAtMillis = System.currentTimeMillis(),
        )
        lastTickSecond = 0
    }

    fun finishSession() {
        val session = state.session
        state = state.copy(
            screen = Screen.SUMMARY,
            summary = Summary(
                answered = session.size,
                correct = session.count { it.correct },
                totalSeconds = session.sumOf { it.seconds },
                missed = session.filter { !it.correct },
            ),
            outcome = null,
        )
    }

    fun closePractice() {
        state = state.copy(
            screen = Screen.TODAY,
            practiceKeys = emptyList(),
            practiceIndex = 0,
            selected = emptySet(),
            outcome = null,
            session = emptyList(),
        )
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    companion object {
        private const val KEY_WRITE_BACK = "write_back_enabled"
        private const val KEY_VAULT_URI = "vault_uri"
    }
}
