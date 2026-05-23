package codebase.quality

data class QualityCheckResult(
    val checkerName: String,
    val verdict: QualityVerdict,
    val score: Double,
    val details: String,
    val issues: List<QualityIssue> = emptyList()
) {
    init {
        require(checkerName.isNotBlank()) { "checkerName must not be blank" }
        require(score in 0.0..1.0) { "score must be in [0.0, 1.0], got $score" }
        require(details.isNotBlank()) { "details must not be blank" }
    }

    override fun toString(): String = buildString {
        append("[$checkerName] ${verdict.name} (score=$score)")
        if (issues.isNotEmpty()) {
            append(" — ${issues.size} issue(s): ${issues.joinToString("; ")}")
        }
    }
}
