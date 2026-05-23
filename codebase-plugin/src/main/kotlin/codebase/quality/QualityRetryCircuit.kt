package codebase.quality

data class QualityAttempt(
    val attempt: Int,
    val output: String,
    val prompt: String,
    val qualityAssessment: QualityAssessment
)

data class QualityResult(
    val bestOutput: String?,
    val attempts: Int,
    val passed: Boolean,
    val history: List<QualityAttempt>,
    val summary: String
)

class QualityRetryCircuit(
    private val llm: suspend (String) -> String,
    private val gate: QualityGate,
    private val domain: Domain,
    private val maxRetries: Int = gate.config.maxRetries
) {

    suspend fun invoke(initialPrompt: String): QualityResult {
        val history = mutableListOf<QualityAttempt>()
        var currentPrompt = initialPrompt
        var best: String? = null

        val totalAttempts = maxRetries + 1
        for (attempt in 1..totalAttempts) {
            val output = llm(currentPrompt)
            best = best ?: output

            val assessment = gate.evaluate(output, domain)
            history.add(
                QualityAttempt(
                    attempt = attempt,
                    output = output,
                    prompt = currentPrompt,
                    qualityAssessment = assessment
                )
            )

            if (assessment.passed) {
                return QualityResult(
                    bestOutput = output,
                    attempts = attempt,
                    passed = true,
                    history = history,
                    summary = "GATE: PASS — $attempt attempt(s)"
                )
            }

            if (attempt < totalAttempts) {
                val feedback = gate.buildFeedback(assessment)
                currentPrompt = "$initialPrompt\n\n$feedback"
            }
        }

        val bestAttempt = history.maxByOrNull { it.qualityAssessment.results.sumOf { r -> (r.score * 1000).toLong() } }
        val bestOutput = bestAttempt?.output ?: best

        return QualityResult(
            bestOutput = bestOutput,
            attempts = totalAttempts,
            passed = false,
            history = history,
            summary = "GATE: FAIL — $totalAttempts attempt(s)"
        )
    }
}
