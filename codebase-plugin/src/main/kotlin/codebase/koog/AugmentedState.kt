package codebase.koog

import codebase.rag.CompositeContext

/**
 * État augmenté pour le graphe koog+langchain4j.
 * Combine le contexte composite (RAG/pgvector/EAGER/Graphify) et le plan de décomposition.
 *
 * L-2 : wrapper koog autour du pipeline langchain4j de contexte augmenté.
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
