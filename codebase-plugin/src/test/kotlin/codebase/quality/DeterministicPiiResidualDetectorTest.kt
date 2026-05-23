package codebase.quality

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicPiiResidualDetectorTest {

    private val detector = DeterministicPiiResidualDetector()

    @Test
    fun `clean text with no PII passes`() {
        val result = detector.check(
            "class UserService { fun findById(id: Long): User = repository.get(id) }",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertEquals(1.0, result.score)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `email in output fails detection`() {
        val result = detector.check(
            "Contact admin at admin@talaria.school for support.",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.isNotEmpty())
        assertTrue(result.issues.any { it.category == "PII_EMAIL" })
    }

    @Test
    fun `GitHub token pattern fails detection`() {
        val result = detector.check(
            "export GITHUB_TOKEN=ghp_1234567890abcdefghijklmnopqrstuv",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.any { it.category == "PII_TOKEN" })
    }

    @Test
    fun `OpenAI API key pattern fails detection`() {
        val result = detector.check(
            "apiKey = \"sk-proj-1234567890abcdefghijklmnopqrstuvwxyz\"",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.any { it.category == "PII_TOKEN" })
    }

    @Test
    fun `AWS access key pattern fails detection`() {
        val result = detector.check(
            "AWS_ACCESS_KEY_ID=AKIA1234567890ABCDEF",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.any { it.category == "PII_TOKEN" })
    }

    @Test
    fun `IP address in output fails detection`() {
        val result = detector.check(
            "Server running on 192.168.1.100:8080 with backup at 10.0.0.1.",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.any { it.category == "PII_IP" })
    }

    @Test
    fun `credit card number pattern fails detection`() {
        val result = detector.check(
            "Payment processed with card 4111-1111-1111-1111 successfully.",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.any { it.category == "PII_CREDIT_CARD" })
    }

    @Test
    fun `french phone number fails detection`() {
        val result = detector.check(
            "Contactez-nous au 06 12 34 56 78 pour plus d'informations.",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.any { it.category == "PII_PHONE" })
    }

    @Test
    fun `international phone number fails detection`() {
        val result = detector.check(
            "Numéro international : +33 6 12 34 56 78",
            QualityGateConfig()
        )
        assertTrue(result.verdict.severity >= QualityVerdict.NEEDS_FIX.severity)
        assertTrue(result.issues.any { it.category == "PII_PHONE" })
    }

    @Test
    fun `password in context fails detection`() {
        val result = detector.check(
            "password=SuperSecret123!",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.any { it.category == "PII_PASSWORD" })
    }

    @Test
    fun `multiple PII types produce multiple issues with FAIL verdict`() {
        val result = detector.check(
            """
            Configuration:
            admin_email = jean.dupont@example.com
            api_key = ghp_abcdef1234567890
            server = 10.0.0.50
            password = changeMe123
            """.trimIndent(),
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.FAIL, result.verdict)
        assertTrue(result.issues.size >= 3)
    }

    @Test
    fun `masked tokens with stars pass detection`() {
        val result = detector.check(
            "token=*** password=*** email=anonymous@acme.com",
            QualityGateConfig()
        )
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `empty text passes with max score`() {
        val result = detector.check("", QualityGateConfig())
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertEquals(1.0, result.score)
    }

    @Test
    fun `blank text passes with max score`() {
        val result = detector.check("   \n  \t  ", QualityGateConfig())
        assertEquals(QualityVerdict.PASS, result.verdict)
        assertEquals(1.0, result.score)
    }

    @Test
    fun `score degrades with more PII issues`() {
        val clean = detector.check("val x = 42", QualityGateConfig())
        val oneIssue = detector.check("email: user@test.com", QualityGateConfig())
        val manyIssues = detector.check(
            "email: a@b.com, token: ghp_test, ip: 1.2.3.4, card: 4111111111111111",
            QualityGateConfig()
        )
        assertTrue(clean.score > oneIssue.score)
        assertTrue(oneIssue.score > manyIssues.score)
    }
}
