package codebase.rag

import kotlin.math.abs

data class ChannelBudget(
    val totalTokenBudget: Int = 8000,
    val budgetEager: Double = 0.40,
    val budgetRag: Double = 0.30,
    val budgetGraphify: Double = 0.20,
    val budgetResource: Double = 0.10
) {
    init {
        val sum = budgetEager + budgetRag + budgetGraphify + budgetResource
        require(abs(sum - 1.0) < 0.001) { "Channel budget must sum to 1.0, got $sum" }
    }

    val eagerTokens: Int get() = (totalTokenBudget * budgetEager).toInt()
    val ragTokens: Int get() = (totalTokenBudget * budgetRag).toInt()
    val graphifyTokens: Int get() = (totalTokenBudget * budgetGraphify).toInt()
    val resourceTokens: Int get() = (totalTokenBudget * budgetResource).toInt()

    fun tokensFor(type: ChannelType): Int = when (type) {
        ChannelType.EAGER -> eagerTokens
        ChannelType.RAG -> ragTokens
        ChannelType.GRAPHIFY -> graphifyTokens
        ChannelType.RESOURCE -> resourceTokens
    }

    fun tokensFor(channel: ContextChannel): Int = tokensFor(channel.type)

    fun applyBudget(channels: List<ContextChannel>): List<ContextChannel> =
        channels.map { it.truncateToTokens(tokensFor(it.type)) }

    companion object {
        fun fromConfig(config: CompositeContextConfig): ChannelBudget = ChannelBudget(
            totalTokenBudget = config.totalTokenBudget,
            budgetEager = config.budgetEagerLazy,
            budgetRag = config.budgetRag,
            budgetGraphify = config.budgetGraphify
        )
    }
}
