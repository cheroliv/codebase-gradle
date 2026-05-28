package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnonymizationContextTest {

    @Test
    fun `AnonymizationRequest stores sourcePath content and targetFormat`() {
        val request = AnonymizationRequest(
            sourcePath = "/workspace/config/secrets.yml",
            content = "api_key: sk-123456",
            targetFormat = "yml"
        )
        assertEquals("/workspace/config/secrets.yml", request.sourcePath)
        assertEquals("api_key: sk-123456", request.content)
        assertEquals("yml", request.targetFormat)
    }

    @Test
    fun `AnonymizationResult reports findings count from detectedPiiCategories`() {
        val result = AnonymizationResult(
            anonymizedContent = "api_key: [REDACTED]",
            confidenceScore = 0.95,
            detectedPiiCategories = listOf("API_KEY", "EMAIL"),
            replacedCount = 2,
            summary = "2 secrets anonymized"
        )
        assertEquals(2, result.detectedPiiCategories.size)
        assertEquals(2, result.replacedCount)
        assertEquals(0.95, result.confidenceScore)
        assertTrue(result.anonymizedContent.contains("[REDACTED]"))
    }

    @Test
    fun `SensitivityLevel enum has all five levels in order`() {
        val levels = SensitivityLevel.entries
        assertEquals(5, levels.size)
        assertEquals(setOf(0, 1, 2, 3, 4), levels.map { it.level }.toSet())
        assertEquals("niveau 0 — public", SensitivityLevel.PUBLIC.label)
        assertEquals("niveau 1 — interne", SensitivityLevel.INTERNAL.label)
        assertEquals("niveau 2 — confidentiel", SensitivityLevel.CONFIDENTIAL.label)
        assertEquals("niveau 3 — restreint", SensitivityLevel.RESTRICTED.label)
        assertEquals("niveau 4 — secret RGPD", SensitivityLevel.SECRET.label)
    }

    @Test
    fun `SensitivityLevel fromLevel returns correct level or defaults to PUBLIC`() {
        assertEquals(SensitivityLevel.PUBLIC, SensitivityLevel.fromLevel(0))
        assertEquals(SensitivityLevel.INTERNAL, SensitivityLevel.fromLevel(1))
        assertEquals(SensitivityLevel.CONFIDENTIAL, SensitivityLevel.fromLevel(2))
        assertEquals(SensitivityLevel.RESTRICTED, SensitivityLevel.fromLevel(3))
        assertEquals(SensitivityLevel.SECRET, SensitivityLevel.fromLevel(4))
        assertEquals(SensitivityLevel.PUBLIC, SensitivityLevel.fromLevel(-1))
        assertEquals(SensitivityLevel.PUBLIC, SensitivityLevel.fromLevel(99))
    }

    @Test
    fun `PiiCategory stores name level and examples`() {
        val apiKey = PiiCategory(
            name = "API_KEY",
            level = 3,
            examples = listOf("ghp_xxx", "sk-xxx")
        )
        assertEquals("API_KEY", apiKey.name)
        assertEquals(3, apiKey.level)
        assertEquals(2, apiKey.examples.size)

        val email = PiiCategory(
            name = "EMAIL",
            level = 1,
            examples = listOf("user@example.com")
        )
        assertEquals("EMAIL", email.name)
        assertEquals(1, email.level)
        assertEquals(1, email.examples.size)
    }

    @Test
    fun `AnonymizationRequest copy with different targetFormat`() {
        val request = AnonymizationRequest(
            sourcePath = "/app/config.json",
            content = "{\"password\": \"secret\"}",
            targetFormat = "json"
        )
        val modified = request.copy(targetFormat = "yaml")
        assertEquals("/app/config.json", modified.sourcePath)
        assertEquals("yaml", modified.targetFormat)
        assertTrue(modified !== request)
    }
}
