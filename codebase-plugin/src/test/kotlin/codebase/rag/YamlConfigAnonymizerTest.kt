package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.*

class YamlConfigAnonymizerTest {

    @Test
    fun `anonymizeYaml should replace secret value with asterisks`() {
        val yaml = """
            |server:
            |  host: localhost
            |  password: secret123
            |  port: 8080
        """.trimMargin()

        val result = YamlConfigAnonymizer.anonymizeYaml(yaml)

        assertTrue(result.contains("password: ***"), "password value should be anonymized")
        assertTrue(result.contains("host: localhost"), "non-sensitive values should be preserved")
        assertFalse(result.contains("secret123"), "secret value should not appear")
    }

    @Test
    fun `anonymizeYaml should not modify non-sensitive keys`() {
        val yaml = """
            |database:
            |  host: db.example.com
            |  port: 5432
            |  name: mydb
        """.trimMargin()

        val result = YamlConfigAnonymizer.anonymizeYaml(yaml)

        assertTrue(result.contains("host: db.example.com"))
        assertTrue(result.contains("port: 5432"))
        assertTrue(result.contains("name: mydb"))
    }

    @Test
    fun `anonymizeYaml should preserve comments`() {
        val yaml = """
            |# database credentials
            |password: secret123
        """.trimMargin()

        val result = YamlConfigAnonymizer.anonymizeYaml(yaml)

        assertTrue(result.contains("# database credentials"), "comments should be preserved")
        assertTrue(result.contains("password: ***"), "password should be anonymized")
    }

    @Test
    fun `anonymizeJson should replace sensitive value in JSON`() {
        val json = """{"apiKey": "sk-abc123", "host": "localhost"}"""

        val result = YamlConfigAnonymizer.anonymizeJson(json)

        assertTrue(result.contains("\"apiKey\": \"***\""), "apiKey should be anonymized")
        assertTrue(result.contains("\"host\": \"localhost\""), "non-sensitive should be preserved")
        assertFalse(result.contains("sk-abc123"), "secret value should not appear")
    }

    @Test
    fun `anonymizeJson should handle token field`() {
        val json = """{"token": "ghp_abcdef1234567890"}"""

        val result = YamlConfigAnonymizer.anonymizeJson(json)

        assertTrue(result.contains("\"token\": \"***\""), "token should be anonymized")
    }

    @Test
    fun `anonymizeJson should handle authorization field`() {
        val json = """{"authorization": "Bearer xyz789"}"""

        val result = YamlConfigAnonymizer.anonymizeJson(json)

        assertTrue(result.contains("\"authorization\": \"***\""), "authorization should be anonymized")
    }

    @Test
    fun `anonymize should dispatch to yaml for yml extension`() {
        val yaml = "token: mytoken\nhost: localhost"

        val result = YamlConfigAnonymizer.anonymize(yaml, "yml")

        assertTrue(result.contains("token: ***"))
    }

    @Test
    fun `anonymize should dispatch to json for json extension`() {
        val json = """{"apiKey": "sk-abc123"}"""

        val result = YamlConfigAnonymizer.anonymize(json, "json")

        assertTrue(result.contains("\"apiKey\": \"***\""))
    }

    @Test
    fun `anonymize should return unchanged for unknown format`() {
        val content = "password: secret123"

        val result = YamlConfigAnonymizer.anonymize(content, "txt")

        assertEquals(content, result)
    }

    @Test
    fun `verifyNoSecrets should return empty list for clean content`() {
        val clean = """
            |server:
            |  host: localhost
            |  port: 8080
        """.trimMargin()

        val findings = YamlConfigAnonymizer.verifyNoSecrets(clean)

        assertTrue(findings.isEmpty(), "clean content should have no findings")
    }

    @Test
    fun `verifyNoSecrets should detect password in plain text`() {
        val content = """
            |server:
            |  host: localhost
            |  password: mypass
        """.trimMargin()

        val findings = YamlConfigAnonymizer.verifyNoSecrets(content)

        assertTrue(findings.isNotEmpty(), "should detect the secret")
        assertTrue(findings.any { it.contains("password") }, "finding should mention password")
        assertTrue(findings.any { it.contains("mypass") }, "finding should contain the leaked value")
    }

    @Test
    fun `verifyNoSecrets should detect api key`() {
        val content = "api_key: sk-proj-1234567890"

        val findings = YamlConfigAnonymizer.verifyNoSecrets(content)

        assertTrue(findings.isNotEmpty(), "should detect api_key")
    }

    @Test
    fun `verifyNoSecrets should skip comments`() {
        val content = """
            |# password: secret123
            |host: localhost
        """.trimMargin()

        val findings = YamlConfigAnonymizer.verifyNoSecrets(content)

        assertTrue(findings.isEmpty(), "commented secrets should be ignored")
    }

    @Test
    fun `verifyNoSecrets should skip already anonymized values`() {
        val content = "password: ***"

        val findings = YamlConfigAnonymizer.verifyNoSecrets(content)

        assertTrue(findings.isEmpty(), "already masked values should be skipped")
    }
}
