package codebase.scenarios

import codebase.rag.ChunkTokenizer
import codebase.rag.YamlConfigAnonymizer
import io.cucumber.java.en.And
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.sql.DriverManager

class ConfigIndexingSteps {

    private val log = LoggerFactory.getLogger(ConfigIndexingSteps::class.java)
    private val ctx = PgVectorTestContext

    private val knownSecrets = mapOf(
        "codebase_config.yml" to listOf(
            "sk-ant-api03-abc123def456ghijk789",
            "sk-ant-stg-xyz987",
            "AIzaSyD_abc123def456ghijklmn",
            "s3cr3t_db_p4ss!",
            "ghp_abc123def456ghijklmn7890",
            "npm_xyz789abc"
        ),
        "app_config.json" to listOf(
            "sk-ant-api03-json-secret-key-12345",
            "AIzaSyD_json_abc123def456",
            "json_s3cr3t_p4ss",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
            "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4..."
        )
    )

    private fun createSchema() {
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
        }
    }

    private fun indexConfigFiles(extensions: List<String>) {
        createSchema()
        val datasetDir = java.io.File(ctx.DATASETS_DIR)
        val configFiles = datasetDir.listFiles { f ->
            f.isFile && extensions.any { ext -> f.name.endsWith(".$ext") }
        }?.sortedBy { it.name } ?: emptyList()

        ctx.fileChunks.clear()
        for (file in configFiles) {
            val rawText = file.readText()
            val ext = file.name.substringAfterLast('.', "")
            val anonymizedText = YamlConfigAnonymizer.anonymize(rawText, ext)
            val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(anonymizedText)
            ctx.fileChunks[file.name] = chunks
            log.info("${file.name}: raw=${rawText.length}c anon=${anonymizedText.length}c → ${chunks.size} chunk(s)")
        }

        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            for (fileName in ctx.fileChunks.keys.sorted()) {
                val file = java.io.File(datasetDir, fileName)
                val chunksForFile = ctx.fileChunks[fileName] ?: continue

                val docId: Long
                conn.prepareStatement(
                    "INSERT INTO documents (file_name, file_path, file_size, chunk_count, package_name, class_name, repo_name) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id"
                ).use { stmt ->
                    stmt.setString(1, fileName)
                    stmt.setString(2, file.path)
                    stmt.setLong(3, file.length())
                    stmt.setInt(4, chunksForFile.size)
                    stmt.setNull(5, java.sql.Types.VARCHAR)
                    stmt.setNull(6, java.sql.Types.VARCHAR)
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
                log.info("Inserted config doc '$fileName' with ${chunksForFile.size} chunks")
            }
        }
    }

    @When("I tokenize each YAML config file in the dataset directory after anonymization")
    fun `tokenize YAML config files after anonymization`() {
        indexConfigFiles(listOf("yml", "yaml"))
    }

    @And("I insert the anonymized YAML configs and chunks into the pgvector database")
    fun `insert anonymized YAML configs and chunks`() {
    }

    @Then("the documents table includes YAML config files")
    fun `documents table includes YAML config files`() {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT count(*) FROM documents WHERE file_name LIKE '%.yml' OR file_name LIKE '%.yaml'"
                )
                rs.next()
                val count = rs.getInt(1)
                assert(count > 0) { "No YAML config files found in documents table" }
                log.info("Documents table contains $count YAML config file(s)")
            }
        }
    }

    @Then("no YAML chunk text contains unmasked secrets")
    fun `no YAML chunk text contains unmasked secrets`() {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT c.chunk_text, d.file_name FROM chunks c JOIN documents d ON c.document_id = d.id WHERE d.file_name LIKE '%.yml' OR d.file_name LIKE '%.yaml'"
                )
                val findings = mutableListOf<String>()
                while (rs.next()) {
                    val text = rs.getString("chunk_text")
                    val fileName = rs.getString("file_name")
                    val secretsForFile = knownSecrets[fileName] ?: emptyList()
                    for (secret in secretsForFile) {
                        if (secret.length > 3 && text.contains(secret)) {
                            findings.add("$fileName: found unmasked secret '${secret.take(12)}...' in chunk")
                        }
                    }
                }
                assert(findings.isEmpty()) {
                    "Found ${findings.size} unmasked secret(s) in YAML chunks:\n${findings.joinToString("\n")}"
                }
                log.info("All YAML chunks verified — zero unmasked secrets")
            }
        }
    }

    @When("I tokenize each JSON config file in the dataset directory after anonymization")
    fun `tokenize JSON config files after anonymization`() {
        indexConfigFiles(listOf("json"))
    }

    @And("I insert the anonymized JSON configs and chunks into the pgvector database")
    fun `insert anonymized JSON configs and chunks`() {
    }

    @Then("the documents table includes JSON config files")
    fun `documents table includes JSON config files`() {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM documents WHERE file_name LIKE '%.json'")
                rs.next()
                val count = rs.getInt(1)
                assert(count > 0) { "No JSON config files found in documents table" }
                log.info("Documents table contains $count JSON config file(s)")
            }
        }
    }

    @Then("no JSON chunk text contains unmasked secrets")
    fun `no JSON chunk text contains unmasked secrets`() {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT c.chunk_text, d.file_name FROM chunks c JOIN documents d ON c.document_id = d.id WHERE d.file_name LIKE '%.json'"
                )
                val findings = mutableListOf<String>()
                while (rs.next()) {
                    val text = rs.getString("chunk_text")
                    val fileName = rs.getString("file_name")
                    val secretsForFile = knownSecrets[fileName] ?: emptyList()
                    for (secret in secretsForFile) {
                        if (secret.length > 3 && text.contains(secret)) {
                            findings.add("$fileName: found unmasked secret '${secret.take(12)}...' in chunk")
                        }
                    }
                }
                assert(findings.isEmpty()) {
                    "Found ${findings.size} unmasked secret(s) in JSON chunks:\n${findings.joinToString("\n")}"
                }
                log.info("All JSON chunks verified — zero unmasked secrets")
            }
        }
    }

    @When("I tokenize all config files in the dataset directory after anonymization")
    fun `tokenize all config files after anonymization`() {
        indexConfigFiles(listOf("yml", "yaml", "json"))
    }

    @And("I insert all anonymized configs and chunks into the pgvector database")
    fun `insert all anonymized configs and chunks`() {
    }

    @Then("the vector store contains zero chunks with unmasked sensitive values")
    fun `vector store contains zero chunks with unmasked sensitive values`() {
        DriverManager.getConnection(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword()).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    "SELECT c.chunk_text, d.file_name FROM chunks c JOIN documents d ON c.document_id = d.id WHERE d.file_name LIKE '%.yml' OR d.file_name LIKE '%.yaml' OR d.file_name LIKE '%.json'"
                )
                val findings = mutableListOf<String>()
                while (rs.next()) {
                    val text = rs.getString("chunk_text")
                    val fileName = rs.getString("file_name")
                    val secretsForFile = knownSecrets[fileName] ?: emptyList()
                    for (secret in secretsForFile) {
                        if (secret.length > 3 && text.contains(secret)) {
                            findings.add("$fileName: found unmasked secret '${secret.take(12)}...' in chunk")
                        }
                    }
                }
                assert(findings.isEmpty()) {
                    "Found ${findings.size} unmasked sensitive value(s) across all config chunks:\n${findings.joinToString("\n")}"
                }
                log.info("All config chunks verified — zero unmasked sensitive values in pgvector")
            }
        }
    }
}
