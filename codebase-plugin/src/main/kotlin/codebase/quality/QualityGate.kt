package codebase.quality

class QualityGate(
    val sentimentAnalyzer: QualityChecker = DeterministicSentimentAnalyzer(),
    val offTopicDetector: DeterministicOffTopicDetector = DeterministicOffTopicDetector(),
    val piiDetector: QualityChecker = DeterministicPiiResidualDetector(),
    val config: QualityGateConfig = QualityGateConfig()
) {

    fun evaluate(output: String, domain: Domain): QualityAssessment {
        val results = mutableListOf<QualityCheckResult>()

        if (config.enableSentimentCheck) {
            results.add(sentimentAnalyzer.check(output, config))
        }
        if (config.enableOffTopicCheck) {
            results.add(offTopicDetector.check(output, domain, config))
        }
        if (config.enablePiiCheck) {
            results.add(piiDetector.check(output, config))
        }

        val verdicts = results.map { it.verdict }
        val overall = if (verdicts.isEmpty()) QualityVerdict.PASS else QualityVerdict.worst(verdicts)

        return QualityAssessment(
            overallVerdict = overall,
            results = results,
            domain = domain
        )
    }

    fun buildFeedback(assessment: QualityAssessment): String {
        if (assessment.passed) return ""

        val sb = StringBuilder()
        sb.appendLine("=== QUALITY_GATE_FEEDBACK ===")

        for (result in assessment.failingChecks) {
            sb.appendLine("- [${result.checkerName}] ${result.verdict}: ${result.details}")
            for (issue in result.issues) {
                sb.appendLine("  - ${issue.category}: ${issue.description}")
            }
        }

        val prefix = config.retryFeedbackPrefix
        if (prefix.isNotBlank()) {
            sb.insert(0, "$prefix\n")
        }

        return sb.toString().trimEnd()
    }
}
