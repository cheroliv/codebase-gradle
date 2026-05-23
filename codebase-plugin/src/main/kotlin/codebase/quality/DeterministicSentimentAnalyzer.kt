package codebase.quality

class DeterministicSentimentAnalyzer : QualityChecker {

    override fun check(output: String, config: QualityGateConfig): QualityCheckResult {
        if (output.isBlank()) {
            return QualityCheckResult(
                checkerName = "sentiment",
                verdict = QualityVerdict.PASS,
                score = 1.0,
                details = "sentiment check skipped (empty output)"
            )
        }

        val lower = output.lowercase()
        val positiveScore = countPositive(lower)
        val negativeScore = countNegative(lower)
        val total = output.length.coerceAtLeast(1)

        val sentimentRatio = (positiveScore.toDouble() / total).coerceIn(0.0, 1.0)
        val negativeRatio = (negativeScore.toDouble() / total).coerceIn(0.0, 1.0)
        val score = ((sentimentRatio * 2 + (1.0 - negativeRatio) * 3) / 5).coerceIn(0.0, 1.0)

        val (verdict, details) = when {
            score >= config.minAcceptableScore -> QualityVerdict.PASS to "sentiment positif (${"%.2f".format(score)})"
            score >= config.minAcceptableScore * 0.5 -> QualityVerdict.NEEDS_FIX to "sentiment mitigé (${"%.2f".format(score)})"
            else -> QualityVerdict.FAIL to "sentiment négatif (${"%.2f".format(score)})"
        }

        return QualityCheckResult(
            checkerName = "sentiment",
            verdict = verdict,
            score = score,
            details = details
        )
    }

    private fun countPositive(text: String): Int =
        positiveKeywords.sumOf { kw -> kw.toRegex().findAll(text).count() * kw.length }

    private fun countNegative(text: String): Int =
        negativeKeywords.sumOf { kw -> kw.toRegex().findAll(text).count() * kw.length }

    companion object {
        private val positiveKeywords = listOf(
            "excellent", "parfait", "génial", "superbe", "magnifique",
            "merveilleux", "formidable", "très bien", "bon ", "bonne ",
            "réussi", "succès", "bravo", "correct", "acceptable",
            "satisfaisant", "conforme", "valide"
        )

        private val negativeKeywords = listOf(
            "mauvais", "grave", "échec", "nul", "horrible", "désastre",
            "inutile", "catastrophique", "terrible", "déteste",
            "erreur", "incorrect", "invalide", "insuffisant",
            "déplorable", "lamentable"
        )
    }
}
