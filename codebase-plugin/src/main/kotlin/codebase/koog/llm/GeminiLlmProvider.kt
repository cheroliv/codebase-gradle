package codebase.koog.llm

import codebase.rag.GeminiConfig
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Provider Gemini en production — cablé via GeminiConfig.
 *
 * RÈGLE ABSOLUE : la clé API n'est jamais loggée.
 * Le logger est limité à INFO et ne doit jamais afficher la clé.
 */
class GeminiLlmProvider(
    private val config: GeminiConfig
) : LlmProvider {

    private val log = LoggerFactory.getLogger(GeminiLlmProvider::class.java)

    private val model: GoogleAiGeminiChatModel by lazy {
        val apiKey = config.resolveApiKey()
        log.info("[GeminiLlmProvider] Creating Gemini chat model: model={}, baseUrl={}", config.model, config.baseUrl)
        GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(config.model)
            .build()
    }

    override suspend fun call(prompt: String): String {
        log.info("[GeminiLlmProvider] Calling Gemini: model={}, promptLength={}", config.model, prompt.length)
        val response = withContext(Dispatchers.IO) {
            model.chat(prompt)
        }
        log.info("[GeminiLlmProvider] Gemini response received: length={}", response.length)
        return response
    }
}
