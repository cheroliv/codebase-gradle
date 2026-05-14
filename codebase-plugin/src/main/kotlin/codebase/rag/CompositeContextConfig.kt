package codebase.rag

data class CompositeContextConfig(
    val totalTokenBudget: Int = 8000,
    val budgetEagerLazy: Double = 0.40,
    val budgetRag: Double = 0.30,
    val budgetGraphify: Double = 0.20,
    val budgetOverhead: Double = 0.10
) {
    init {
        val sum = budgetEagerLazy + budgetRag + budgetGraphify + budgetOverhead
        require(sum == 1.0) { "Token budget must sum to 1.0, got $sum" }
    }

    val eagerLazyTokens: Int get() = (totalTokenBudget * budgetEagerLazy).toInt()
    val ragTokens: Int get() = (totalTokenBudget * budgetRag).toInt()
    val graphifyTokens: Int get() = (totalTokenBudget * budgetGraphify).toInt()
    val overheadTokens: Int get() = (totalTokenBudget * budgetOverhead).toInt()
}
