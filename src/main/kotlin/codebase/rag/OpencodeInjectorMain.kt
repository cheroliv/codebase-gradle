package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File

object OpencodeInjectorMain {
    private val log = LoggerFactory.getLogger(OpencodeInjectorMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val ragQuestion = if (args.isNotEmpty()) args[0] else "architecture du workspace"
        val jdbcUrl = System.getenv("PGVECTOR_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/codebase_rag"
        val user = System.getenv("PGVECTOR_USER") ?: "codebase"
        val password = System.getenv("PGVECTOR_PASSWORD") ?: "codebase"
        val workspaceRoot = System.getenv("CODEBASE_ROOT_DIR")?.let { File(it) }
            ?: File(".").absoluteFile.parentFile.parentFile
        val outputFile = File("/tmp/opencode-context.txt")

        val store = VectorStore(jdbcUrl, user, password)
        val pipeline = EmbeddingPipeline(store)
        val config = CompositeContextConfig()
        val builder = CompositeContextBuilder(workspaceRoot, store, pipeline, config)
        val injector = OpencodeInjector()

        StdoutFormatter.banner("OpencodeInjector — US-9.11")

        StdoutFormatter.ctx("RAG question: $ragQuestion")
        StdoutFormatter.ctx("Output file : ${outputFile.absolutePath}")

        val composite = builder.build(ragQuestion)
        val injected = injector.injectToFile(composite, outputFile)

        StdoutFormatter.separator()

        val eagerLines = composite.eagerSection.lines().count { it.isNotBlank() }
        val ragChunks = composite.ragSection.lines().count { it.startsWith("[sim=") }
        val graphifyNodes = Regex("~\\d+ nodes").find(composite.graphifySection)?.value ?: "N/A"

        StdoutFormatter.result("EAGER   : $eagerLines lignes non-vides de gouvernance")
        StdoutFormatter.result("RAG     : $ragChunks chunks pertinents (similarité cosinus)")
        StdoutFormatter.result("Graphify: $graphifyNodes")

        StdoutFormatter.separator()
        log.info("OpencodeInjector complete: {} chars written to {}",
            injected.readText().length, outputFile.absolutePath)
    }
}
