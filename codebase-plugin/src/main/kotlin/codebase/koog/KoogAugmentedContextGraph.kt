package codebase.koog

import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import codebase.rag.CompositeContext
import codebase.rag.CompositeContextBuilder
import codebase.rag.CompositeContextConfig
import codebase.rag.EmbeddingPipeline
import codebase.rag.PgVectorConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Graphe koog augmenté — intègre le pipeline RAG/pgvector (langchain4j)
 * directement dans l'orchestrateur koog.
 *
 * Architecture L-2 (wrapper koog+langchain4j) :
 * ```
 * [intention] → buildContext (pgvector/RAG) → classify → plan (flash|pro) → [plan]
 * ```
 *
 * - **buildContext** : appelle CompositeContextBuilder via langchain4j (pgvector + ONNX embeddings)
 * - **classify** : classifie simple vs complexe via heuristique (sans LLM, pour éviter double appel)
 * - **plan** : décompose en EPICs via KoogPlanningGraph (flash ou pro)
 *
 * Ce wrapper remplace le pré-traitement externe de PlanIntentionTask :
 * le context building devient un nœud du graphe koog, pas un step hors-graphe.
 *
 * Note : koog ne supporte pas pgvector nativement (doc officielle : "production vector database integrations
 * are not provided in the current rag module"). Le pont vers langchain4j est donc conservé
 * pour le RAG, les embeddings ONNX et le VectorStore — koog orchestre, langchain4j exécute.
 */
class KoogAugmentedContextGraph {

    private val log = LoggerFactory.getLogger(KoogAugmentedContextGraph::class.java)
    private val planningGraph = KoogPlanningGraph()

    val graph: AIAgentGraphStrategy<AugmentedState, AugmentedState> = strategy<AugmentedState, AugmentedState>(
        name = "augmented-planning",
        toolSelectionStrategy = ToolSelectionStrategy.NONE
    ) {
        val buildContext by node<AugmentedState, AugmentedState> { state ->
            val context = buildCompositeContext(state.workspaceRoot, state.intention)
            state.copy(compositeContext = context)
        }

        val classify by node<AugmentedState, AugmentedState> { state ->
            val classification = if (state.compositeContext != null) {
                classifyIntention(state.intention)
            } else {
                "simple"
            }
            state.copy(classification = classification)
        }

        val plan by node<AugmentedState, AugmentedState> { state ->
            val ctx = state.compositeContext
            if (ctx == null) {
                state.copy(
                    planError = "CompositeContext unavailable — cannot generate plan",
                    error = "ContextBuildFailed"
                )
            } else {
                val planState = planningGraph.execute(state.intention, ctx)
                state.copy(
                    planJson = planState.planJson,
                    plan = planState.plan,
                    planError = planState.error,
                    error = planState.error
                )
            }
        }

        edge(nodeStart forwardTo buildContext onCondition { _ -> true } transformed { it })
        edge(buildContext forwardTo classify onCondition { _ -> true } transformed { it })
        edge(classify forwardTo plan onCondition { _ -> true } transformed { it })
        edge(plan forwardTo nodeFinish onCondition { _ -> true } transformed { it })
    }

    /**
     * Point d'entrée principal — exécute le pipeline complet.
     *
     * Résilient : chaque étape catch ses erreurs. pgvector down → contexte null, pas de crash.
     * La classification utilise une heuristique simple (pas d'appel LLM supplémentaire).
     */
    fun processState(initialState: AugmentedState): AugmentedState {
        var state = initialState

        // Étape 1 : buildContext (RAG/pgvector via langchain4j)
        state = try {
            val context = buildCompositeContext(state.workspaceRoot, state.intention)
            state.copy(compositeContext = context)
        } catch (e: Exception) {
            log.warn("[KoogAugmentedContextGraph] buildContext failed (pgvector down?): {}", e.message)
            state.copy(compositeContext = null, error = "BuildContextFailed: ${e.message}")
        }

        // Étape 2 : classify (heuristique, pas d'appel LLM)
        state = state.copy(
            classification = if (state.compositeContext != null) {
                classifyIntention(state.intention)
            } else {
                "simple"
            }
        )

        // Étape 3 : plan (KoogPlanningGraph)
        state = if (state.compositeContext != null) {
            try {
                val planState = planningGraph.execute(state.intention, state.compositeContext)
                state.copy(
                    planJson = planState.planJson,
                    plan = planState.plan,
                    planError = planState.error,
                    error = planState.error
                )
            } catch (e: Exception) {
                log.error("[KoogAugmentedContextGraph] plan failed: {}", e.message)
                state.copy(
                    planError = "PlanExecutionFailed: ${e.message}",
                    error = "PlanExecutionFailed: ${e.message}"
                )
            }
        } else {
            state.copy(
                planError = "CompositeContext is null — cannot generate plan",
                error = "ContextUnavailable"
            )
        }

        return state
    }

    fun asMermaidDiagram(): String = runBlocking { graph.asMermaidDiagram() }

    // === Méthodes privées — pont koog → langchain4j (RAG/pgvector) ===

    private fun buildCompositeContext(workspaceRoot: String, question: String): CompositeContext {
        val rootDir = File(workspaceRoot)
        val config = CompositeContextConfig()
        val cfg = PgVectorConfig.fromEnv()
        val store = cfg.toVectorStore()

        try {
            store.initSchema()
        } catch (e: Exception) {
            log.warn("[buildCompositeContext] pgvector schema init failed: {}", e.message)
        }

        val pipeline = EmbeddingPipeline(store)
        val builder = CompositeContextBuilder(rootDir, store, pipeline, config)
        return builder.build(question)
    }

    /**
     * Heuristique de classification simple (sans appel LLM).
     *
     * Pourquoi pas de LLM ici : KoogPlanningGraph.execute() appelle déjà flashModel.chat()
     * pour sa propre classification. On évite le double appel.
     *
     * Cette heuristique suffit pour L-2 — un classifieur LLM dédié sera en L-3.
     */
    private fun classifyIntention(intention: String): String {
        return if (intention.length > 80 ||
            intention.contains("cross-borough", ignoreCase = true) ||
            intention.contains("multi-plugins", ignoreCase = true) ||
            intention.contains("architecture", ignoreCase = true)
        ) {
            "complexe"
        } else {
            "simple"
        }
    }
}
