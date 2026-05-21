package codebase.rag

import cccp.vibecoding.contracts.context.CompositeContext
import cccp.vibecoding.contracts.context.CompositeContextConfig
import codebase.walker.WorkspaceWalker
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Locale

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
        return assembleContext(eagerContent, ragQuestion)
    }

    fun buildScoped(boroughName: String, ragQuestion: String): CompositeContext {
        val eagerContent = collectEagerForBorough(boroughName)
        return assembleContext(eagerContent, ragQuestion)
    }

    private fun assembleContext(eagerContent: String, ragQuestion: String): CompositeContext {
        val truncatedEager = truncateTokens(eagerContent, config.eagerLazyTokens)

        val truncatedRag = try {
            val ragResults = queryService.query(ragQuestion, topK = 10)
            val ragContent = ragResults.joinToString("\n") { r ->
                "[sim=${"%.3f".format(Locale.US, r.similarity)}] ${r.text.take(300)}"
            }
            truncateTokens(ragContent, config.ragTokens)
        } catch (e: Exception) {
            log.warn("RAG section unavailable (pgvector down?): {}", e.message)
            "[RAG] pgvector indisponible — section non generee"
        }

        val graphifyContent = loadGraphifyStats()
        val truncatedGraphify = truncateTokens(graphifyContent, config.graphifyTokens)

        return CompositeContext(
            eagerSection = truncatedEager,
            ragSection = truncatedRag,
            graphifySection = truncatedGraphify,
            config = config
        )
    }

    private fun collectEagerForBorough(boroughName: String): String {
        val sb = StringBuilder()
        val agentsDir = workspaceRoot.resolve(".agents")
        if (!agentsDir.isDirectory) return ""

        for (eagerFile in listOf("INDEX.adoc")) {
            val file = agentsDir.resolve(eagerFile)
            if (!file.isFile) continue
            try {
                val content = file.readText().take(2000)
                sb.appendLine("--- $boroughName/$eagerFile ---")
                sb.appendLine(content)
                sb.appendLine()
            } catch (e: Exception) {
                log.warn("Cannot read ${file.absolutePath}: {}", e.message)
            }
        }

        val promptReprise = workspaceRoot.resolve("PROMPT_REPRISE.adoc")
        if (promptReprise.isFile) {
            try {
                val content = promptReprise.readText().take(2000)
                sb.appendLine("--- $boroughName/PROMPT_REPRISE.adoc ---")
                sb.appendLine(content)
                sb.appendLine()
            } catch (e: Exception) {
                log.warn("Cannot read ${promptReprise.absolutePath}: {}", e.message)
            }
        }
        return sb.toString()
    }

    private fun collectEagerFiles(): String {
        val sb = StringBuilder()
        val ossDir = workspaceRoot.resolve("foundry/public")
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
