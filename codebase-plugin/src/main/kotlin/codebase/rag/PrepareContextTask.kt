package codebase.rag

import contracts.context.CompositeContextConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "LLM-based RAG context generation is probabilistic")
abstract class PrepareContextTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workspaceRoot: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val ragQuestion: Property<String>

    @get:Input
    @get:Optional
    abstract val projectName: Property<String>

    @TaskAction
    fun execute() {
        val rootDir = workspaceRoot.get().asFile
        val question = ragQuestion.getOrElse("architecture du workspace")
        val name = projectName.getOrElse(rootDir.name)

        val contextConfig = CompositeContextConfig()
        val cfg = PgVectorConfig.fromEnv()
        val store = cfg.toVectorStore()

        try {
            store.initSchema()
            val existingDocCount = store.countDocuments()
            if (existingDocCount == 0) {
                indexBoroughSources(rootDir, store, name)
            }
        } catch (e: Exception) {
            logger.warn("[collectFromCodebase] pgvector unavailable — EAGER+Graphify only: {}", e.message)
        }

        val pipeline = EmbeddingPipeline(store)
        val builder = CompositeContextBuilder(rootDir, store, pipeline, contextConfig)
        val composite = builder.buildScoped(name, question)

        val injector = OpencodeInjector()
        val output = outputFile.get().asFile
        injector.injectToFile(composite, output)

        logger.lifecycle("[collectFromCodebase] {}: {} bytes (EAGER={}, RAG={}, Graphify={})",
            name,
            output.length(),
            composite.eagerSection.length,
            composite.ragSection.length,
            composite.graphifySection.length)
    }

    private fun indexBoroughSources(rootDir: File, store: VectorStore, boroughName: String) {
        logger.lifecycle("[collectFromCodebase] No indexed documents — indexing {}...", boroughName)
        val walker = codebase.walker.WorkspaceWalker(rootDir)
        val sourceExtensions = listOf("kt", "adoc", "kts")
        val configExtensions = listOf("yml", "yaml", "json")
        val files = walker.walk().filter { it.extension in sourceExtensions + configExtensions }

        val metadataExtractor = KotlinMetadataExtractor(repoName = boroughName)
        var totalChunks = 0

        for (wf in files) {
            val file = File(wf.filePath)
            val rawText = try {
                file.readText()
            } catch (e: Exception) {
                logger.warn("Cannot read {}: {}", wf.filePath, e.message)
                continue
            }
            val text = if (wf.extension in configExtensions) {
                YamlConfigAnonymizer.anonymize(rawText, wf.extension)
            } else rawText

            val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(text)
            val metadata = if (wf.extension == "kt") {
                metadataExtractor.extract(wf.filePath, rawText)
            } else {
                KotlinMetadata(packageName = null, className = null, repoName = boroughName)
            }
            store.insertDocument(wf.fileName, wf.filePath, wf.fileSize, chunks, metadata.packageName, metadata.className, metadata.repoName)
            totalChunks += chunks.size
        }

        if (totalChunks > 0) {
            val allRecords = store.fetchAllChunks()
            val pipeline = EmbeddingPipeline(store)
            pipeline.embedAll(allRecords)
        }
        logger.lifecycle("[collectFromCodebase] Indexed {}: {} documents, {} chunks", boroughName, store.countDocuments(), store.countChunks())
    }
}
