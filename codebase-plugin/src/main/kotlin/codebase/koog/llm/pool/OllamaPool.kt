package codebase.koog.llm.pool

import contracts.llmpool.LlmInstance
import contracts.llmpool.LlmInstancePool
import contracts.llmpool.RotationStrategy

/**
 * Pool d'instances Ollama avec rotation automatique sur quota exceeded.
 *
 * Adapté du pattern ApiKeyPool de graphify-gradle.
 * Chaque instance correspond à un conteneur Docker Ollama (port distinct, clé SSH distincte).
 * Si le quota d'une instance est dépassé, on passe automatiquement à l'instance suivante.
 *
 * @param instances liste d'instances Ollama (ports 11437→11465+)
 * @param rotationStrategy stratégie de rotation (ROUND_ROBIN par défaut)
 */
class OllamaPool(
    private val instances: List<LlmInstance>,
    private val rotationStrategy: RotationStrategy = RotationStrategy.ROUND_ROBIN
) : LlmInstancePool {

    private var currentIndex = 0
    private val usageCounts = mutableMapOf<String, Long>()

    init {
        instances.forEach { instance ->
            usageCounts[instance.id] = 0
        }
    }

    override fun size(): Int = instances.size

    override fun instances(): List<LlmInstance> = instances.toList()

    override fun nextInstance(): LlmInstance {
        if (instances.isEmpty()) {
            throw IllegalStateException("Ollama pool is empty — no instances configured")
        }

        // Cherche la prochaine instance non saturée
        val startIndex = when (rotationStrategy) {
            RotationStrategy.ROUND_ROBIN -> currentIndex
            RotationStrategy.LEAST_USED -> instances.indices.minByOrNull { i ->
                usageCounts[instances[i].id] ?: 0
            } ?: 0
        }

        var attempts = 0
        var idx = startIndex
        while (attempts < instances.size) {
            val candidate = instances[idx % instances.size]
            val usage = usageCounts[candidate.id] ?: 0
            val exceeded = candidate.quota.isExceeded(usage)
            if (!exceeded) {
                usageCounts[candidate.id] = usage + 1
                currentIndex = (idx + 1) % instances.size
                return candidate
            }
            idx++
            attempts++
        }

        // Toutes les instances sont saturées — retourne la première (best-effort)
        val fallback = instances[startIndex % instances.size]
        usageCounts[fallback.id] = (usageCounts[fallback.id] ?: 0) + 1
        currentIndex = (startIndex + 1) % instances.size
        return fallback
    }

    override fun isQuotaExceeded(instance: LlmInstance): Boolean {
        val usage = usageCounts[instance.id] ?: 0
        return instance.quota.isExceeded(usage)
    }

    override fun resetUsage() {
        usageCounts.clear()
        instances.forEach { instance ->
            usageCounts[instance.id] = 0
        }
        currentIndex = 0
    }
}
