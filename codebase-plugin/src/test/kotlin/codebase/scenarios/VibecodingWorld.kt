package codebase.scenarios

import codebase.koog.llm.FakeLlmProvider
import codebase.koog.llm.LlmProvider
import codebase.koog.tracking.TokenTracker
import contracts.vibecoding.registry.ToolRegistry
import vibecoding.contracts.state.VibecodingState
import codebase.koog.VibecodingGraph

/**
 * World Object injecté par PicoContainer dans toutes les Steps Cucumber
 * de type vibecoding.
 *
 * Pattern aligné sur AugmentedPlanningWorld :
 * - Injection par constructeur dans les Steps
 * - État mutable partagé entre les scénarios
 * - PicoContainer crée une nouvelle instance par scénario → pas besoin de reset()
 * - VibecodingGraph sans graphe augmenté (pas de pgvector/Ollama)
 */
class VibecodingWorld {

    var intention: String = ""
    var maxActions: Int = 10
    var dryRun: Boolean = false
    var sessionTimeoutSeconds: Int = 300
    var resultState: VibecodingState? = null
    var mermaidDiagram: String = ""
    var securityException: SecurityException? = null

    /** V-5 : fake LLM provider pour les tests (null = mode déterministe) */
    var fakeLlmProvider: FakeLlmProvider? = null
    var tokenTracker: TokenTracker = TokenTracker()

    var graph: VibecodingGraph = VibecodingGraph(
        augmentedGraph = null,
        toolRegistry = ToolRegistry()
    )

    /**
     * Réinitialise le graphe avec le FakeLlmProvider après configuration.
     * Appelé dans le Background @epic_v_5.
     */
    fun initGraphWithLLM() {
        val provider = fakeLlmProvider ?: FakeLlmProvider()
        fakeLlmProvider = provider
        tokenTracker = TokenTracker()
        graph = VibecodingGraph(
            augmentedGraph = null,
            toolRegistry = ToolRegistry(),
            llmProvider = provider,
            tokenTracker = tokenTracker
        )
    }
}
