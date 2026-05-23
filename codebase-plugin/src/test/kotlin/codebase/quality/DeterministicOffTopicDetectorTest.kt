package codebase.quality

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicOffTopicDetectorTest {

    private val detector = DeterministicOffTopicDetector()

    @Test
    fun `CDA output with Kotlin keywords is on topic`() {
        val result = detector.check(
            "class MainActivity : AppCompatActivity() { val list = mutableListOf<String>() }",
            Domain.CDA,
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertTrue(result.score >= 0.60)
    }

    @Test
    fun `CDA output with Gradle keywords is on topic`() {
        val result = detector.check(
            "plugins { kotlin(\"jvm\") } dependencies { implementation(\"lib\") }",
            Domain.CDA,
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertTrue(result.score >= 0.50)
    }

    @Test
    fun `FPA output with pedagogical keywords is on topic`() {
        val result = detector.check(
            "Objectif pédagogique : évaluer les compétences selon la taxonomie de Bloom.",
            Domain.FPA,
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertTrue(result.score >= 0.50)
    }

    @Test
    fun `CDA output about cuisine is off topic`() {
        val result = detector.check(
            "La recette du gâteau au chocolat nécessite 200g de farine.",
            Domain.CDA,
            QualityGateConfig()
        )
        assertTrue(result.verdict.severity >= QualityVerdict.NEEDS_FIX.severity)
        assertTrue(result.score < 0.30)
    }

    @Test
    fun `FPA output about sports is off topic`() {
        val result = detector.check(
            "Le match de football a été gagné par l'équipe locale.",
            Domain.FPA,
            QualityGateConfig()
        )
        assertTrue(result.verdict.severity >= QualityVerdict.NEEDS_FIX.severity)
        assertTrue(result.score < 0.30)
    }

    @Test
    fun `CDA output about pedagogy is off topic for CDA domain`() {
        val result = detector.check(
            "La formation professionnelle nécessite une évaluation formative continue.",
            Domain.CDA,
            QualityGateConfig()
        )
        assertTrue(result.verdict.severity >= QualityVerdict.NEEDS_FIX.severity)
    }

    @Test
    fun `FPA output about Spring Boot is off topic for FPA domain`() {
        val result = detector.check(
            "@SpringBootApplication class App { fun main() = runApplication<App>(*args) }",
            Domain.FPA,
            QualityGateConfig()
        )
        assertTrue(result.verdict.severity >= QualityVerdict.NEEDS_FIX.severity)
    }

    @Test
    fun `empty text passes for any domain`() {
        val result = detector.check("", Domain.CDA, QualityGateConfig())
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertEquals(1.0, result.score)
    }

    @Test
    fun `generic programming text is on topic for CDA`() {
        val result = detector.check(
            "fun calculateSum(a: Int, b: Int): Int = a + b",
            Domain.CDA,
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.PASS, result.verdict)
    }

    @Test
    fun `generic pedagogical text is on topic for FPA`() {
        val result = detector.check(
            "L'apprenant doit acquérir les compétences nécessaires.",
            Domain.FPA,
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.PASS, result.verdict)
    }

    @Test
    fun `FPA text with Qualliopi reference is strongly on topic`() {
        val result = detector.check(
            "Conforme aux exigences Qualiopi, RNCP niveau 6, AFNOR.",
            Domain.FPA,
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertTrue(result.score >= 0.70)
    }

    @Test
    fun `details mention expected domain`() {
        val result = detector.check("Kotlin is great", Domain.CDA, QualityGateConfig())
        assertTrue(result.details.lowercase().contains("cda"))
    }

    @Test
    fun `score is always between 0 and 1`() {
        val texts = listOf(
            "Spring Boot Gradle Kotlin PostgreSQL Docker" to Domain.CDA,
            "Qualliopi Bloom AFNOR formation évaluation pédagogie" to Domain.FPA,
            "recette cuisine jardinage météo" to Domain.CDA,
            "football tennis natation" to Domain.FPA
        )
        for ((text, domain) in texts) {
            val result = detector.check(text, domain, QualityGateConfig())
            assertTrue(result.score in 0.0..1.0, "score ${result.score} out of range for $domain: $text")
        }
    }
}
