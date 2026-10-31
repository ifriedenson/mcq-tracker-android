package com.mcqtracker.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteParserTest {

    private val documentedSample = """
        ---
        question_id: SAMPLE-001
        course: Sample Course
        skill: Reading
        difficulty: easy
        choices: 4
        answer: C
        target_seconds: 60
        ---

        ## Question

        In the sentence "The experiment was inconclusive, so the team repeated it," what does "inconclusive" most nearly mean?

        ## Answer Choices

        - A. Dangerous
        - B. Expensive
        - C. Not decisive
        - D. Carefully planned

        ## Answer

        > [!answer]- Reveal answer
        > **Correct Answer: C**
        >
        > Inconclusive means the results did not settle the question.
    """.trimIndent()

    @Test
    fun `parses the documented sample note`() {
        val question = requireNotNull(NoteParser.parse(documentedSample, Question.SAMPLE_SOURCE))
        assertEquals("SAMPLE-001", question.questionId)
        assertEquals(QuestionMode.MCQ, question.mode)
        assertEquals("C", question.answerKey)
        assertEquals(4, question.choices.size)
        assertEquals("Dangerous", question.choices[0].text)
        assertEquals(60, question.targetSeconds)
        assertEquals("Reading", question.skill)
        assertTrue(question.prompt.contains("inconclusive"))
        assertTrue(question.explanation.contains("Inconclusive means"))
        assertFalse(question.explanation.contains("[!answer]"))
    }

    @Test
    fun `supports section aliases and bold list choices`() {
        val note = """
            ---
            question_id: ALIAS-1
            answer: B
            ---

            ## Stem

            Which number is prime?

            ## Options

            - **A)** 4
            - **B)** 7
            - **C)** 9
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertEquals("Which number is prime?", question.prompt)
        assertEquals(3, question.choices.size)
        assertEquals("7", question.choices[1].text)
        assertEquals("B", question.answerKey)
        assertEquals(QuestionMode.MCQ, question.mode)
    }

    @Test
    fun `supports wrapped continuation lines`() {
        val note = """
            ---
            question_id: WRAP-1
            answer: A
            ---

            ## Question

            Pick one.

            ## Choices

            - A. This choice
            wraps onto a second line
            - B. Short
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertEquals("This choice wraps onto a second line", question.choices[0].text)
    }

    @Test
    fun `answer key can live only in the answer section`() {
        val note = """
            ---
            question_id: SEC-1
            ---

            ## Question

            Pick B.

            ## Answer Choices

            - A. One
            - B. Two

            ## Answer

            > **Correct Answer: B**
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertEquals("B", question.answerKey)
        assertEquals(QuestionMode.MCQ, question.mode)
    }

    @Test
    fun `recognizes select-all keys in several notations`() {
        for (key in listOf("A, C", "A/C", "A and C", "AC")) {
            val note = """
                ---
                question_id: SET-1
                answer: $key
                ---

                ## Question

                Choose all that apply.

                ## Answer Choices

                - A. One
                - B. Two
                - C. Three
                - D. Four
            """.trimIndent()
            val question = requireNotNull(NoteParser.parse(note, "src"))
            assertEquals("key=$key", QuestionMode.SELECT_ALL, question.mode)
            assertEquals("key=$key", "A, C", question.answerKey)
        }
    }

    @Test
    fun `true or false as a word without choices`() {
        val note = """
            ---
            question_id: TF-1
            answer: False
            ---

            ## Question

            The sky is green.
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertEquals(QuestionMode.TRUE_FALSE, question.mode)
        assertEquals("False", question.answerKey)
    }

    @Test
    fun `true or false letter maps through the note's own choices`() {
        val note = """
            ---
            question_id: TF-2
            answer: B
            ---

            ## Question

            Water boils at room temperature.

            ## Answer Choices

            - A. True
            - B. False
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertEquals(QuestionMode.TRUE_FALSE, question.mode)
        assertEquals("False", question.answerKey)
    }

    @Test
    fun `spr notes are self graded and keep their raw key`() {
        val note = """
            ---
            question_id: SPR-1
            question_type: student-produced response
            answer: 0.5
            ---

            ## Question

            What is one half?
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertEquals(QuestionMode.SELF_GRADED, question.mode)
        assertEquals("0.5", question.answerKey)
    }

    @Test
    fun `mcq with fewer than two choices downgrades to self graded`() {
        val note = """
            ---
            question_id: ONE-1
            answer: A
            ---

            ## Question

            Only one option here.

            ## Answer Choices

            - A. Alone
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertEquals(QuestionMode.SELF_GRADED, question.mode)
    }

    @Test
    fun `parses progress properties and quoted history rows`() {
        val note = """
            ---
            question_id: PROG-1
            answer: A
            attempts: 2
            correct_count: 1
            streak: 0
            next_review: 2026-11-02
            result: incorrect
            last_confidence: 2
            last_error_type: careless
            time_spent_total: 90
            last_time_spent: 40
            history:
              - "2026-10-30T10:00:00|correct|50|A|2||attempt-1"
              - "2026-10-31T10:00:00|incorrect|40|B|2|careless|attempt-2"
            ---

            ## Question

            Example

            ## Answer Choices

            - A. One
            - B. Two
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertEquals(2, question.progress.attempts)
        assertEquals(1, question.progress.correctCount)
        assertEquals(90, question.progress.timeSpentTotal)
        assertEquals("careless", question.progress.lastErrorType)
        assertEquals(2, question.progress.history.size)
        assertEquals("attempt-2", question.progress.history[1].attemptId)
        assertEquals(java.time.LocalDate.of(2026, 11, 2), question.progress.nextReview)
    }

    @Test
    fun `borrows a stimulus from a sibling note`() {
        val donor = """
            ---
            question_id: A-1
            stimulus_id: PASSAGE-9
            answer: A
            ---

            ## Stimulus

            A short passage.

            ## Question

            First question.

            ## Answer Choices

            - A. Yes
            - B. No
        """.trimIndent()
        val borrower = """
            ---
            question_id: A-2
            stimulus_id: PASSAGE-9
            answer: B
            ---

            ## Question

            Second question.

            ## Answer Choices

            - A. Yes
            - B. No
        """.trimIndent()
        val parsed = listOf(
            requireNotNull(NoteParser.parse(donor, "src")),
            requireNotNull(NoteParser.parse(borrower, "src")),
        )
        val result = NoteParser.borrowStimuli(parsed)
        assertEquals("A short passage.", result[1].stimulus)
    }

    @Test
    fun `malformed or non question notes return null`() {
        assertNull(NoteParser.parse("no frontmatter here", "src"))
        assertNull(NoteParser.parse("---\ntitle: nope\n---\nbody", "src"))
        assertNull(NoteParser.parse("---\nquestion_id:   \n---\nbody", "src"))
    }

    @Test
    fun `markdown cleanup removes callouts links images and bold`() {
        val markdown = """
            ## Heading

            > [!note] Extra
            > Quoted line with **bold** and a [link](https://example.com).

            ![diagram](image.png)
        """.trimIndent()
        val plain = MarkdownLite.toPlainText(markdown)
        assertFalse(plain.contains("[!note]"))
        assertFalse(plain.contains("**"))
        assertFalse(plain.contains("https://example.com"))
        assertTrue(plain.contains("Quoted line with bold and a link."))
        assertTrue(plain.contains("diagram"))
    }

    @Test
    fun `fsrs notes are marked scheduler managed`() {
        val note = """
            ---
            question_id: FSRS-1
            scheduler: fsrs
            answer: A
            ---

            ## Question

            Example

            ## Answer Choices

            - A. One
            - B. Two
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertTrue(question.schedulerManaged)
    }

    @Test
    fun `excluded and suspended notes are never queued`() {
        val note = """
            ---
            question_id: EX-1
            study_status: suspended
            answer: A
            ---

            ## Question

            Example

            ## Answer Choices

            - A. One
            - B. Two
        """.trimIndent()
        val question = requireNotNull(NoteParser.parse(note, "src"))
        assertFalse(com.mcqtracker.android.domain.Stats.isQueueable(question, java.time.LocalDate.now()))
    }
}
