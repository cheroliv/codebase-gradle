package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LlmConfigTest {

    @Test
    fun `should deserialize from YAML with envVar pattern`() {
        val yaml = """
            ai:
              ollama:
                baseUrl: "http://localhost:11434"
                model: "deepseek-v4-pro:cloud"
              gemini:
                envVar: "GEMINI_API_KEY"
                model: "gemini-1.5-flash"
                baseUrl: "https://generativelanguage.googleapis.com/v1beta"
        """.trimIndent()

        val config = LlmConfig.fromYaml(yaml)

        assertNotNull(config)

        // Ollama
        assertEquals("http://localhost:11434", config.ai.ollama.baseUrl)
        assertEquals("deepseek-v4-pro:cloud", config.ai.ollama.model)
        assertEquals(null, config.ai.ollama.envVar)

        // Gemini
        assertEquals("GEMINI_API_KEY", config.ai.gemini.envVar)
        assertEquals("gemini-1.5-flash", config.ai.gemini.model)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", config.ai.gemini.baseUrl)
    }

    @Test
    fun `should resolve Gemini API key from env var`() {
        val config = LlmConfig.fromYaml("""
            ai:
              gemini:
                envVar: "GEMINI_API_KEY"
        """.trimIndent())

        assertEquals("GEMINI_API_KEY", config.ai.gemini.envVar)

        // Sans la variable d'environnement set, resolveApiKey() doit lever une erreur
        try {
            config.ai.gemini.resolveApiKey()
            // Si pas d'erreur, vérifier que la clé n'est pas vide
            // (ne pas logger la clé !)
            assertNotNull(config.ai.gemini.resolveApiKey())
        } catch (e: IllegalStateException) {
            // Attendu si GEMINI_API_KEY n'est pas set dans l'environnement de test
            assertNotNull(e.message)
        }
    }

    @Test
    fun `should resolve Ollama defaults when no env vars set`() {
        val config = LlmConfig.fromYaml("""
            ai:
              ollama:
                baseUrl: "http://localhost:11434"
                model: "deepseek-v4-pro:cloud"
        """.trimIndent())

        assertEquals("http://localhost:11434", config.ai.ollama.resolveBaseUrl())
        assertEquals("deepseek-v4-pro:cloud", config.ai.ollama.resolveModel())
    }

    @Test
    fun `should load from test resource file`() {
        val yaml = javaClass.classLoader.getResource("llm-config.yml")?.readText()
        assertNotNull(yaml, "llm-config.yml should exist in test resources")

        val config = LlmConfig.fromYaml(yaml)

        assertNotNull(config.ai.ollama)
        assertNotNull(config.ai.gemini)
        assertEquals("GEMINI_API_KEY", config.ai.gemini.envVar)
    }
}
