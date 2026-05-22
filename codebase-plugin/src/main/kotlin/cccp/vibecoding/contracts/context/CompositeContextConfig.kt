package cccp.vibecoding.contracts.context

/**
 * Configuration du budget token pour le contexte composite.
 * Extraite de codebase.rag dans N0 pour partage N1→N2 sans violation DAG.
 */
data class CompositeContextConfig(
    val totalTokenBudget: Int = 8000,
    val budgetEagerLazy: Double = 0.40,
    val budgetRag: Double = 0.30,
    val budgetGraphify: Double = 0.20,
    val budgetDocs: Double = 0.10,
    val budgetOverhead: Double = 0.0
) {
    init {
        val sum = budgetEagerLazy + budgetRag + budgetGraphify + budgetDocs + budgetOverhead
        require(kotlin.math.abs(sum - 1.0) < 0.001) { "Token budget must sum to 1.0, got $sum" }
    }

    val eagerLazyTokens: Int get() = (totalTokenBudget * budgetEagerLazy).toInt()
    val ragTokens: Int get() = (totalTokenBudget * budgetRag).toInt()
    val graphifyTokens: Int get() = (totalTokenBudget * budgetGraphify).toInt()
    val docsTokens: Int get() = (totalTokenBudget * budgetDocs).toInt()
    val overheadTokens: Int get() = (totalTokenBudget * budgetOverhead).toInt()
}
