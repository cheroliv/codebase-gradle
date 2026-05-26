package codebase.koog.llm

import codebase.koog.llm.pool.OllamaLlmProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

/**
 * Tests unitaires pour LlmProviderResolver — mapping model → provider.
 *
 * Architecture TDD : ces tests définissent le comportement attendu.
 * - "gemini" → GeminiLlmProvider (lazy, sans appel .call())
 * - "ollama", "deepseek", "" → OllamaLlmProvider avec deepseek-v4-pro:cloud
 * - Autre chaîne → OllamaLlmProvider avec cette chaîne comme model name
 */
class LlmProviderResolverTest {

    @Test
    fun `resolve gemini returns GeminiLlmProvider`() {
        val provider = LlmProviderResolver.resolve("gemini")
        assertIs<GeminiLlmProvider>(provider)
    }

    @Test
    fun `resolve GEMINI uppercase returns GeminiLlmProvider`() {
        val provider = LlmProviderResolver.resolve("GEMINI")
        assertIs<GeminiLlmProvider>(provider)
    }

    @Test
    fun `resolve ollama returns OllamaLlmProvider`() {
        val provider = LlmProviderResolver.resolve("ollama")
        assertIs<OllamaLlmProvider>(provider)
    }

    @Test
    fun `resolve deepseek returns OllamaLlmProvider`() {
        val provider = LlmProviderResolver.resolve("deepseek")
        assertIs<OllamaLlmProvider>(provider)
    }

    @Test
    fun `resolve blank returns OllamaLlmProvider as default`() {
        val provider = LlmProviderResolver.resolve("")
        assertIs<OllamaLlmProvider>(provider)
    }

    @Test
    fun `resolve unknown model returns OllamaLlmProvider`() {
        val provider = LlmProviderResolver.resolve("custom-model:latest")
        assertIs<OllamaLlmProvider>(provider)
    }
}
