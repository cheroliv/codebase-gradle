package codebase.koog.llm

/**
 * Fake provider LLM pour les tests — sans clé API, sans réseau.
 * Retourne des réponses déterministes pour valider le pipeline BDD.
 */
class FakeLlmProvider : LlmProvider {

    val promptsReceived = mutableListOf<String>()

    var nextResponse: String = "I'll execute: add_dark_mode_toggle"

    override suspend fun call(prompt: String): String {
        promptsReceived.add(prompt)
        return nextResponse
    }
}
