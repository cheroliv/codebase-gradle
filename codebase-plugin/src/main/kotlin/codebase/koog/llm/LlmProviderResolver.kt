package codebase.koog.llm

import codebase.koog.llm.pool.OllamaLlmProvider
import codebase.koog.llm.pool.OllamaPool
import codebase.rag.GeminiConfig
import contracts.llmpool.LlmInstance
import contracts.llmpool.RotationStrategy

/**
 * Resolves an [LlmProvider] from a model name.
 *
 * Zero file-based configuration. Secrets live exclusively in environment
 * variables (GEMINI_API_KEY) — never in gradle.properties or any versioned file.
 *
 * Mapping:
 * - "gemini" → [GeminiLlmProvider]
 * - "ollama", "deepseek" → [OllamaLlmProvider] backed by Gemma4 Cloud pool
 *   (ROUND_ROBIN rotation across ports from `OLLAMA_POOL_PORTS` env var,
 *   default single port 11437, model `gpt-oss:120b-cloud`)
 * - any other string → [OllamaLlmProvider] single-instance with that model name
 *
 * NOTE: blank model is handled by the caller (VibecodingTask) — no provider
 * is injected when model is empty, preserving deterministic/backward-compat mode.
 */
object LlmProviderResolver {

    private const val DEFAULT_HOST = "http://localhost:%d"
    private const val DEFAULT_PORT = 11437
    /** Modèle par défaut pour le pool Gemma4 — aligné sur OllamaPoolTest */
    private const val DEFAULT_MODEL = "gpt-oss:120b-cloud"

    /** Gemma4 Cloud pool — shared across all vibecoding sessions */
    private val gemma4Pool: OllamaPool by lazy {
        val ports = parsePorts(System.getenv("OLLAMA_POOL_PORTS") ?: DEFAULT_PORT.toString())
        val instances = ports.map { port ->
            LlmInstance(
                id = "gemma4-$port",
                baseUrl = DEFAULT_HOST.format(port),
                model = DEFAULT_MODEL
            )
        }
        OllamaPool(instances, rotationStrategy = RotationStrategy.ROUND_ROBIN)
    }

    fun resolve(model: String): LlmProvider {
        return when (model.lowercase().trim()) {
            "gemini" -> GeminiLlmProvider(GeminiConfig())
            "ollama", "deepseek" -> OllamaLlmProvider(gemma4Pool)
            else -> {
                val port = parsePorts(
                    System.getenv("OLLAMA_BASE_URL")
                        ?.removePrefix("http://localhost:")
                        ?: DEFAULT_PORT.toString()
                ).first()
                val instance = LlmInstance(
                    id = "custom",
                    baseUrl = DEFAULT_HOST.format(port),
                    model = model
                )
                OllamaLlmProvider(OllamaPool(listOf(instance)))
            }
        }
    }

    private fun parsePorts(raw: String): List<Int> =
        raw.split(",").map { it.trim().toInt() }
}
