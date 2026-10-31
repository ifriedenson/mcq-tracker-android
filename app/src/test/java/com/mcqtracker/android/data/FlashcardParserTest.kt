package com.mcqtracker.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardParserTest {

    @Test
    fun `parses a basic single line card`() {
        val cards = FlashcardParser.parse("Capital of France::Paris", "src")
        assertEquals(1, cards.size)
        assertEquals(FlashcardKind.BASIC, cards[0].kind)
        assertEquals("Capital of France", cards[0].front)
        assertEquals("Paris", cards[0].back)
    }

    @Test
    fun `reversed cards are produced in both directions`() {
        val cards = FlashcardParser.parse("Photosynthesis:::Converts light to sugar", "src")
        assertEquals(2, cards.size)
        assertEquals("Photosynthesis", cards[0].front)
        assertEquals("Converts light to sugar", cards[1].front)
        assertTrue(cards.all { it.kind == FlashcardKind.REVERSED })
        assertEquals(2, cards.map { it.cardId }.distinct().size)
    }

    @Test
    fun `multi line cards use a lone question mark separator`() {
        val text = """
            What is 2 + 2?
            ?
            4
        """.trimIndent()
        val cards = FlashcardParser.parse(text, "src")
        assertEquals(1, cards.size)
        assertEquals(FlashcardKind.MULTI_LINE, cards[0].kind)
        assertEquals("What is 2 + 2?", cards[0].front)
        assertEquals("4", cards[0].back)
    }

    @Test
    fun `every highlight becomes its own cloze card`() {
        val cards = FlashcardParser.parse("The ==mitochondria== is the ==powerhouse==.", "src")
        assertEquals(2, cards.size)
        assertTrue(cards.all { it.kind == FlashcardKind.CLOZE })
        // only the card's own deletion is hidden; other highlights read as plain text
        assertEquals("The [...] is the powerhouse.", cards[0].front)
        assertEquals("The mitochondria is the [...].", cards[1].front)
        assertEquals("mitochondria", cards[0].back)
        assertEquals("powerhouse", cards[1].back)
    }

    @Test
    fun `question notes are never parsed as flashcards`() {
        val note = """
            ---
            question_id: SAMPLE-001
            answer: C
            ---

            ## Question

            Example::with double colon
        """.trimIndent()
        assertTrue(FlashcardParser.parse(note, "src").isEmpty())
    }

    @Test
    fun `plain prose produces no cards`() {
        assertTrue(FlashcardParser.parse("Just a paragraph of ordinary text.", "src").isEmpty())
        assertTrue(FlashcardParser.parse("", "src").isEmpty())
    }

    @Test
    fun `card ids are stable across parses`() {
        val text = "Capital of France::Paris"
        val first = FlashcardParser.parse(text, "src").map { it.key }
        val second = FlashcardParser.parse(text, "src").map { it.key }
        assertEquals(first, second)
    }
}
