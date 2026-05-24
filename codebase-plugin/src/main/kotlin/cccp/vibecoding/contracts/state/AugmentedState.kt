package cccp.vibecoding.contracts.state

import education.cccp.contracts.context.CompositeContext
import cccp.vibecoding.contracts.plan.Plan

/**
 * État augmenté pour le graphe koog+langchain4j.
 * Combine le contexte composite (RAG/pgvector/EAGER/Graphify) et le plan de décomposition.
 * Extraite de codebase.koog dans N0 pour partage N1→N2 sans violation DAG.
 */
data class AugmentedState(
    val intention: String,
    val workspaceRoot: String,
    val compositeContext: CompositeContext? = null,
    val classification: String = "",
    val planJson: String = "",
    val plan: Plan? = null,
    val planError: String? = null,
    val error: String? = null
)
