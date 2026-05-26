package codebase.scenarios

import contracts.context.CompositeContextConfig
import codebase.rag.ChunkTokenizer
import codebase.rag.CompositeContextBuilder
import codebase.rag.EmbeddingPipeline
import codebase.rag.OpencodeInjector
import codebase.rag.VectorStore
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrepareContextUnitTest {

    private val log = LoggerFactory.getLogger(PrepareContextUnitTest::class.java)
    private val container = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg17").apply {
        withDatabaseName("codebase_rag")
        withUsername("codebase")
        withPassword("codebase")
        withStartupTimeout(java.time.Duration.ofMinutes(2))
        withReuse(false)
    }

    private lateinit var store: VectorStore
    private lateinit var pipeline: EmbeddingPipeline
    private var datasetIndexed = false

    @BeforeAll
    fun setUp() {
        container.start()
        store = VectorStore(container.jdbcUrl, container.username, container.password)
        store.initSchema()
        pipeline = EmbeddingPipeline(store)
        log.info("Testcontainers pgvector started")
    }

    @BeforeEach
    fun ensureDatasetIndexed() {
        if (!datasetIndexed) {
            indexDatasetFiles()
            datasetIndexed = true
        }
    }

    @AfterAll
    fun tearDown() {
        container.stop()
    }

    fun setupBoroughDir(dir: File, boroughName: String) {
        File(dir, ".agents").mkdirs()
        File(dir, ".agents/INDEX.adoc").writeText("= INDEX $boroughName\n== Roadmap\nEPIC 1 DONE\nEPIC 2 TODO\n")
        File(dir, "PROMPT_REPRISE.adoc").writeText("= PROMPT_REPRISE $boroughName\nMission: build tests\n")
    }

    @Test
    fun `buildScoped produces non-empty context for known borough`(@TempDir tempDir: File) {
        setupBoroughDir(tempDir, "test-borough")
        val config = CompositeContextConfig()
        val builder = CompositeContextBuilder(tempDir, store, pipeline, config)
        val composite = builder.buildScoped("test-borough", "repository tasks database")

        assertTrue(composite.eagerSection.isNotEmpty(), "EAGER should not be empty")
        assertTrue(composite.ragSection.isNotEmpty(), "RAG should not be empty")
        assertTrue(composite.graphifySection.isNotEmpty(), "Graphify should not be empty")
    }

    @Test
    fun `OpencodeInjector formats all three sections`(@TempDir tempDir: File) {
        setupBoroughDir(tempDir, "test-borough")
        val config = CompositeContextConfig()
        val builder = CompositeContextBuilder(tempDir, store, pipeline, config)
        val composite = builder.buildScoped("test-borough", "HTTP client configuration")

        val result = OpencodeInjector().inject(composite)

        assertTrue(result.contains("[RÈGLES_EAGER]"), "Missing EAGER header")
        assertTrue(result.contains("[CONTEXTE_RAG]"), "Missing RAG header")
        assertTrue(result.contains("[RELATIONS_GRAPHIFY]"), "Missing Graphify header")
        assertTrue(result.contains("[CONTEXTE_DOCS]"), "Missing Docs header")
    }

    @Test
    fun `injectToFile writes valid context file`(@TempDir tempDir: File) {
        setupBoroughDir(tempDir, "test-borough")
        val config = CompositeContextConfig()
        val builder = CompositeContextBuilder(tempDir, store, pipeline, config)
        val composite = builder.buildScoped("test-borough", "repository tasks database")

        val output = File(tempDir, "context.txt")
        OpencodeInjector().injectToFile(composite, output)

        assertTrue(output.isFile, "Output file should exist")
        assertTrue(output.length() > 100, "Output should be > 100 bytes, got ${output.length()}")
        val content = output.readText()
        assertTrue(content.contains("[RÈGLES_EAGER]"), "Missing EAGER section")
        assertTrue(content.contains("[CONTEXTE_RAG]"), "Missing RAG section")
        assertTrue(content.contains("[RELATIONS_GRAPHIFY]"), "Missing Graphify section")
        assertTrue(content.contains("[CONTEXTE_DOCS]"), "Missing Docs section")
    }

    @Test
    fun `RAG section contains similarity scores from vector query`(@TempDir tempDir: File) {
        setupBoroughDir(tempDir, "test-borough")
        val config = CompositeContextConfig()
        val builder = CompositeContextBuilder(tempDir, store, pipeline, config)
        val composite = builder.buildScoped("test-borough", "HTTP client configuration")

        val simMatches = Regex("""\[sim=""").findAll(composite.ragSection).count()
        assertTrue(simMatches >= 1, "Expected >= 1 similarity chunk in RAG, got $simMatches")
    }

    @Test
    fun `AssembleWorkspaceContext aggregates multiple context files`(@TempDir tempDir: File) {
        val boroughA = File(tempDir, "borough-a").apply { mkdirs() }
        val boroughB = File(tempDir, "borough-b").apply { mkdirs() }
        setupBoroughDir(boroughA, "borough-a")
        setupBoroughDir(boroughB, "borough-b")

        val config = CompositeContextConfig()
        val injector = OpencodeInjector()

        val builderA = CompositeContextBuilder(boroughA, store, pipeline, config)
        val builderB = CompositeContextBuilder(boroughB, store, pipeline, config)

        val ctxA = File(boroughA, "build/context").apply { mkdirs() }
        val ctxB = File(boroughB, "build/context").apply { mkdirs() }

        injector.injectToFile(builderA.buildScoped("borough-a", "tasks"), File(ctxA, "borough-a.context.txt"))
        injector.injectToFile(builderB.buildScoped("borough-b", "config"), File(ctxB, "borough-b.context.txt"))

        val aggregateOutput = File(tempDir, "workspace-context.txt")
        assembleContexts(tempDir, aggregateOutput)

        assertTrue(aggregateOutput.isFile, "Assembled output should exist")
        val content = aggregateOutput.readText()
        assertTrue(content.contains("borough-a"), "Should contain borough-a name")
        assertTrue(content.contains("borough-b"), "Should contain borough-b name")
        assertTrue(content.contains("INDEX borough-a"), "Should contain borough-a INDEX content")
        assertTrue(content.length > 200, "Should be > 200 bytes, got ${content.length}")
    }

    private fun indexDatasetFiles() {
        val datasetDir = File("src/test/resources/datasets")
        val allFiles = datasetDir.listFiles { it.isFile }?.sortedBy { it.name } ?: emptyList()

        var docCount = 0
        for (file in allFiles) {
            val text = file.readText()
            val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(text)
            if (chunks.isEmpty()) continue
            store.insertDocument(file.name, file.path, file.length(), chunks, null, null, "test-dataset")
            docCount++
        }

        val records = store.fetchAllChunks()
        pipeline.embedAll(records)
        log.info("Indexed {} documents, {} chunks embedded", docCount, records.size)
    }

    private fun assembleContexts(parentDir: File, output: File) {
        val contextFiles = parentDir.listFiles { it.isDirectory }
            ?.sortedBy { it.name }
            ?.flatMap { boroughDir ->
                val ctxDir = boroughDir.resolve("build/context")
                if (!ctxDir.isDirectory) return@flatMap emptyList()
                ctxDir.listFiles { it.isFile && it.name.endsWith(".context.txt") }
                    ?.map { boroughDir.name to it } ?: emptyList()
            } ?: emptyList()

        val sb = StringBuilder()
        sb.appendLine("= CONTEXTE WORKSPACE AUGMENTE")
        sb.appendLine()
        if (contextFiles.isEmpty()) {
            sb.appendLine("Aucun fichier contexte.")
        } else {
            sb.appendLine("== Sommaire")
            for ((name, _) in contextFiles) sb.appendLine("* $name")
            sb.appendLine()
            for ((name, file) in contextFiles) {
                sb.appendLine("== $name")
                sb.appendLine(file.readText())
                sb.appendLine()
            }
        }
        output.parentFile.mkdirs()
        output.writeText(sb.toString())
    }
}
