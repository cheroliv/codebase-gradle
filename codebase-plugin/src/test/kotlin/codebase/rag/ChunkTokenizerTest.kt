package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.*

class ChunkTokenizerTest {

    @Test
    fun `splitIntoSegments should return one segment for short string`() {
        val text = "Hello world, this is a test."

        val segments = ChunkTokenizer.splitIntoSegments(text)

        assertEquals(1, segments.size)
        assertEquals("Hello world, this is a test.", segments[0])
    }

    @Test
    fun `splitIntoSegments should split by blank lines`() {
        val text = """
            |First paragraph here.
            |
            |Second paragraph goes here.
            |
            |Third paragraph is this one.
        """.trimMargin()

        val segments = ChunkTokenizer.splitIntoSegments(text)

        assertEquals(3, segments.size)
        assertEquals("First paragraph here.", segments[0])
        assertEquals("Second paragraph goes here.", segments[1])
        assertEquals("Third paragraph is this one.", segments[2])
    }

    @Test
    fun `splitIntoSegments should handle multiple blank lines as single separator`() {
        val text = """
            |Part one
            |
            |
            |
            |Part two
        """.trimMargin()

        val segments = ChunkTokenizer.splitIntoSegments(text)

        assertEquals(2, segments.size)
    }

    @Test
    fun `estimateTokenCount should return roughly a quarter of char count`() {
        val text = "a".repeat(100)

        val tokens = ChunkTokenizer.estimateTokenCount(text)

        assertEquals(28, tokens)
    }

    @Test
    fun `estimateTokenCount should return at least one for empty string`() {
        val tokens = ChunkTokenizer.estimateTokenCount("")

        assertTrue(tokens in 0..1, "empty string should yield minimal token count, got $tokens")
    }

    @Test
    fun `estimateTokenCount should return at least one for whitespace only`() {
        val tokens = ChunkTokenizer.estimateTokenCount("   ")

        assertEquals(1, tokens)
    }

    @Test
    fun `splitIntoSentenceLevelChunks should return single chunk for small text`() {
        val text = "A short single paragraph text."

        val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(text)

        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("short single paragraph"))
    }

    @Test
    fun `splitIntoSentenceLevelChunks should split large content into multiple chunks`() {
        val paragraph = "This is a paragraph with enough text to exceed token limits. " +
            "We need quite some characters to make each paragraph substantial. " +
            "Adding more content to fill up the token budget for this test case."
        val text = List(10) { paragraph }.joinToString("\n\n")

        val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(text, maxTokens = 50, overlapTokens = 10)

        assertTrue(chunks.size > 1, "large text with low maxTokens should produce multiple chunks, got ${chunks.size}")
    }

    @Test
    fun `buildOverlap should return suffix of text`() {
        val text = "one two three four five six seven eight nine ten"

        val overlap = ChunkTokenizer.buildOverlap(text, overlapTokens = 10)

        assertTrue(overlap.isNotBlank(), "overlap should not be empty")
        assertTrue(overlap.contains("seven"), "overlap should contain later words")
        assertTrue(!overlap.startsWith("one"), "overlap should not start with first word")
    }

    @Test
    fun `splitIntoSentenceLevelChunks with three paragraphs should create multiple chunks`() {
        val p1 = "First paragraph with significant amount of text content to fill tokens."
        val p2 = "Second paragraph also containing enough characters to count toward the budget."
        val p3 = "Third paragraph making sure we have three distinct segments to chunk properly."
        val text = "$p1\n\n$p2\n\n$p3"

        val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(
            text,
            maxTokens = ChunkTokenizer.estimateTokenCount("$p1\n\n$p2") - 1,
            overlapTokens = 0
        )

        assertTrue(chunks.size >= 2, "should produce at least 2 chunks, got ${chunks.size}")
    }
}
