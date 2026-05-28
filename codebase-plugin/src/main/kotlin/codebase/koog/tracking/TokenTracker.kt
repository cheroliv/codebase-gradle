package codebase.koog.tracking

class TokenTracker(
    private val promptTokenEstimator: (String) -> Int = TokenTracker.Companion::defaultEstimateTokens,
    private val completionTokenEstimator: (String) -> Int = TokenTracker.Companion::defaultEstimateTokens
) {
    var promptTokens: Long = 0
        private set
    var completionTokens: Long = 0
        private set
    var totalCalls: Int = 0
        private set

    fun reset() {
        promptTokens = 0
        completionTokens = 0
        totalCalls = 0
    }

    fun trackPrompt(input: String): TokenTracker {
        promptTokens += promptTokenEstimator(input)
        totalCalls++
        return this
    }

    fun <T> trackPromptAndCompletion(input: String, block: (String) -> T): T {
        promptTokens += promptTokenEstimator(input)
        val result = block(input)
        val completionText = result?.toString() ?: ""
        completionTokens += completionTokenEstimator(completionText)
        totalCalls++
        return result
    }

    fun trackCompletion(output: String) {
        completionTokens += completionTokenEstimator(output)
    }

    fun estimatedCost(model: String): Double {
        return Companion.costFor(model, promptTokens, completionTokens)
    }

    companion object {
        fun defaultEstimateTokens(text: String): Int {
            if (text.isEmpty()) return 0
            return maxOf(1, text.length / 4)
        }

        val COST_PER_MILLION: Map<String, Pair<Double, Double>> = mapOf(
            "deepseek-v4-pro" to (2.50 to 8.00),
            "deepseek-v4-flash" to (0.27 to 1.10),
            "deepseek-v3" to (0.27 to 1.10),
        )

        fun costFor(model: String, promptTokens: Long, completionTokens: Long): Double {
            // Normalise les suffixes Ollama (:cloud, :latest, etc.) vers le nom canonique
            val normalizedModel = model.substringBefore(":")
            val (promptPrice, completionPrice) = COST_PER_MILLION[normalizedModel] ?: return 0.0
            val promptCost = (promptTokens.toDouble() / 1_000_000.0) * promptPrice
            val completionCost = (completionTokens.toDouble() / 1_000_000.0) * completionPrice
            return promptCost + completionCost
        }
    }
}
