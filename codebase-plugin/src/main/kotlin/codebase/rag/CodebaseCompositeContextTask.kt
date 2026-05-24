package codebase.rag

import codex.store.CodexVectorStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.time.Instant

@DisableCachingByDefault(because = "pgvector RAG + Codex VectorStore — external dependencies, non-cacheable")
abstract class CodebaseCompositeContextTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val query: Property<String>

    @get:Input
    @get:Optional
    abstract val topK: Property<String>

    @get:Input
    abstract val trainingProjectDir: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val q = query.orNull ?: "architecture du workspace"
        val k = topK.orNull?.toIntOrNull() ?: 10
        val mapper = jacksonObjectMapper()

        val codexEntries = mutableListOf<Map<String, Any>>()
        runCatching {
            val store = CodexVectorStore()
            val results = store.searchBlocking(q, k)
            results.forEach { r ->
                codexEntries.add(mapOf(
                    "source" to "codex",
                    "chunkId" to r.chunkId,
                    "chunkText" to r.chunkText.take(500),
                    "sectionPath" to r.sectionPath,
                    "headingLevel" to r.headingLevel,
                    "sourceDocument" to r.sourceDocument,
                    "similarity" to r.similarity
                ))
            }
            logger.lifecycle("[codebase] CodexVectorStore OK — {} resultats", results.size)
        }.onFailure { e ->
            logger.warn("[codebase] CodexVectorStore indisponible: {}", e.message)
        }

        val trainingEntries = mutableListOf<Map<String, Any>>()
        val trainingDir = trainingProjectDir.get()
        val trainingBuildFile = File(trainingDir, "build.gradle.kts")

        if (trainingBuildFile.exists()) {
            logger.lifecycle("[codebase] Appel training-gradle → generateCompositeContext")
            val proc = ProcessBuilder(
                listOf("./gradlew", "-q", "generateCompositeContext", "-Pquery=$q", "-PtopK=$k")
            )
                .directory(File(trainingDir))
                .redirectErrorStream(true)
                .start()
            val exit = proc.waitFor()

            val trainingJson = File(trainingDir, "build/training/composite-context.json")
            if (exit == 0 && trainingJson.exists()) {
                val json = mapper.readValue(trainingJson, Map::class.java)
                @Suppress("UNCHECKED_CAST")
                val entries = json["entries"] as? List<Map<String, Any>> ?: emptyList()
                trainingEntries.addAll(entries)
                logger.lifecycle("[codebase] training OK — {} entries recues", entries.size)
            } else {
                logger.warn("[codebase] training indisponible (exit=$exit)")
            }
        } else {
            logger.warn("[codebase] training-gradle non trouve — {} absent", trainingDir)
        }

        val allEntries = codexEntries + trainingEntries
        val result = mapOf(
            "source" to "codebase",
            "query" to q,
            "topK" to k,
            "entries" to allEntries,
            "count" to allEntries.size,
            "codexCount" to codexEntries.size,
            "trainingCount" to trainingEntries.size,
            "timestamp" to System.currentTimeMillis()
        )

        val output = outputFile.asFile.get()
        output.parentFile.mkdirs()
        mapper.writerWithDefaultPrettyPrinter().writeValue(output, result)

        val metadata = metadataMap(allEntries.size, codexEntries.size, trainingEntries.size)
        val metadataFile = File(output.parentFile, "metadata.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(metadataFile, metadata)

        logger.lifecycle("[codebase] generateCompositeContext — {} entries (codex:{} + training:{}) → {}",
            allEntries.size, codexEntries.size, trainingEntries.size, output.absolutePath)
    }

    private fun metadataMap(total: Int, codexN: Int, trainingN: Int): Map<String, Any> = mapOf(
        "source" to "codebase",
        "type" to "composite-context",
        "sessions" to total,
        "generatedAt" to Instant.now().toString(),
        "model" to "onnx-local",
        "version" to "1.0",
        "dependencies" to listOf("brooklyn", "newark")
    )
}
