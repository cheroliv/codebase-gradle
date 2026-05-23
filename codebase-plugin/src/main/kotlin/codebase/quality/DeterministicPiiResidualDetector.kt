package codebase.quality

class DeterministicPiiResidualDetector : QualityChecker {

    override fun check(output: String, config: QualityGateConfig): QualityCheckResult {
        if (output.isBlank()) {
            return QualityCheckResult(
                checkerName = "pii-residual",
                verdict = QualityVerdict.PASS,
                score = 1.0,
                details = "pii-residual check skipped (empty output)"
            )
        }

        val issues = mutableListOf<QualityIssue>()

        for (pattern in patterns) {
            val matches = pattern.regex.findAll(output).toList()
            for (match in matches) {
                if (!isMasked(match.value, output)) {
                    issues.add(
                        QualityIssue(
                            category = pattern.category,
                            description = "${pattern.description}: ${maskValue(match.value)}",
                            confidence = pattern.confidence
                        )
                    )
                }
            }
        }

        val score = if (issues.isEmpty()) 1.0
        else (1.0 / (1 + issues.size)).coerceIn(0.0, 1.0)

        val (verdict, details) = when {
            issues.isEmpty() -> QualityVerdict.PASS to "no PII detected"
            else -> QualityVerdict.FAIL to "${issues.size} PII issue(s) detected"
        }

        return QualityCheckResult(
            checkerName = "pii-residual",
            verdict = verdict,
            score = score,
            details = details,
            issues = issues
        )
    }

    private fun isMasked(match: String, fullText: String): Boolean {
        val before = fullText.substring(0, fullText.indexOf(match).coerceAtLeast(0)).lowercase()
        if (before.endsWith("***") || before.endsWith("anonymous@acme.com") || before.contains("***")) return true

        val isMaskedValue = match == "***" ||
            match == "anonymous@acme.com" ||
            match.startsWith("***") ||
            match == "**" ||
            match == "****"

        return isMaskedValue
    }

    private fun maskValue(value: String): String {
        return when {
            value.length <= 4 -> "***"
            else -> value.take(3) + "***"
        }
    }

    private data class PiiPattern(
        val regex: Regex,
        val category: String,
        val description: String,
        val confidence: Double
    )

    companion object {
        private val patterns = listOf(
            PiiPattern(
                Regex("""gh[pousr]_[A-Za-z0-9_]{20,}"""),
                "PII_TOKEN", "GitHub token", 0.99
            ),
            PiiPattern(
                Regex("""sk-(?:proj-)?[A-Za-z0-9_\-]{20,}"""),
                "PII_TOKEN", "OpenAI/API key", 0.99
            ),
            PiiPattern(
                Regex("""AKIA[0-9A-Z]{16}"""),
                "PII_TOKEN", "AWS access key", 0.99
            ),
            PiiPattern(
                Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""),
                "PII_EMAIL", "email address", 0.98
            ),
            PiiPattern(
                Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b"""),
                "PII_IP", "IP address", 0.95
            ),
            PiiPattern(
                Regex("""\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b"""),
                "PII_CREDIT_CARD", "credit card number", 0.99
            ),
            PiiPattern(
                Regex("""(?:\+33[-\s]?|0)[1-9](?:[-\s]?\d{2}){4}"""),
                "PII_PHONE", "phone number", 0.90
            ),
            PiiPattern(
                Regex("""(?i)password\s*[=:]\s*\S+"""),
                "PII_PASSWORD", "password in context", 0.99
            ),
        )
    }
}
