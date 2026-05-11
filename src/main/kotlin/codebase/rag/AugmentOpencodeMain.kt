package codebase.rag

import codebase.walker.WorkspaceWalker
import org.slf4j.LoggerFactory
import java.io.File

object AugmentOpencodeMain {
    private val log = LoggerFactory.getLogger(AugmentOpencodeMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val ragQuestion = if (args.isNotEmpty()) args[0] else "architecture du workspace"
        val jdbcUrl = System.getenv("PGVECTOR_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/codebase_rag"
        val user = System.getenv("PGVECTOR_USER") ?: "codebase"
        val password = System.getenv("PGVECTOR_PASSWORD") ?: "codebase"
        val workspaceRoot = System.getenv("CODEBASE_ROOT_DIR")?.let { File(it) }
            ?: File(".").absoluteFile.parentFile.parentFile
        val outputFile = File("/tmp/opencode-context.txt")

        StdoutFormatter.banner("Augment Opencode — US-9.12 (Pipeline complet)")
        StdoutFormatter.ctx("RAG question: $ragQuestion")

        val store = VectorStore(jdbcUrl, user, password)
        store.initSchema()

        val existingDocCount = store.countDocuments()
        if (existingDocCount == 0) {
            StdoutFormatter.plan("Aucun document indexé — exécution du walk + index...")
            indexWorkspace(workspaceRoot, store)
        } else {
            StdoutFormatter.plan("$existingDocCount documents déjà indexés — skip indexation")
        }

        StdoutFormatter.plan("Construction du contexte composite...")
        val pipeline = EmbeddingPipeline(store)
        val config = CompositeContextConfig()
        val builder = CompositeContextBuilder(workspaceRoot, store, pipeline, config)
        val composite = builder.build(ragQuestion)

        StdoutFormatter.plan("Injection avec headers [REGLES_EAGER]/[CONTEXTE_RAG]/[RELATIONS_GRAPHIFY]...")
        val injector = OpencodeInjector()
        injector.injectToFile(composite, outputFile)

        StdoutFormatter.separator()

        val eagerLines = composite.eagerSection.lines().count { it.isNotBlank() }
        val ragChunks = composite.ragSection.lines().count { it.startsWith("[sim=") }
        val fileSize = outputFile.length()

        StdoutFormatter.result("Pipeline augmentOpencode terminé")
        StdoutFormatter.result("EAGER   : $eagerLines lignes de gouvernance")
        StdoutFormatter.result("RAG     : $ragChunks chunks sémantiques (top-10 cosine)")
        StdoutFormatter.result("Output  : ${outputFile.absolutePath} ($fileSize bytes)")
        StdoutFormatter.result("Prêt    : concaténer ce fichier au prompt système opencode")

        StdoutFormatter.separator()
        log.info("augmentOpencode complete: {} bytes", fileSize)
    }

    private fun indexWorkspace(rootDir: File, store: VectorStore) {
        val sourceExtensions = listOf("kt", "adoc", "kts")
        val configExtensions = listOf("yml", "yaml", "json")

        val walker = WorkspaceWalker(rootDir)
        val files = walker.walk().filter { it.extension in sourceExtensions + configExtensions }
        log.info("Walk: {} files trouvés dans {}", files.size, rootDir.absolutePath)

        val metadataExtractor = KotlinMetadataExtractor(repoName = rootDir.name)
        var totalChunks = 0

        for (wf in files) {
            val file = File(wf.filePath)
            val rawText = try {
                file.readText()
            } catch (e: Exception) {
                log.warn("Cannot read {}: {}", wf.filePath, e.message)
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
        }

        if (totalChunks > 0) {
            val allRecords = store.fetchAllChunks()
            val pipeline = EmbeddingPipeline(store)
            pipeline.embedAll(allRecords)
        }
        log.info("Index done: {} docs, {} chunks", store.countDocuments(), store.countChunks())
    }
}
