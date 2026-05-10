package codebase.scenarios

import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.sql.DriverManager

class BatchValidationSteps {

    private val log = LoggerFactory.getLogger(BatchValidationSteps::class.java)
    private val ctx = PgVectorTestContext

    @When("I tokenize all dataset files into sentence-level chunks")
    fun `tokenize all dataset files`() {
        val datasetDir = java.io.File(ctx.DATASETS_DIR)
        val allFiles = datasetDir.listFiles { f -> f.isFile && !f.name.startsWith(".") }?.sortedBy { it.name }
            ?: throw AssertionError("No files found in ${ctx.DATASETS_DIR}")

        ctx.fileChunks.clear()
        for (file in allFiles) {
            val text = file.readText()
            val chunks = ctx.splitIntoSentenceLevelChunks(text)
            ctx.fileChunks[file.name] = chunks
            log.info("${file.name}: ${chunks.size} chunk(s) produced")
        }
        val total = ctx.fileChunks.values.sumOf { it.size }
        log.info("Total chunks across all files: $total")
    }

    @And("I insert all documents with extracted metadata into the pgvector database")
    fun `insert all documents with metadata into pgvector`() {
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
                        package_name TEXT,
                        class_name TEXT,
                        repo_name TEXT,
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

                val (pkg, cls) = if (fileName.endsWith(".kt")) {
                    DatasetEmbeddingSteps.extractKotlinMetadata(file.readText(), fileName)
                } else {
                    null to null
                }

                val docId: Long
                conn.prepareStatement(
                    "INSERT INTO documents (file_name, file_path, file_size, chunk_count, package_name, class_name, repo_name) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id"
                ).use { stmt ->
                    stmt.setString(1, fileName)
                    stmt.setString(2, file.path)
                    stmt.setLong(3, file.length())
                    stmt.setInt(4, chunksForFile.size)
                    stmt.setString(5, pkg)
                    stmt.setString(6, cls)
                    stmt.setString(7, "test-dataset")
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
                log.info("Inserted '${fileName}' with ${chunksForFile.size} chunks")
            }
        }
    }

    @When("I query raw chunk text for the phrase {string}")
    fun `query raw chunk text for phrase`(phrase: String) {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.prepareStatement("SELECT count(*) FROM chunks WHERE chunk_text LIKE ?").use { stmt ->
                stmt.setString(1, "%$phrase%")
                val rs = stmt.executeQuery()
                rs.next()
                val count = rs.getInt(1)
                log.info("Found $count chunks containing '$phrase'")
                assert(count == 0) { "Found $count chunks containing raw '$phrase'" }
            }
        }
    }

    @Then("no chunks contain raw secrets")
    fun `no chunks contain raw secrets`() {
        log.info("Confirmed: no raw secrets in chunk_text")
    }

    @Then("the top result is from a Kotlin source file")
    fun `top result is from Kotlin file`() {
        val top = ctx.topResults.first()
        assert(top.text.startsWith("(") && ".kt)" in top.text) {
            "Top result is not from a Kotlin file. Top text: ${top.text.take(120)}"
        }
        log.info("Top result verified as Kotlin source file")
    }
}
