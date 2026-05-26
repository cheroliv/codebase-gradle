package codebase.koog.llm.pool

import codebase.koog.llm.LlmProvider
import contracts.llmpool.LlmInstance
import contracts.llmpool.LlmInstancePool
import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider Ollama via pool d'instances avec rotation automatique.
 *
 * Chaque instance du pool correspond à un conteneur Docker Ollama distinct
 * (port + clé SSH différentes). Le pool gère la rotation quand le quota
 * d'une instance est dépassé (quota exceeded → instance suivante).
 *
 * Pattern : Clean Architecture — implémente LlmProvider.
 * Utilise OllamaPool (N1) qui implémente LlmInstancePool (N0).
 * Utilise OllamaChatModel de langchain4j pour l'appel HTTP.
 *
 * RÈGLE ABSOLUE : pas de clé API, pas de token SSH en clair.
 * L'instance Docker gère l'auth via sa propre clé SSH montée en volume.
 * On appelle juste l'URL HTTP locale.
 *
 * Timeout : 60s max par appel LLM.
 */
class OllamaLlmProvider(
    private val pool: LlmInstancePool
) : LlmProvider {

    private val log = LoggerFactory.getLogger(OllamaLlmProvider::class.java)
    private val timeout = Duration.ofSeconds(60)

    /** Cache des ChatModels par (baseUrl, model) — thread-safe */
    private val modelCache = ConcurrentHashMap<String, OllamaChatModel>()

    override suspend fun call(prompt: String): String {
        if (pool.size() == 0) {
            throw IllegalStateException("Ollama pool is empty — no instances configured")
        }

        val instance = pool.nextInstance()
        log.info(
            "[OllamaLlmProvider] Selected instance: id={}, baseUrl={}, model={}",
            instance.id, instance.baseUrl, instance.model
        )
        val model = getOrCreateModel(instance)
        log.info("[OllamaLlmProvider] Calling Ollama: model={}, promptLength={}", instance.model, prompt.length)

        return try {
            val response = withContext(Dispatchers.IO) {
                model.chat(prompt)
            }
            log.info("[OllamaLlmProvider] Ollama response received: length={}", response.length)
            response
        } catch (e: Exception) {
            val message = e.message ?: "unknown"
            log.warn("[OllamaLlmProvider] Ollama call failed for instance {}: {}", instance.id, message)

            // Si quota exceeded, le pool devrait déjà avoir comptabilisé l'appel
            // On ne tente pas de retry ici — le pool gère la rotation
            if (message.contains("quota", ignoreCase = true) ||
                message.contains("rate limit", ignoreCase = true) ||
                message.contains("exceeded", ignoreCase = true)
            ) {
                log.warn("[OllamaLlmProvider] Quota exceeded detected on instance {} — pool will rotate to next instance", instance.id)
            }

            throw e
        }
    }

    private fun getOrCreateModel(instance: LlmInstance): OllamaChatModel {
        val cacheKey = cacheKey(instance)
        return modelCache.getOrPut(cacheKey) {
            log.info("[OllamaLlmProvider] Creating OllamaChatModel: baseUrl={}, model={}", instance.baseUrl, instance.model)
            OllamaChatModel.builder()
                .baseUrl(instance.baseUrl)
                .modelName(instance.model)
                .timeout(timeout)
                .build()
        }
    }

    /** 🔥 Exposé pour les tests (package-visible) */
    internal fun getCachedModel(instance: LlmInstance): OllamaChatModel = getOrCreateModel(instance)

    private fun cacheKey(instance: LlmInstance): String = "${instance.baseUrl}|${instance.model}"
}
