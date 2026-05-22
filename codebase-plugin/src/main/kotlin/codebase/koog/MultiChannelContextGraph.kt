package codebase.koog

import cccp.vibecoding.contracts.context.ChannelBudget
import cccp.vibecoding.contracts.context.CompositeContextConfig
import cccp.vibecoding.contracts.context.ContextChannel
import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import codebase.rag.EmbeddingPipeline
import codebase.rag.OpencodeInjector
import codebase.rag.PgVectorConfig
import codebase.rag.VectorQueryService
import codebase.rag.VectorStore
import codex.store.CodexVectorStore
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Locale

data class MultiChannelState(
    val intention: String,
    val workspaceRoot: String,
    val budget: ChannelBudget = ChannelBudget(),
    val channels: List<ContextChannel> = emptyList(),
    val assembledContext: String = "",
    val error: String? = null
) {
    val eager: ContextChannel? get() = channels.find { it is ContextChannel.Eager }
    val rag: ContextChannel? get() = channels.find { it is ContextChannel.Rag }
    val graphify: ContextChannel? get() = channels.find { it is ContextChannel.Graphify }
    val resource: ContextChannel? get() = channels.find { it is ContextChannel.Resource }
    val docs: ContextChannel? get() = channels.find { it is ContextChannel.Docs }
}

class MultiChannelContextGraph {

    private val log = LoggerFactory.getLogger(MultiChannelContextGraph::class.java)

    val graph: AIAgentGraphStrategy<MultiChannelState, MultiChannelState> = strategy<MultiChannelState, MultiChannelState>(
        name = "multi-channel-context",
        toolSelectionStrategy = ToolSelectionStrategy.NONE
    ) {
        val collectEager by node<MultiChannelState, MultiChannelState> { state ->
            collectEagerNode(state)
        }

        val collectRag by node<MultiChannelState, MultiChannelState> { state ->
            collectRagNode(state)
        }

        val collectGraphify by node<MultiChannelState, MultiChannelState> { state ->
            collectGraphifyNode(state)
        }

        val collectDocs by node<MultiChannelState, MultiChannelState> { state ->
            collectDocsNode(state)
        }

        val assemble by node<MultiChannelState, MultiChannelState> { state ->
            assembleNode(state)
        }

        edge(nodeStart forwardTo collectEager onCondition { _ -> true } transformed { it })
        edge(collectEager forwardTo collectRag onCondition { _ -> true } transformed { it })
        edge(collectRag forwardTo collectGraphify onCondition { _ -> true } transformed { it })
        edge(collectGraphify forwardTo collectDocs onCondition { _ -> true } transformed { it })
        edge(collectDocs forwardTo assemble onCondition { _ -> true } transformed { it })
        edge(assemble forwardTo nodeFinish onCondition { _ -> true } transformed { it })
    }

    fun execute(initialState: MultiChannelState): MultiChannelState {
        var state = safeStep(initialState) { collectEagerNode(it) }

        state = safeStep(state) { collectRagNode(it) }

        state = safeStep(state) { collectGraphifyNode(it) }

        state = safeStep(state) { collectDocsNode(it) }

        state = safeStep(state) { assembleNode(it) }

        log.info("MultiChannelContextGraph: assembled {} channels -> {} chars (budget={} tokens)",
            state.channels.count { it.content.isNotEmpty() },
            state.assembledContext.length,
            ContextChannel.DEFAULT_TOKEN_BUDGET)

        return state
    }

    fun asMermaidDiagram(): String = runBlocking { graph.asMermaidDiagram() }

    private fun safeStep(state: MultiChannelState, step: (MultiChannelState) -> MultiChannelState): MultiChannelState {
        return try {
            step(state)
        } catch (e: Exception) {
            log.warn("[MultiChannelContextGraph] step failed (resilient): {}", e.message)
            state.copy(error = state.error ?: "StepFailed: ${e.message}")
        }
    }

    private fun collectEagerNode(state: MultiChannelState): MultiChannelState {
        val rootDir = File(state.workspaceRoot)
        val content = collectEagerFiles(rootDir)
        val channel = ContextChannel.Eager(content).truncateToTokens(state.budget.eagerTokens)
        return state.copy(channels = listOf(channel))
    }

    private fun collectRagNode(state: MultiChannelState): MultiChannelState {
        val rootDir = File(state.workspaceRoot)
        val content = try {
            val cfg = PgVectorConfig.fromEnv()
            val store = cfg.toVectorStore()
            store.initSchema()
            val embeddingPipeline = EmbeddingPipeline(store)
            val queryService = VectorQueryService(store, embeddingPipeline)
            val ragResults = queryService.query(state.intention, topK = 10)
            ragResults.joinToString("\n") { r ->
                "[sim=${"%.3f".format(Locale.US, r.similarity)}] ${r.text.take(300)}"
            }
        } catch (e: Exception) {
            log.warn("[collectRag] pgvector unavailable: {}", e.message)
            "[RAG] pgvector indisponible — section non generee"
        }
        val channel = ContextChannel.Rag(content).truncateToTokens(state.budget.ragTokens)
        return state.copy(channels = state.channels + channel)
    }

    private fun collectGraphifyNode(state: MultiChannelState): MultiChannelState {
        val rootDir = File(state.workspaceRoot)
        val content = loadGraphifyStats(rootDir)
        val channel = ContextChannel.Graphify(content).truncateToTokens(state.budget.graphifyTokens)
        return state.copy(channels = state.channels + channel)
    }

    private fun collectDocsNode(state: MultiChannelState): MultiChannelState {
        val content = try {
            val codexStore = CodexVectorStore()
            val results = codexStore.searchBlocking(state.intention, topK = 5)
            if (results.isEmpty()) "[Docs] Aucun resultat dans le corpus codex"
            else results.joinToString("\n\n") { r ->
                "[Docs] source=${r.sourceDocument} section=${r.sectionPath} sim=${"%.3f".format(Locale.US, r.similarity)}\n${r.chunkText.take(500)}"
            }
        } catch (e: Exception) {
            log.warn("[collectDocs] codex store unavailable: {}", e.message)
            "[Docs] CodexVectorStore indisponible — ${e.message}"
        }
        val channel = ContextChannel.Docs(content).truncateToTokens(state.budget.docsTokens)
        return state.copy(channels = state.channels + channel)
    }

    private fun assembleNode(state: MultiChannelState): MultiChannelState {
        val resourceChannel = ContextChannel.Resource("").truncateToTokens(state.budget.resourceTokens)
        val allChannels = state.channels + resourceChannel
        val injector = OpencodeInjector()
        val assembled = injector.injectChannels(allChannels)
        return state.copy(channels = allChannels, assembledContext = assembled)
    }

    private fun collectEagerFiles(rootDir: File): String {
        val sb = StringBuilder()
        val ossDir = rootDir.resolve("foundry/public")
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

    private fun loadGraphifyStats(rootDir: File): String {
        val graphFile = rootDir.resolve("office/graph.json")
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
}
