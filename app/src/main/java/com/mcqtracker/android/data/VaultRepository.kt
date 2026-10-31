package com.mcqtracker.android.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads question notes either from the bundled sample vault or from a user-picked
 * SAF folder tree.
 *
 * Scanning is bounded (4,000 files, 2 MB per note) so a huge vault cannot hang the
 * app, and dot-directories are skipped.
 */
class VaultRepository(private val context: Context) {

    data class ScanResult(
        val questions: List<Question>,
        val truncated: Boolean,
        val filesScanned: Int,
        val readOnly: Boolean,
        val cards: List<Flashcard> = emptyList(),
    )

    suspend fun loadSamples(store: ProgressStore): List<Question> = withContext(Dispatchers.IO) {
        val local = store.loadAll()
        val names = context.assets.list("sample-vault/Questions").orEmpty().filter { it.endsWith(".qnote") }
        val parsed = names.mapNotNull { name ->
            val text = runCatching {
                context.assets.open("sample-vault/Questions/$name").bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            NoteParser.parse(text, Question.SAMPLE_SOURCE)
        }
        NoteParser.borrowStimuli(parsed).map { question ->
            question.withProgress(ProgressMerge.merge(question.progress, local[question.key]))
        }.sortedBy { it.questionId }
    }

    suspend fun loadSampleCards(store: ProgressStore): List<Flashcard> = withContext(Dispatchers.IO) {
        val local = store.loadAll()
        val names = context.assets.list("sample-vault/Flashcards").orEmpty().filter { it.endsWith(".qcard") }
        names.flatMap { name ->
            val text = runCatching {
                context.assets.open("sample-vault/Flashcards/$name").bufferedReader().use { it.readText() }
            }.getOrNull()
            if (text == null) emptyList() else FlashcardParser.parse(text, Question.SAMPLE_SOURCE)
        }.map { card ->
            card.withProgress(ProgressMerge.merge(card.progress, local[card.key]))
        }
    }

    suspend fun scanTree(
        treeUri: Uri,
        store: ProgressStore,
        writeEnabled: Boolean,
    ): ScanResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext ScanResult(emptyList(), false, 0, true)
        val local = store.loadAll()
        val parsed = mutableListOf<Question>()
        val foundCards = mutableListOf<Flashcard>()
        var files = 0
        var truncated = false
        val readOnly = !root.canWrite() || !writeEnabled

        suspend fun walk(dir: DocumentFile, depth: Int) {
            if (depth > MAX_DEPTH || truncated) return
            for (child in dir.listFiles()) {
                if (truncated) return
                val name = child.name.orEmpty()
                if (child.isDirectory) {
                    if (name.startsWith(".")) continue
                    walk(child, depth + 1)
                } else {
                    if (name.startsWith(".") || !name.endsWith(".md", ignoreCase = true)) continue
                    files++
                    if (files > MAX_FILES) {
                        truncated = true
                        return
                    }
                    if (child.length() > MAX_BYTES) continue
                    val text = readText(child) ?: continue
                    val question = NoteParser.parse(text, treeUri.toString(), child.uri.toString())
                    if (question != null) {
                        parsed += question
                    } else {
                        foundCards += FlashcardParser.parse(text, treeUri.toString(), child.uri.toString())
                    }
                }
            }
        }

        walk(root, 0)
        val merged = NoteParser.borrowStimuli(parsed).map { question ->
            question.withProgress(ProgressMerge.merge(question.progress, local[question.key]))
        }
        val mergedCards = foundCards.map { card ->
            card.withProgress(ProgressMerge.merge(card.progress, local[card.key]))
        }
        ScanResult(merged.sortedBy { it.questionId }, truncated, files, readOnly, mergedCards)
    }

    suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        val document = DocumentFile.fromSingleUri(context, uri) ?: return@withContext null
        readText(document)
    }

    suspend fun readText(document: DocumentFile): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(document.uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > MAX_BYTES) null else String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    suspend fun writeText(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val document = DocumentFile.fromSingleUri(context, uri) ?: return@withContext false
            if (!document.canWrite()) return@withContext false
            context.contentResolver.openOutputStream(document.uri, "wt")?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
            } != null
        }.getOrDefault(false)
    }

    /** Cheap content signature used to detect external edits before writing. */
    fun signature(text: String): Long {
        var hash = 1125899906842597L
        for (char in text) hash = 31 * hash + char.code
        return hash
    }

    companion object {
        const val MAX_FILES = 4_000
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_DEPTH = 32
    }
}
