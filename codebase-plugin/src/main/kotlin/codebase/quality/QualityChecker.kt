package codebase.quality

fun interface QualityChecker {
    fun check(output: String, config: QualityGateConfig): QualityCheckResult
}
