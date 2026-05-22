package codebase.scenarios

import cccp.vibecoding.contracts.registry.ToolRegistry
import cccp.vibecoding.contracts.state.VibecodingState
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

    val graph: VibecodingGraph by lazy {
        VibecodingGraph(
            augmentedGraph = null,
            toolRegistry = ToolRegistry()
        )
    }
}
