package com.mcqtracker.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SampleVaultTest {

    private fun questionsDir(): File {
        val candidates = listOf(
            File("src/main/assets/sample-vault/Questions"),
            File("app/src/main/assets/sample-vault/Questions"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("sample vault not found from ${File(".").absolutePath}")
    }

    @Test
    fun `the sample vault holds four parseable questions`() {
        val files = questionsDir().listFiles { file -> file.extension == "qnote" }.orEmpty()
        assertEquals(4, files.size)

        val questions = files.map { file ->
            requireNotNull(NoteParser.parse(file.readText(), Question.SAMPLE_SOURCE)) {
                "could not parse ${file.name}"
            }
        }.sortedBy { it.questionId }

        assertEquals(
            listOf("SAMPLE-001", "SAMPLE-002", "SAMPLE-003", "SAMPLE-004"),
            questions.map { it.questionId },
        )
        assertEquals(
            listOf(QuestionMode.MCQ, QuestionMode.MCQ, QuestionMode.TRUE_FALSE, QuestionMode.MCQ),
            questions.map { it.mode },
        )
        assertEquals("C", questions[0].answerKey)
        assertEquals("True", questions[2].answerKey)
        questions.forEach { question ->
            assertTrue("${question.questionId} has no prompt", question.prompt.isNotBlank())
            assertTrue("${question.questionId} has too few choices", question.choices.size >= 2)
        }
    }

    @Test
    fun `sample files avoid the markdown question extension`() {
        val mdFiles = questionsDir().listFiles { file -> file.extension == "md" }.orEmpty()
        assertEquals(0, mdFiles.size)
    }
}
