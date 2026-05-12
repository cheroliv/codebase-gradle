package codebase.rag

import codebase.walker.WorkspaceWalker
import org.slf4j.LoggerFactory

object CodebaseIndexerMain {
    private val log = LoggerFactory.getLogger(CodebaseIndexerMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val cfg = PgVectorConfig.fromEnv()

        log.info("Connecting to pgvector at ${cfg.jdbcUrl.replace(Regex("password=.*"), "password=***")}")

        val store = cfg.toVectorStore()
        store.initSchema()

        val rootDir = System.getenv("CODEBASE_ROOT_DIR")?.let { java.io.File(it) } ?: java.io.File(".")
        val sourceExtensions = listOf("kt", "adoc", "kts")
        val configExtensions = listOf("yml", "yaml", "json")

        val walker = WorkspaceWalker(rootDir)
        val files = walker.walk().filter { it.extension in sourceExtensions + configExtensions }

        log.info("Walking {}: found {} indexable files", rootDir.absolutePath, files.size)

        val metadataExtractor = KotlinMetadataExtractor(repoName = rootDir.name)
        var totalChunks = 0
        for (wf in files) {
            val file = java.io.File(wf.filePath)
            val rawText = try {
                file.readText()
            } catch (e: Exception) {
                log.error("Cannot read {}: {}", wf.filePath, e.message)
                continue
            }
            val text = if (wf.extension in configExtensions) {
                YamlConfigAnonymizer.anonymize(rawText, wf.extension)
            } else rawText

            val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(text)
            val metadata = if (wf.extension == "kt") {
                metadataExtractor.extract(wf.filePath, rawText)
            } else {
                KotlinMetadata(packageName = null, className = null, repoName = rootDir.name)
            }
            store.insertDocument(wf.fileName, wf.filePath, wf.fileSize, chunks, metadata.packageName, metadata.className, metadata.repoName)
            totalChunks += chunks.size
            log.info("  {}: {} chunks{}{}", wf.fileName, chunks.size,
                if (metadata.packageName != null) " (pkg=${metadata.packageName})" else "",
                if (wf.extension in configExtensions) " [anonymized]" else "")
        }

        log.info("Total: {} documents, {} chunks", files.size, totalChunks)

        if (totalChunks > 0) {
            val allRecords = store.fetchAllChunks()
            log.info("Computing embeddings for {} chunks via ONNX AllMiniLmL6V2...", allRecords.size)
            val pipeline = EmbeddingPipeline(store)
            pipeline.embedAll(allRecords)
            log.info("Embeddings computed and stored")
        }

        log.info("indexCodebase complete — {} documents, {} chunks indexed", store.countDocuments(), store.countChunks())
    }
}
