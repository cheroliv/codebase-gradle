package codebase.rag

import codebase.walker.WorkspaceWalker
import org.slf4j.LoggerFactory
import java.io.File

data class CompositeContext(
    val eagerSection: String,
    val ragSection: String,
    val graphifySection: String,
    val config: CompositeContextConfig
)

class CompositeContextBuilder(
    private val workspaceRoot: File,
    private val vectorStore: VectorStore,
    private val embeddingPipeline: EmbeddingPipeline,
    private val config: CompositeContextConfig = CompositeContextConfig()
) {
    private val log = LoggerFactory.getLogger(CompositeContextBuilder::class.java)
    private val queryService = VectorQueryService(vectorStore, embeddingPipeline)

    fun build(ragQuestion: String): CompositeContext {
        val eagerContent = collectEagerFiles()
        val truncatedEager = truncateTokens(eagerContent, config.eagerLazyTokens)

        val ragResults = queryService.query(ragQuestion, topK = 10)
        val ragContent = ragResults.joinToString("\n") { r ->
            "[sim=${"%.3f".format(r.similarity)}] ${r.text.take(300)}"
        }
        val truncatedRag = truncateTokens(ragContent, config.ragTokens)

        val graphifyContent = loadGraphifyStats()
        val truncatedGraphify = truncateTokens(graphifyContent, config.graphifyTokens)

        return CompositeContext(
            eagerSection = truncatedEager,
            ragSection = truncatedRag,
            graphifySection = truncatedGraphify,
            config = config
        )
    }

    private fun collectEagerFiles(): String {
        val sb = StringBuilder()
        val ossDir = workspaceRoot.resolve("foundry/OSS")
        if (!ossDir.isDirectory) return ""

        ossDir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach { projectDir ->
            val agentsDir = projectDir.resolve(".agents")
            if (!agentsDir.isDirectory) return@forEach

            for (eagerFile in listOf("INDEX.adoc", "PROMPT_REPRISE.adoc")) {
                val eagerPath = if (eagerFile == "PROMPT_REPRISE.adoc") {
                    projectDir.resolve(eagerFile)
                } else {
                    agentsDir.resolve(eagerFile)
                }
                if (!eagerPath.isFile) continue

                try {
                    val content = eagerPath.readText().take(2000)
                    sb.appendLine("--- ${projectDir.name}/$eagerFile ---")
                    sb.appendLine(content)
                    sb.appendLine()
                } catch (e: Exception) {
                    log.warn("Cannot read ${eagerPath.absolutePath}: {}", e.message)
                }
            }
        }
        return sb.toString()
    }

    private fun loadGraphifyStats(): String {
        val graphFile = workspaceRoot.resolve("office/graph.json")
        if (!graphFile.isFile) return "[Graphify] graph.json non trouve dans office/"

        val content = try {
            graphFile.readText()
        } catch (e: Exception) {
            return "[Graphify] graph.json illisible: ${e.message}"
        }

        val nodeCount = Regex(""""name":""").findAll(content).count()
        val edgeCount = Regex(""""source":""").findAll(content).count()
        return "[Graphify] global graph: ~$nodeCount nodes, ~$edgeCount edges"
    }

    private fun truncateTokens(text: String, maxTokens: Int): String {
        val lines = text.lines()
        val result = mutableListOf<String>()
        var tokenCount = 0
        for (line in lines) {
            val lineTokens = (line.length / 3.5).toInt().coerceAtLeast(1)
            if (tokenCount + lineTokens > maxTokens) break
            result.add(line)
            tokenCount += lineTokens
        }
        return result.joinToString("\n")
    }
}
