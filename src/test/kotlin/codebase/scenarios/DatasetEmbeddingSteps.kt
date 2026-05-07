package codebase.scenarios

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

class DatasetEmbeddingSteps {

    companion object {
        private val log = LoggerFactory.getLogger(DatasetEmbeddingSteps::class.java)
        private var sharedContainer: PostgreSQLContainer<Nothing>? = null
        private const val DATASETS_DIR = "src/test/resources/datasets"

        fun jdbcUrl() = sharedContainer?.jdbcUrl
            ?: throw IllegalStateException("Container not started")

        fun jdbcUser() = sharedContainer?.username ?: "codebase"
        fun jdbcPassword() = sharedContainer?.password ?: "codebase"
    }

    data class TopResult(val chunkId: Long, val text: String, val similarity: Double)

    private val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }
    private val fileChunks = mutableMapOf<String, List<String>>()
    private var topResults = listOf<TopResult>()

    @Given("a pgvector container is running")
    fun `a pgvector container is running`() {
        if (sharedContainer != null && sharedContainer!!.isRunning) return

        log.info("Starting pgvector container...")
        sharedContainer = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg17").apply {
            withDatabaseName("codebase_rag")
            withUsername("codebase")
            withPassword("codebase")
            withStartupTimeout(java.time.Duration.ofMinutes(2))
            withReuse(false)
        }.also { it.start() }
        log.info("pgvector container started: ${sharedContainer!!.containerId}")
    }

    @When("I tokenize each dataset file into sentence-level chunks of approximately 512 tokens")
    fun `tokenize dataset files into sentence-level chunks`() {
        val datasetDir = java.io.File(DATASETS_DIR)
        val ktFiles = datasetDir.listFiles { f -> f.isFile && f.name.endsWith(".kt") }?.sortedBy { it.name }
            ?: throw AssertionError("No .kt files found in $DATASETS_DIR")

        fileChunks.clear()
        for (file in ktFiles) {
            val text = file.readText()
            val chunks = splitIntoSentenceLevelChunks(text)
            fileChunks[file.name] = chunks
            log.info("${file.name}: ${chunks.size} chunk(s) produced")
        }
        val total = fileChunks.values.sumOf { it.size }
        log.info("Total chunks across all files: $total")
    }

    @And("I insert the documents and chunks into the pgvector database")
    fun `insert documents and chunks into pgvector`() {
        DriverManager.getConnection(jdbcUrl(), jdbcUser(), jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector")
                stmt.execute("DROP TABLE IF EXISTS chunks CASCADE")
                stmt.execute("DROP TABLE IF EXISTS documents CASCADE")
                stmt.execute("""
                    CREATE TABLE documents (
                        id BIGSERIAL PRIMARY KEY,
                        file_name TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        file_size BIGINT NOT NULL,
                        chunk_count INTEGER NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                    )
                """.trimIndent())
                stmt.execute("""
                    CREATE TABLE chunks (
                        id BIGSERIAL PRIMARY KEY,
                        document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                        chunk_index INTEGER NOT NULL,
                        chunk_text TEXT NOT NULL,
                        token_count INTEGER,
                        embedding vector(384),
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
                    )
                """.trimIndent())
            }

            val datasetDir = java.io.File(DATASETS_DIR)
            for (fileName in fileChunks.keys.sorted()) {
                val file = java.io.File(datasetDir, fileName)
                val chunksForFile = fileChunks[fileName] ?: continue

                val docId: Long
                conn.prepareStatement(
                    "INSERT INTO documents (file_name, file_path, file_size, chunk_count) VALUES (?, ?, ?, ?) RETURNING id"
                ).use { stmt ->
                    stmt.setString(1, fileName)
                    stmt.setString(2, file.path)
                    stmt.setLong(3, file.length())
                    stmt.setInt(4, chunksForFile.size)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        docId = rs.getLong(1)
                    }
                }

                conn.prepareStatement(
                    "INSERT INTO chunks (document_id, chunk_index, chunk_text, token_count) VALUES (?, ?, ?, ?)"
                ).use { stmt ->
                    chunksForFile.forEachIndexed { index, chunkText ->
                        stmt.setLong(1, docId)
                        stmt.setInt(2, index)
                        stmt.setString(3, chunkText)
                        stmt.setInt(4, estimateTokenCount(chunkText))
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
                log.info("Inserted document '$fileName' id=$docId with ${chunksForFile.size} chunks")
            }
        }
    }

    @Then("the documents table has exactly {int} rows")
    fun `documents table has exactly N rows`(expected: Int) {
        DriverManager.getConnection(jdbcUrl(), jdbcUser(), jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM documents")
                rs.next()
                val count = rs.getInt(1)
                assert(count == expected) { "Expected $expected documents, got $count" }
                log.info("Documents table has $count rows")
            }
        }
    }

    @Then("the chunks table has more than 0 rows")
    fun `chunks table has more than 0 rows`() {
        DriverManager.getConnection(jdbcUrl(), jdbcUser(), jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM chunks")
                rs.next()
                val count = rs.getInt(1)
                assert(count > 0) { "Chunks table should have > 0 rows, got $count" }
                log.info("Chunks table has $count rows")
            }
        }
    }

    @Then("each chunk has a valid foreign key to its parent document")
    fun `each chunk has valid FK`() {
        DriverManager.getConnection(jdbcUrl(), jdbcUser(), jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM chunks c LEFT JOIN documents d ON c.document_id = d.id WHERE d.id IS NULL"
                )
                rs.next()
                val orphans = rs.getInt(1)
                assert(orphans == 0) { "Found $orphans orphaned chunks with no parent document" }
                log.info("All chunks have valid document foreign keys")
            }
        }
    }

    @When("I load the AllMiniLmL6V2 embedding model via ONNX Runtime")
    fun `load AllMiniLmL6V2 model`() {
        val dim = model.dimension()
        assert(dim == 384) { "Expected dimension 384, got $dim" }
        log.info("AllMiniLmL6V2 embedding model loaded, dimension=$dim")
    }

    @And("I compute embeddings for all chunks")
    fun `compute embeddings for all chunks`() {
        val records = mutableListOf<Pair<Long, String>>()
        DriverManager.getConnection(jdbcUrl(), jdbcUser(), jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT id, chunk_text FROM chunks ORDER BY id")
                while (rs.next()) {
                    records.add(rs.getLong("id") to rs.getString("chunk_text"))
                }
            }
        }

        log.info("Computing embeddings for ${records.size} chunks...")
        for ((id, text) in records) {
            val embedding = model.embed(TextSegment.from(text)).content()
            val vectorStr = embedding.vector().joinToString(",", "[", "]")

            DriverManager.getConnection(jdbcUrl(), jdbcUser(), jdbcPassword()).use { conn ->
                conn.prepareStatement("UPDATE chunks SET embedding = ?::vector WHERE id = ?").use { stmt ->
                    stmt.setString(1, vectorStr)
                    stmt.setLong(2, id)
                    stmt.executeUpdate()
                }
            }
        }
        log.info("Embeddings computed and stored for all ${records.size} chunks")
    }

    @Then("every chunk has a non-null embedding of dimension 384")
    fun `every chunk has non-null embedding of dimension 384`() {
        DriverManager.getConnection(jdbcUrl(), jdbcUser(), jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs1 = stmt.executeQuery("SELECT count(*) FROM chunks WHERE embedding IS NULL")
                rs1.next()
                val nullCount = rs1.getInt(1)
                assert(nullCount == 0) { "Found $nullCount chunks with null embedding" }

                val rs2 = stmt.executeQuery("SELECT count(*) FROM chunks WHERE vector_dims(embedding) != 384")
                rs2.next()
                val wrongDim = rs2.getInt(1)
                assert(wrongDim == 0) { "Found $wrongDim chunks with non-384 dimension" }

                log.info("All chunks have valid 384-dimensional embeddings")
            }
        }
    }

    @When("I query with the phrase {string}")
    fun `query with phrase`(query: String) {
        val queryEmbedding = model.embed(TextSegment.from(query)).content()
        val queryVectorStr = queryEmbedding.vector().joinToString(",", "[", "]")

        DriverManager.getConnection(jdbcUrl(), jdbcUser(), jdbcPassword()).use { conn ->
            conn.prepareStatement("""
                SELECT c.id, c.chunk_text,
                       1 - (c.embedding <=> ?::vector) AS similarity
                FROM chunks c
                WHERE c.embedding IS NOT NULL
                ORDER BY c.embedding <=> ?::vector
                LIMIT 5
            """.trimIndent()).use { stmt ->
                stmt.setString(1, queryVectorStr)
                stmt.setString(2, queryVectorStr)
                topResults = stmt.executeQuery().use { rs ->
                    val results = mutableListOf<TopResult>()
                    while (rs.next()) {
                        results.add(
                            TopResult(
                                chunkId = rs.getLong("id"),
                                text = rs.getString("chunk_text"),
                                similarity = rs.getDouble("similarity")
                            )
                        )
                    }
                    results
                }
            }
        }
        log.info("Query returned ${topResults.size} results")
        topResults.forEach { r ->
            log.info("  chunk_id=${r.chunkId} similarity=${"%.4f".format(r.similarity)} text=${r.text.take(80)}...")
        }
    }

    @Then("the top 5 results are returned ordered by cosine similarity")
    fun `top 5 results returned ordered by similarity`() {
        assert(topResults.size == 5) { "Expected 5 results, got ${topResults.size}" }
        for (i in 0 until topResults.size - 1) {
            assert(topResults[i].similarity >= topResults[i + 1].similarity) {
                "Results not ordered by descending similarity at index $i: " +
                    "${topResults[i].similarity} < ${topResults[i + 1].similarity}"
            }
        }
        log.info("Top 5 results correctly ordered by cosine similarity")
    }

    @Then("the highest-ranked chunk is semantically related to database task retrieval")
    fun `highest-ranked chunk is about database task retrieval`() {
        val top = topResults.first()
        val relevance = top.text.contains("task", ignoreCase = true) ||
            top.text.contains("SELECT", ignoreCase = false) ||
            top.text.contains("tasks", ignoreCase = false) ||
            top.text.contains("TaskRepository", ignoreCase = false) ||
            top.text.contains("find", ignoreCase = true) ||
            top.text.contains("repository", ignoreCase = true) ||
            top.text.contains("database", ignoreCase = true)

        assert(relevance) {
            "Top chunk is not semantically related to task/database retrieval. " +
                "Top text excerpt: ${top.text.take(200)}"
        }
        log.info("Top chunk verified as semantically relevant: similarity=${"%.4f".format(top.similarity)}")
    }

    private fun splitIntoSentenceLevelChunks(text: String): List<String> {
        val segments = splitIntoSegments(text)
        val results = mutableListOf<String>()
        val current = StringBuilder()
        var currentTokens = 0
        val maxTokens = 512
        val overlapTokens = 50

        for (segment in segments) {
            val segTokens = estimateTokenCount(segment)

            if (currentTokens + segTokens > maxTokens && current.isNotEmpty()) {
                results.add(current.toString().trim())
                val overlap = buildOverlap(current.toString(), overlapTokens)
                current.clear().append(overlap)
                currentTokens = estimateTokenCount(overlap)
            }

            if (current.isNotEmpty()) current.append("\n\n")
            current.append(segment)
            currentTokens += segTokens + 2
        }

        if (current.isNotBlank()) {
            results.add(current.toString().trim())
        }

        if (results.isEmpty()) {
            results.add(text)
        }

        return results
    }

    private fun splitIntoSegments(text: String): List<String> {
        val lines = text.split("\n")
        val segments = mutableListOf<String>()
        val buf = StringBuilder()

        for (line in lines) {
            if (line.isBlank()) {
                if (buf.isNotBlank()) {
                    segments.add(buf.toString().trimEnd())
                    buf.clear()
                }
            } else {
                if (buf.isNotEmpty()) buf.append("\n")
                buf.append(line)
            }
        }
        if (buf.isNotBlank()) {
            segments.add(buf.toString().trimEnd())
        }
        return segments
    }

    private fun buildOverlap(text: String, overlapTokens: Int): String {
        val words = text.split(Regex("\\s+"))
        val overlapWords = (overlapTokens * 0.75).toInt().coerceAtMost(words.size)
        return words.takeLast(overlapWords.coerceAtLeast(1)).joinToString(" ") + "\n\n"
    }

    private fun estimateTokenCount(text: String): Int {
        val chars = text.trim().length
        return (chars / 3.5).toInt().coerceAtLeast(1)
    }
}
