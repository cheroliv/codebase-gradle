package codebase.quality

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityCheckResultTest {

    @Test
    fun `construct PASS result with confidence`() {
        val result = QualityCheckResult(
            checkerName = "sentiment",
            verdict = QualityVerdict.PASS,
            score = 0.95,
            details = "Positive sentiment detected"
        )
        assertEquals("sentiment", result.checkerName)
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertEquals(0.95, result.score)
        assertEquals("Positive sentiment detected", result.details)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `construct FAIL result with issues`() {
        val issues = listOf(
            QualityIssue("PII_EMAIL", "Found email: user@example.com", 0.98),
            QualityIssue("PII_TOKEN", "Found API token pattern", 0.99)
        )
        val result = QualityCheckResult(
            checkerName = "pii-residual",
            verdict = QualityVerdict.FAIL,
            score = 0.12,
            details = "2 PII issues detected",
            issues = issues
        )
        assertEquals("pii-residual", result.checkerName)
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertEquals(0.12, result.score)
        assertEquals(2, result.issues.size)
    }

    @Test
    fun `result with empty issues defaults to empty list`() {
        val result = QualityCheckResult(
            checkerName = "off-topic",
            verdict = QualityVerdict.PASS,
            score = 0.88,
            details = "On topic"
        )
        assertTrue(result.issues.isEmpty())
    }
}
