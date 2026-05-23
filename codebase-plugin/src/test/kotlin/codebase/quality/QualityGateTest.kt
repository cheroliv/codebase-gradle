package codebase.quality

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityGateTest {

    private val sentinel: QualityGate = QualityGate(
        sentimentAnalyzer = DeterministicSentimentAnalyzer(),
        offTopicDetector = DeterministicOffTopicDetector(),
        piiDetector = DeterministicPiiResidualDetector(),
        config = QualityGateConfig()
    )

    @Test
    fun `clean CDA output passes all checks`() {
        val result = sentinel.evaluate(
            "class UserRepository { fun findByName(name: String): User = em.createQuery(...).singleResult }",
            Domain.CDA
        )
        assertEquals(QualityVerdict.PASS, result.overallVerdict)
        assertEquals(3, result.results.size)
        assertTrue(result.results.all { it.verdict == QualityVerdict.PASS })
        assertTrue(result.passed)
    }

    @Test
    fun `clean FPA output passes all checks`() {
        val result = sentinel.evaluate(
            "Objectif pédagogique : acquérir les compétences Qualiopi via une évaluation formative.",
            Domain.FPA
        )
        assertEquals(QualityVerdict.PASS, result.overallVerdict)
        assertTrue(result.passed)
    }

    @Test
    fun `output with PII fails overall`() {
        val result = sentinel.evaluate(
            "Contact admin@example.com, token=ghp_1234567890abcdefghijkl",
            Domain.CDA
        )
        assertEquals(QualityVerdict.FAIL, result.overallVerdict)
        assertFalse(result.passed)
        assertTrue(result.failingChecks.isNotEmpty())
    }

    @Test
    fun `output with negative sentiment combined with off-topic fails`() {
        val result = sentinel.evaluate(
            "C'est nul, horrible, un désastre. La recette du gâteau est ratée.",
            Domain.CDA
        )
        assertTrue(result.overallVerdict.severity >= QualityVerdict.NEEDS_FIX.severity)
    }

    @Test
    fun `failingChecks only contains non-PASS results`() {
        val result = sentinel.evaluate(
            "token=ghp_test1234 val x = 42 password=secret",
            Domain.CDA
        )
        assertTrue(result.failingChecks.all { it.verdict != QualityVerdict.PASS })
        assertTrue(result.failingChecks.any { it.checkerName == "pii-residual" })
    }

    @Test
    fun `empty output passes all checks`() {
        val result = sentinel.evaluate("", Domain.CDA)
        assertEquals(QualityVerdict.PASS, result.overallVerdict)
        assertTrue(result.passed)
    }

    @Test
    fun `config can disable individual checks`() {
        val config = QualityGateConfig(enableSentimentCheck = false)
        val gate = QualityGate(
            sentimentAnalyzer = DeterministicSentimentAnalyzer(),
            offTopicDetector = DeterministicOffTopicDetector(),
            piiDetector = DeterministicPiiResidualDetector(),
            config = config
        )
        val result = gate.evaluate("C'est nul et horrible", Domain.CDA)
        assertEquals(2, result.results.size)
        assertTrue(result.results.none { it.checkerName == "sentiment" })
    }

    @Test
    fun `feedback message includes failing check details`() {
        val result = sentinel.evaluate(
            "admin@talaria.school password=secret123!",
            Domain.CDA
        )
        val feedback = sentinel.buildFeedback(result)
        assertTrue(feedback.contains("QUALITY_GATE"))
        assertTrue(feedback.contains("pii"))
    }

    @Test
    fun `feedback is empty when all checks pass`() {
        val result = sentinel.evaluate("fun sum(a: Int, b: Int) = a + b", Domain.CDA)
        val feedback = sentinel.buildFeedback(result)
        assertEquals("", feedback)
    }
}
