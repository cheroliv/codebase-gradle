package codebase.scenarios

import dev.langchain4j.data.segment.TextSegment
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.sql.DriverManager

class DatasetEmbeddingSteps {

    private val log = LoggerFactory.getLogger(DatasetEmbeddingSteps::class.java)
    private val ctx = PgVectorTestContext

    @Given("a pgvector container is running")
    fun `a pgvector container is running`() {
        log.info("Starting pgvector container...")
        ctx.startContainer()
        log.info("pgvector container started")
    }

    @When("I tokenize each dataset file into sentence-level chunks of approximately 512 tokens")
    fun `tokenize dataset files into sentence-level chunks`() {
        val datasetDir = java.io.File(ctx.DATASETS_DIR)
        val ktFiles = datasetDir.listFiles { f -> f.isFile && f.name.endsWith(".kt") }?.sortedBy { it.name }
            ?: throw AssertionError("No .kt files found in ${ctx.DATASETS_DIR}")

        ctx.fileChunks.clear()
        for (file in ktFiles) {
            val text = file.readText()
            val chunks = ctx.splitIntoSentenceLevelChunks(text)
            ctx.fileChunks[file.name] = chunks
            log.info("${file.name}: ${chunks.size} chunk(s) produced")
        }
        val total = ctx.fileChunks.values.sumOf { it.size }
        log.info("Total chunks across all files: $total")
    }

    @And("I insert the documents and chunks into the pgvector database")
    fun `insert documents and chunks into pgvector`() {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
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

            val datasetDir = java.io.File(ctx.DATASETS_DIR)
            for (fileName in ctx.fileChunks.keys.sorted()) {
                val file = java.io.File(datasetDir, fileName)
                val chunksForFile = ctx.fileChunks[fileName] ?: continue

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
                        stmt.setInt(4, ctx.estimateTokenCount(chunkText))
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
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
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
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
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
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
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
        val dim = ctx.model.dimension()
        assert(dim == 384) { "Expected dimension 384, got $dim" }
        log.info("AllMiniLmL6V2 embedding model loaded, dimension=$dim")
    }

    @When("I compute embeddings for all chunks")
    fun `compute embeddings for all chunks`() {
        val records = mutableListOf<Pair<Long, String>>()
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT id, chunk_text FROM chunks ORDER BY id")
                while (rs.next()) {
                    records.add(rs.getLong("id") to rs.getString("chunk_text"))
                }
            }
        }

        log.info("Computing embeddings for ${records.size} chunks...")
        for ((id, text) in records) {
            val embedding = ctx.model.embed(TextSegment.from(text)).content()
            val vectorStr = embedding.vector().joinToString(",", "[", "]")

            DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
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
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
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
        val queryEmbedding = ctx.model.embed(TextSegment.from(query)).content()
        val queryVectorStr = queryEmbedding.vector().joinToString(",", "[", "]")

        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.prepareStatement("""
                SELECT c.id, c.chunk_text, d.file_name,
                       1 - (c.embedding <=> ?::vector) AS similarity
                FROM chunks c
                JOIN documents d ON c.document_id = d.id
                WHERE c.embedding IS NOT NULL
                ORDER BY c.embedding <=> ?::vector
                LIMIT 5
            """.trimIndent()).use { stmt ->
                stmt.setString(1, queryVectorStr)
                stmt.setString(2, queryVectorStr)
                ctx.topResults = stmt.executeQuery().use { rs ->
                    val results = mutableListOf<PgVectorTestContext.TopResult>()
                    while (rs.next()) {
                        val text = "(${rs.getString("file_name")}) ${rs.getString("chunk_text")}"
                        results.add(
                            PgVectorTestContext.TopResult(
                                chunkId = rs.getLong("id"),
                                text = text,
                                similarity = rs.getDouble("similarity")
                            )
                        )
                    }
                    results
                }
            }
        }
        log.info("Query returned ${ctx.topResults.size} results")
        ctx.topResults.forEach { r ->
            log.info("  chunk_id=${r.chunkId} similarity=${"%.4f".format(r.similarity)} text=${r.text.take(80)}...")
        }
    }

    @Then("the top 5 results are returned ordered by cosine similarity")
    fun `top 5 results returned ordered by similarity`() {
        val results = ctx.topResults
        assert(results.size == 5) { "Expected 5 results, got ${results.size}" }
        for (i in 0 until results.size - 1) {
            assert(results[i].similarity >= results[i + 1].similarity) {
                "Results not ordered by descending similarity at index $i: " +
                    "${results[i].similarity} < ${results[i + 1].similarity}"
            }
        }
        log.info("Top 5 results correctly ordered by cosine similarity")
    }

    @Then("the highest-ranked chunk is semantically related to database task retrieval")
    fun `highest-ranked chunk is about database task retrieval`() {
        val top = ctx.topResults.first()
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
}
