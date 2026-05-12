package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File

object CompositeContextBuilderMain {
    private val log = LoggerFactory.getLogger(CompositeContextBuilderMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val ragQuestion = if (args.isNotEmpty()) args[0] else "architecture du workspace"
        val cfg = PgVectorConfig.fromEnv()
        val workspaceRoot = System.getenv("CODEBASE_ROOT_DIR")?.let { File(it) } ?: File(".").absoluteFile.parentFile.parentFile

        val store = cfg.toVectorStore()
        val pipeline = EmbeddingPipeline(store)
        val config = CompositeContextConfig()
        val builder = CompositeContextBuilder(workspaceRoot, store, pipeline, config)

        StdoutFormatter.banner("Composite Context Builder — US-9.10")

        StdoutFormatter.ctx("RAG question: $ragQuestion")
        StdoutFormatter.ctx("Token budget : ${config.totalTokenBudget} (EAGER ${config.eagerLazyTokens}, RAG ${config.ragTokens}, Graphify ${config.graphifyTokens}, overhead ${config.overheadTokens})")

        val composite = builder.build(ragQuestion)

        StdoutFormatter.separator()
        StdoutFormatter.section(StdoutFormatter.Tag.CTX, "EAGER / LAZY (${composite.eagerSection.lines().size} lignes)")
        StdoutFormatter.section(StdoutFormatter.Tag.PLAN, "RAG pgvector (${composite.ragSection.lines().size} lignes)")
        StdoutFormatter.section(StdoutFormatter.Tag.RESULT, "Graphify")

        println()
        println(composite.eagerSection)
        println("--- RAG ---")
        println(composite.ragSection)
        println("--- GRAPHIFY ---")
        println(composite.graphifySection)

        StdoutFormatter.separator()
        log.info("CompositeContext built: EAGER={} chars, RAG={} chars, Graphify={} chars",
            composite.eagerSection.length, composite.ragSection.length, composite.graphifySection.length)
    }
}
