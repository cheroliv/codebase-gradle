package codebase.quality

data class QualityGateConfig(
    val minAcceptableScore: Double = 0.60,
    val maxRetries: Int = 3,
    val enableSentimentCheck: Boolean = true,
    val enableOffTopicCheck: Boolean = true,
    val enablePiiCheck: Boolean = true,
    val retryFeedbackPrefix: String = "Tu as été rappelé à l'ordre pour hors-sujet. Concentre-toi."
) {
    init {
        require(minAcceptableScore in 0.0..1.0) { "minAcceptableScore must be in [0.0, 1.0], got $minAcceptableScore" }
        require(maxRetries > 0) { "maxRetries must be positive, got $maxRetries" }
    }
}
