package codebase.quality

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicSentimentAnalyzerTest {

    private val analyzer = DeterministicSentimentAnalyzer()

    @Test
    fun `positive french text scores HIGH and PASS`() {
        val result = analyzer.check("C'est un excellent résultat, très bien fait.", QualityGateConfig())
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertTrue(result.score >= 0.70)
        assertEquals("sentiment", result.checkerName)
    }

    @Test
    fun `negative french text scores LOW and FAIL`() {
        val result = analyzer.check("C'est mauvais, une erreur grave, un échec total.", QualityGateConfig())
        assertEquals(QualityVerdict.NEEDS_FIX, result.verdict)
        assertTrue(result.score < 0.60)
    }

    @Test
    fun `neutral text scores medium and PASS`() {
        val result = analyzer.check("Le fichier contient 42 lignes de code Kotlin.", QualityGateConfig())
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertTrue(result.score >= 0.50)
    }

    @Test
    fun `empty text returns PASS with score 1`() {
        val result = analyzer.check("", QualityGateConfig())
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertEquals(1.0, result.score)
    }

    @Test
    fun `blank text returns PASS with score 1`() {
        val result = analyzer.check("   ", QualityGateConfig())
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertEquals(1.0, result.score)
    }

    @Test
    fun `very negative text with insults scores very low`() {
        val result = analyzer.check(
            "C'est nul, horrible, inutile, un désastre. Je déteste ça.",
            QualityGateConfig()
        )
        assertTrue(result.score < 0.30)
        assertEquals(QualityVerdict.FAIL, result.verdict)
    }

    @Test
    fun `mixed text with more positive than negative scores above 0 point 5`() {
        val result = analyzer.check(
            "Excellent travail mais il y a une petite erreur. Bon dans l'ensemble.",
            QualityGateConfig()
        )
        assertTrue(result.score >= 0.50)
    }

    @Test
    fun `respects custom minAcceptableScore for NEEDS_FIX threshold`() {
        val config = QualityGateConfig(minAcceptableScore = 0.90)
        val result = analyzer.check(
            "Bon résultat, correct, acceptable.",
            config
        )
        assertEquals(QualityVerdict.NEEDS_FIX, result.verdict)
    }

    @Test
    fun `respects custom minAcceptableScore for FAIL threshold`() {
        val config = QualityGateConfig(minAcceptableScore = 0.90)
        val result = analyzer.check(
            "C'est mauvais et incorrect.",
            config
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
    }

    @Test
    fun `details contain checker name`() {
        val result = analyzer.check("test", QualityGateConfig())
        assertTrue(result.details.contains("sentiment"))
    }

    @Test
    fun `score is always between 0 and 1`() {
        val texts = listOf(
            "Excellent, parfait, génial, superbe, magnifique, merveilleux, formidable",
            "Nul, horrible, désastreux, catastrophique, inutile, mauvais, terrible",
            "Normal."
        )
        for (text in texts) {
            val result = analyzer.check(text, QualityGateConfig())
            assertTrue(result.score in 0.0..1.0, "score ${result.score} out of range for: $text")
        }
    }
}
