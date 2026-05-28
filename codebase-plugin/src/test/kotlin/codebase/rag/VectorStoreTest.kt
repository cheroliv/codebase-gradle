package codebase.rag

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VectorStoreTest {

    private val container = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg17").apply {
        withDatabaseName("codebase_rag_vector_store_test")
        withUsername("codebase")
        withPassword("codebase")
        withStartupTimeout(java.time.Duration.ofMinutes(2))
        withReuse(false)
    }

    private lateinit var store: VectorStore

    @BeforeAll
    fun setUp() {
        container.start()
        store = VectorStore(container.jdbcUrl, container.username, container.password)
        store.initSchema()
    }

    @BeforeEach
    fun cleanDatabase() {
        // Re-init schema between tests for isolation
        store.initSchema()
    }

    @AfterAll
    fun tearDown() {
        container.stop()
    }

    // ── countDocuments / countChunks ──────────────────────────────────────────

    @Test
    fun `countDocuments returns 0 when empty`() {
        assertEquals(0, store.countDocuments())
    }

    @Test
    fun `countDocuments returns 1 after insert`() {
        store.insertDocument("test.txt", "/test/test.txt", 100, listOf("chunk1", "chunk2"))
        assertEquals(1, store.countDocuments())
    }

    @Test
    fun `countDocuments returns 3 after multiple inserts`() {
        store.insertDocument("a.txt", "/a.txt", 10, listOf("a1"))
        store.insertDocument("b.txt", "/b.txt", 20, listOf("b1", "b2"))
        store.insertDocument("c.txt", "/c.txt", 30, listOf("c1", "c2", "c3"))
        assertEquals(3, store.countDocuments())
    }

    @Test
    fun `countChunks returns 0 when empty`() {
        assertEquals(0, store.countChunks())
    }

    @Test
    fun `countChunks returns 3 after insert with 3 chunks`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("a", "b", "c"))
        assertEquals(3, store.countChunks())
    }

    @Test
    fun `countChunks aggregates across multiple documents`() {
        store.insertDocument("a.txt", "/a.txt", 10, listOf("a1", "a2"))
        store.insertDocument("b.txt", "/b.txt", 20, listOf("b1", "b2", "b3"))
        assertEquals(5, store.countChunks())
    }

    // ── hasOrphanChunks ───────────────────────────────────────────────────────

    @Test
    fun `hasOrphanChunks returns false when no orphans`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1"))
        assertFalse(store.hasOrphanChunks())
    }

    @Test
    fun `hasOrphanChunks returns false after multiple documents`() {
        store.insertDocument("a.txt", "/a.txt", 10, listOf("a1"))
        store.insertDocument("b.txt", "/b.txt", 20, listOf("b1"))
        assertFalse(store.hasOrphanChunks())
    }

    @Test
    fun `hasOrphanChunks returns false when empty database`() {
        assertFalse(store.hasOrphanChunks())
    }

    // ── allEmbeddingsNonNull / allEmbeddingsDimension ──────────────────────────

    @Test
    fun `allEmbeddingsNonNull returns false when no embeddings set`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1"))
        assertFalse(store.allEmbeddingsNonNull())
    }

    // Helper: Generates a 384-dimension vector string filled with `value`
    private fun vec384(value: Double = 0.0): String {
        return "[" + (0 until 384).joinToString(", ") { "$value" } + "]"
    }

    // Helper: 384-dim vector with custom value on first position, 0 elsewhere
    private fun embeddingWithFirstDim(first: Double): String {
        return "[$first" + (0 until 383).joinToString("") { ", 0.0" } + "]"
    }

    @Test
    fun `allEmbeddingsNonNull returns true after embedding set`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1"))
        val chunks = store.fetchAllChunks()
        store.updateEmbedding(chunks[0].first, vec384(0.1))
        assertTrue(store.allEmbeddingsNonNull())
    }

    @Test
    fun `allEmbeddingsNonNull returns false when some embeddings missing`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1", "chunk2"))
        val chunks = store.fetchAllChunks()
        store.updateEmbedding(chunks[0].first, vec384(0.1))
        // chunks[1] has no embedding
        assertFalse(store.allEmbeddingsNonNull())
    }

    @Test
    fun `allEmbeddingsDimension returns true when no embeddings (no violation)`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1"))
        // No embeddings at all → nothing violates the dimension constraint → true
        assertTrue(store.allEmbeddingsDimension(384))
    }

    @Test
    fun `allEmbeddingsDimension returns true when all embeddings match expected dim`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1"))
        val chunks = store.fetchAllChunks()
        store.updateEmbedding(chunks[0].first, vec384(0.0))
        assertTrue(store.allEmbeddingsDimension(384))
    }

    @Test
    fun `allEmbeddingsDimension returns false when checking wrong expected dimension`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1"))
        val chunks = store.fetchAllChunks()
        store.updateEmbedding(chunks[0].first, vec384(0.0))
        // All embeddings are 384-dim, but we check for 385 → should be false
        assertFalse(store.allEmbeddingsDimension(385))
    }

    // ── findByPackage ─────────────────────────────────────────────────────────

    @Test
    fun `findByPackage returns empty list for non-matching package`() {
        store.insertDocument("a.kt", "/a.kt", 100, listOf("code"), packageName = "com.example")
        assertEquals(0, store.findByPackage("com.other").size)
    }

    @Test
    fun `findByPackage returns matching documents`() {
        store.insertDocument("a.kt", "/a.kt", 100, listOf("code"), packageName = "com.example")
        store.insertDocument("b.kt", "/b.kt", 200, listOf("more code"), packageName = "com.example")
        store.insertDocument("c.kt", "/c.kt", 300, listOf("other"), packageName = "com.other")
        val results = store.findByPackage("com.example")
        assertEquals(2, results.size)
        assertEquals("a.kt", results[0].fileName)
        assertEquals("b.kt", results[1].fileName)
    }

    @Test
    fun `findByPackage returns DocRecord with all fields`() {
        store.insertDocument(
            "Main.kt", "/src/Main.kt", 500, listOf("fun main"),
            packageName = "com.example", className = "Main", repoName = "my-repo"
        )
        val results = store.findByPackage("com.example")
        assertEquals(1, results.size)
        val doc = results[0]
        assertEquals("Main.kt", doc.fileName)
        assertEquals("/src/Main.kt", doc.filePath)
        assertEquals(500, doc.fileSize)
        assertEquals("com.example", doc.packageName)
        assertEquals("Main", doc.className)
        assertEquals("my-repo", doc.repoName)
    }

    // ── insertDocument ────────────────────────────────────────────────────────

    @Test
    fun `insertDocument returns valid id`() {
        val id = store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1"))
        assertTrue(id > 0)
    }

    @Test
    fun `insertDocument with null optional fields`() {
        val id = store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk"))
        assertTrue(id > 0)
        assertEquals(1, store.countDocuments())
    }

    @Test
    fun `insertDocument with all metadata`() {
        val id = store.insertDocument(
            "Config.kt", "/src/Config.kt", 200, listOf("data class Config"),
            packageName = "com.example", className = "Config", repoName = "my-repo"
        )
        assertTrue(id > 0)
    }

    @Test
    fun `insertDocument with empty chunks list`() {
        val id = store.insertDocument("empty.txt", "/empty.txt", 0, emptyList())
        assertTrue(id > 0)
        assertEquals(0, store.countChunks())
    }

    // ── fetchAllChunks ────────────────────────────────────────────────────────

    @Test
    fun `fetchAllChunks returns empty when no documents`() {
        assertEquals(0, store.fetchAllChunks().size)
    }

    @Test
    fun `fetchAllChunks returns all chunks ordered by id`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("first", "second", "third"))
        val chunks = store.fetchAllChunks()
        assertEquals(3, chunks.size)
        assertEquals("first", chunks[0].second)
        assertEquals("second", chunks[1].second)
        assertEquals("third", chunks[2].second)
    }

    @Test
    fun `fetchAllChunks after updateEmbedding still returns chunks`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("hello"))
        val chunks = store.fetchAllChunks()
        store.updateEmbedding(chunks[0].first, vec384(1.0))
        val afterUpdate = store.fetchAllChunks()
        assertEquals(1, afterUpdate.size)
        assertEquals("hello", afterUpdate[0].second)
    }

    // ── initSchema idempotent ─────────────────────────────────────────────────

    @Test
    fun `initSchema is idempotent — can be called twice`() {
        store.insertDocument("a.txt", "/a.txt", 10, listOf("a"))
        assertEquals(1, store.countDocuments())
        store.initSchema() // re-init drops tables
        assertEquals(0, store.countDocuments())
        store.initSchema() // second call fine
        assertEquals(0, store.countDocuments())
    }

    // ── querySimilar ──────────────────────────────────────────────────────────

    @Test
    fun `querySimilar returns results ordered by similarity`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("A", "B", "C"))
        val chunks = store.fetchAllChunks()
        // Embeddings: A=0.1, B=0.9, C=0.5 on first dimension, rest zero
        // Query = B → B should be closest
        store.updateEmbedding(chunks[0].first, embeddingWithFirstDim(0.1))
        store.updateEmbedding(chunks[1].first, embeddingWithFirstDim(0.9))
        store.updateEmbedding(chunks[2].first, embeddingWithFirstDim(0.5))
        val results = store.querySimilar(embeddingWithFirstDim(0.9), topK = 3)
        assertEquals(3, results.size)
        assertTrue(results[0].text.contains("B"), "B should be closest to query, got: ${results[0].text}")
    }

    @Test
    fun `querySimilar respects topK`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("A", "B", "C", "D", "E"))
        val chunks = store.fetchAllChunks()
        for ((i, chunk) in chunks.withIndex()) {
            val weight = 0.1 + i * 0.1 // 0.1 .. 0.5
            store.updateEmbedding(chunk.first, embeddingWithFirstDim(weight))
        }
        val results = store.querySimilar(embeddingWithFirstDim(0.1), topK = 2)
        assertEquals(2, results.size)
    }

    @Test
    fun `querySimilar with fileExtension filter`() {
        store.insertDocument("test.kt", "/test.kt", 100, listOf("kt content"))
        store.insertDocument("test.java", "/test.java", 100, listOf("java content"))
        val chunks = store.fetchAllChunks()
        for (chunk in chunks) {
            store.updateEmbedding(chunk.first, vec384(1.0))
        }
        val resultsKt = store.querySimilar(vec384(1.0), topK = 10, fileExtension = "kt")
        assertEquals(1, resultsKt.size)
        assertTrue(resultsKt[0].text.contains("test.kt"))

        val resultsJava = store.querySimilar(vec384(1.0), topK = 10, fileExtension = "java")
        assertEquals(1, resultsJava.size)
        assertTrue(resultsJava[0].text.contains("test.java"))
    }

    @Test
    fun `querySimilar similarity is between 0 and 1`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("content"))
        val chunks = store.fetchAllChunks()
        store.updateEmbedding(chunks[0].first, vec384(1.0))
        val results = store.querySimilar(vec384(1.0), topK = 1)
        assertEquals(1, results.size)
        assertNotNull(results[0].similarity)
        assertTrue(results[0].similarity in 0.0..1.0, "Similarity should be in [0,1], got ${results[0].similarity}")
    }

    @Test
    fun `querySimilar returns empty when no embeddings`() {
        store.insertDocument("test.txt", "/test.txt", 100, listOf("chunk1"))
        val results = store.querySimilar(vec384(), topK = 5)
        assertEquals(0, results.size)
    }

    @Test
    fun `querySimilar with no matching fileExtension returns empty`() {
        store.insertDocument("test.kt", "/test.kt", 100, listOf("kt content"))
        val chunks = store.fetchAllChunks()
        for (chunk in chunks) {
            store.updateEmbedding(chunk.first, vec384(1.0))
        }
        val results = store.querySimilar(vec384(1.0), topK = 5, fileExtension = "java")
        assertEquals(0, results.size)
    }
}
