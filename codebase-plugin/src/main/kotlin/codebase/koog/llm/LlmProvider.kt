package codebase.koog.llm

/**
 * Abstraction d'appel LLM — Clean Architecture.
 * Permet d'injecter un vrai modèle (Gemini/Ollama) en production
 * et un FakeLlmProvider en test, sans clé API.
 */
fun interface LlmProvider {
    suspend fun call(prompt: String): String
}
