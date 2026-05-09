package codebase.rag

data class AnonymizationRequest(
    val sourcePath: String,
    val content: String,
    val targetFormat: String
)

data class AnonymizationResult(
    val anonymizedContent: String,
    val confidenceScore: Double,
    val detectedPiiCategories: List<String>,
    val replacedCount: Int,
    val summary: String
)

data class PiiCategory(
    val name: String,
    val level: Int,
    val examples: List<String>
)

enum class SensitivityLevel(val level: Int, val label: String) {
    PUBLIC(0, "niveau 0 — public"),
    INTERNAL(1, "niveau 1 — interne"),
    CONFIDENTIAL(2, "niveau 2 — confidentiel"),
    RESTRICTED(3, "niveau 3 — restreint"),
    SECRET(4, "niveau 4 — secret RGPD");

    companion object {
        fun fromLevel(level: Int): SensitivityLevel =
            entries.firstOrNull { it.level == level } ?: PUBLIC
    }
}
