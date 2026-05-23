package codebase.quality

data class QualityIssue(
    val category: String,
    val description: String,
    val confidence: Double
) {
    init {
        require(category.isNotBlank()) { "category must not be blank" }
        require(description.isNotBlank()) { "description must not be blank" }
        require(confidence in 0.0..1.0) { "confidence must be in [0.0, 1.0], got $confidence" }
    }

    override fun toString(): String = "[$category] $description (${"%.2f".format(confidence)})"
}
