package codebase.scenarios

import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.sql.DriverManager

class MetadataIndexingSteps {

    private val log = LoggerFactory.getLogger(MetadataIndexingSteps::class.java)
    private val ctx = PgVectorTestContext
    private var queryResults = listOf<DocRecordResult>()
    private var lastFoundFileName: String? = null
    private var lastFoundClassName: String? = null

    data class DocRecordResult(
        val fileName: String,
        val packageName: String?,
        val className: String?,
        val repoName: String?
    )

    @When("I tokenize each dataset Kotlin file into sentence-level chunks")
    fun `tokenize dataset Kotlin files`() {
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
        log.info("Total chunks across all Kotlin files: $total")
    }

    @And("I insert the documents with extracted metadata into the pgvector database")
    fun `insert documents with metadata into pgvector`() {
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
                val (pkg, cls) = DatasetEmbeddingSteps.extractKotlinMetadata(file.readText(), fileName)

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
                log.info("Inserted document '$fileName' pkg=$pkg cls=$cls with ${chunksForFile.size} chunks")
            }
        }
    }

    @Then("every Kotlin document has a non-null package_name")
    fun `every Kotlin doc has package name`() {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM documents WHERE file_name LIKE '%.kt' AND package_name IS NULL")
                rs.next()
                val nullCount = rs.getInt(1)
                assert(nullCount == 0) { "Found $nullCount Kotlin documents with null package_name" }
                log.info("All Kotlin documents have non-null package_name")
            }
        }
    }

    @Then("every Kotlin document has a non-null class_name")
    fun `every Kotlin doc has class name`() {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM documents WHERE file_name LIKE '%.kt' AND class_name IS NULL")
                rs.next()
                val nullCount = rs.getInt(1)
                assert(nullCount == 0) { "Found $nullCount Kotlin documents with null class_name" }
                log.info("All Kotlin documents have non-null class_name")
            }
        }
    }

    @When("I query documents by package {string}")
    fun `query documents by package`(packageName: String) {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.prepareStatement(
                "SELECT file_name, package_name, class_name, repo_name FROM documents WHERE package_name = ?"
            ).use { stmt ->
                stmt.setString(1, packageName)
                queryResults = stmt.executeQuery().use { rs ->
                    val results = mutableListOf<DocRecordResult>()
                    while (rs.next()) {
                        results.add(
                            DocRecordResult(
                                fileName = rs.getString("file_name"),
                                packageName = rs.getString("package_name"),
                                className = rs.getString("class_name"),
                                repoName = rs.getString("repo_name")
                            )
                        )
                    }
                    results
                }
                log.info("Found ${queryResults.size} document(s) for package '$packageName'")
                queryResults.forEach { r ->
                    log.info("  ${r.fileName} class=${r.className}")
                }
            }
        }
    }

    @Then("exactly {int} document is returned with file name {string}")
    fun `exactly N documents returned with filename`(expectedCount: Int, expectedFileName: String) {
        assert(queryResults.size == expectedCount) {
            "Expected $expectedCount document(s), got ${queryResults.size}"
        }
        lastFoundFileName = queryResults.firstOrNull()?.fileName
        assert(lastFoundFileName == expectedFileName) {
            "Expected file name '$expectedFileName', got '$lastFoundFileName'"
        }
        log.info("Found $expectedCount document: '$expectedFileName'")
    }

    @And("the returned document has a class_name {string}")
    fun `returned document has class name`(expectedClassName: String) {
        lastFoundClassName = queryResults.firstOrNull()?.className
        assert(lastFoundClassName == expectedClassName) {
            "Expected class_name '$expectedClassName', got '$lastFoundClassName'"
        }
        log.info("Verified class_name: '$expectedClassName'")
    }

    @Then("the top result contains metadata package {string} or class {string}")
    fun `top result contains metadata`(packageName: String, className: String) {
        val top = PgVectorTestContext.topResults.first()
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            val chunkId = top.chunkId
            conn.prepareStatement("""
                SELECT d.package_name, d.class_name, d.file_name
                FROM chunks c JOIN documents d ON c.document_id = d.id
                WHERE c.id = ?
            """.trimIndent()).use { stmt ->
                stmt.setLong(1, chunkId)
                val rs = stmt.executeQuery()
                assert(rs.next()) { "No document metadata found for chunk $chunkId" }
                val pkg = rs.getString("package_name") ?: "null"
                val cls = rs.getString("class_name") ?: "null"
                val fn = rs.getString("file_name")
                assert(pkg == packageName || cls == className) {
                    "Top result metadata ($fn: pkg=$pkg cls=$cls) does not match expected " +
                        "package '$packageName' or class '$className'. ChunkId=$chunkId"
                }
                log.info("Top result metadata verified: $fn pkg=$pkg cls=$cls")
            }
        }
    }
}
