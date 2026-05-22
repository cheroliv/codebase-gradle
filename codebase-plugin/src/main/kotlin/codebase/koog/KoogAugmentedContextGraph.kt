package codebase.koog

import cccp.vibecoding.contracts.context.CompositeContext
import cccp.vibecoding.contracts.context.CompositeContextConfig
import cccp.vibecoding.contracts.state.AugmentedState
import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import codebase.rag.CompositeContextBuilder
import codebase.rag.EmbeddingPipeline
import codebase.rag.PgVectorConfig
import codex.store.CodexVectorStore
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

    /**
     * Graphe koog déclaratif — utilisé pour la visualisation Mermaid et l'intégration future
     * avec `AIAgent` (sans LLM, `NoLLMProvider`). Chaque nœud délègue à une méthode privée
     * de cette classe, garantissant que la logique métier n'est pas dupliquée.
     *
     * L-3 : le graphe et `execute()` partagent les mêmes lambdas — pas de duplication.
     */
    val graph: AIAgentGraphStrategy<AugmentedState, AugmentedState> = strategy<AugmentedState, AugmentedState>(
        name = "augmented-planning",
        toolSelectionStrategy = ToolSelectionStrategy.NONE
    ) {
        val buildContext by node<AugmentedState, AugmentedState> { state ->
            buildContextNode(state)
        }

        val classify by node<AugmentedState, AugmentedState> { state ->
            classifyNode(state)
        }

        val plan by node<AugmentedState, AugmentedState> { state ->
            planNode(state)
        }

        edge(nodeStart forwardTo buildContext onCondition { _ -> true } transformed { it })
        edge(buildContext forwardTo classify onCondition { _ -> true } transformed { it })
        edge(classify forwardTo plan onCondition { _ -> true } transformed { it })
        edge(plan forwardTo nodeFinish onCondition { _ -> true } transformed { it })
    }

    /**
     * Point d'entrée principal — pipeline complet : buildContext → classify → plan.
     *
     * Résilient : chaque étape catch ses erreurs. pgvector down → contexte null, pas de crash.
     * La classification utilise une heuristique simple (pas d'appel LLM supplémentaire).
     *
     * L-3 : n'appelle plus `processState()` manuel. Chaque étape délègue à la même méthode
     * privée que le nœud du graphe koog — zéro duplication.
     */
    fun execute(initialState: AugmentedState): AugmentedState {
        // Étape 1 : buildContext
        var state = try {
            buildContextNode(initialState)
        } catch (e: Exception) {
            log.warn("[KoogAugmentedContextGraph] buildContext failed (pgvector down?): {}", e.message)
            initialState.copy(compositeContext = null, error = "BuildContextFailed: ${e.message}")
        }

        // Étape 2 : classify
        state = try {
            classifyNode(state)
        } catch (e: Exception) {
            log.warn("[KoogAugmentedContextGraph] classify failed: {}", e.message)
            state.copy(classification = "simple", error = state.error ?: "ClassifyFailed: ${e.message}")
        }

        // Étape 3 : plan
        state = try {
            planNode(state)
        } catch (e: Exception) {
            log.error("[KoogAugmentedContextGraph] plan failed: {}", e.message)
            state.copy(
                planError = "PlanExecutionFailed: ${e.message}",
                error = "PlanExecutionFailed: ${e.message}"
            )
        }

        return state
    }

    fun asMermaidDiagram(): String = runBlocking { graph.asMermaidDiagram() }

    // === Méthodes privées — partagées entre le graphe koog et execute() ===

    /**
     * Nœud 1 : buildContext — RAG/pgvector via langchain4j.
     * Appelé par le graphe koog ET par execute() — pas de duplication.
     */
    private fun buildContextNode(state: AugmentedState): AugmentedState {
        val context = buildCompositeContext(state.workspaceRoot, state.intention)
        return state.copy(compositeContext = context)
    }

    /**
     * Nœud 2 : classify — heuristique simple sans appel LLM.
     * Appelé par le graphe koog ET par execute() — pas de duplication.
     */
    private fun classifyNode(state: AugmentedState): AugmentedState {
        val classification = if (state.compositeContext != null) {
            classifyIntention(state.intention)
        } else {
            "simple"
        }
        return state.copy(classification = classification)
    }

    /**
     * Nœud 3 : plan — décomposition en EPICs via IntentionPlanner (planner-gradle).
     * Appelé par le graphe koog ET par execute() — pas de duplication.
     */
    private fun planNode(state: AugmentedState): AugmentedState {
        val ctx = state.compositeContext
        return if (ctx == null) {
            state.copy(
                planError = "CompositeContext unavailable — cannot generate plan",
                error = "ContextBuildFailed"
            )
        } else {
            val planState = codebase.rag.PlannerIntegration.plan(state.intention, ctx)
            state.copy(
                planJson = planState.planJson,
                plan = planState.plan,
                planError = planState.error,
                error = planState.error
            )
        }
    }

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
        val codexStore = CodexVectorStore()
        val builder = CompositeContextBuilder(rootDir, store, pipeline, config, codexStore)
        return builder.build(question)
    }

    /**
     * Heuristique de classification simple (sans appel LLM).
     *
     * Pourquoi pas de LLM ici : KoogPlanningGraph.execute() appelle déjà flashModel.chat()
     * pour sa propre classification. On évite le double appel.
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
