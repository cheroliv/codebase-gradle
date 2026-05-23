package codebase.quality

data class QualityAssessment(
    val overallVerdict: QualityVerdict,
    val results: List<QualityCheckResult>,
    val domain: Domain
) {
    val passed: Boolean get() = overallVerdict == QualityVerdict.PASS

    val failingChecks: List<QualityCheckResult> get() = results.filter { it.verdict != QualityVerdict.PASS }

    val failingCheckerNames: List<String> get() = failingChecks.map { it.checkerName }
}
