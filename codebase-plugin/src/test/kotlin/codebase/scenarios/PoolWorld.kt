package codebase.scenarios

import codebase.koog.llm.pool.OllamaPool
import contracts.llmpool.LlmInstance
import contracts.llmpool.QuotaConfig
import contracts.llmpool.ResetPolicy
import contracts.llmpool.RotationStrategy

/**
 * World Object injecté par PicoContainer dans toutes les Steps Cucumber
 * de type pool Ollama.
 *
 * Pattern aligné sur VibecodingWorld / AugmentedPlanningWorld :
 * - Injection par constructeur dans les Steps
 * - État mutable partagé entre les scénarios du même scénario
 * - PicoContainer crée une nouvelle instance par scénario
 */
class PoolWorld {

    /** Modèles autorisés — identiques à OllamaPoolTest */
    companion object {
        val AUTHORIZED_PORTS = (11437..11465).toList()
        val AUTHORIZED_MODELS = listOf(
            "gpt-oss:120b-cloud",
            "gpt-oss:20b-cloud",
            "qwen3-coder-next:cloud",
            "qwen3-next:80b-cloud",
            "qwen3-coder:480b-cloud"
        )
    }

    /** Le pool Ollama sous test */
    var pool: OllamaPool? = null

    /** Instances du pool */
    var poolInstances: List<LlmInstance> = emptyList()

    /** Résultats des appels successifs (liste des IDs d'instances retournées) */
    var callResults: MutableList<String> = mutableListOf()

    /** Exception capturée pendant l'exécution */
    var thrownException: Throwable? = null

    /**
     * Crée un pool avec N instances, même port 11437, même modèle gpt-oss:120b-cloud.
     * @param count nombre d'instances
     * @param limit quota limit (default 10)
     * @param threshold pourcentage seuil (default 50)
     */
    fun setupPool(count: Int, limit: Long = 10, threshold: Int = 50) {
        val ids = ('a'..'z').take(count).map { it.toString() }
        poolInstances = ids.map { id ->
            LlmInstance(
                id = id,
                baseUrl = "http://localhost:11437",
                model = "gpt-oss:120b-cloud",
                quota = QuotaConfig(limitValue = limit, thresholdPercent = threshold, resetPolicy = ResetPolicy.NEVER)
            )
        }
        pool = OllamaPool(poolInstances, rotationStrategy = RotationStrategy.ROUND_ROBIN)
    }

    /**
     * Crée un pool complet 29 ports avec 5 modèles cyclés.
     */
    fun setupFullPool(limit: Long = 10, threshold: Int = 50) {
        poolInstances = AUTHORIZED_PORTS.mapIndexed { i, port ->
            val model = AUTHORIZED_MODELS[i % AUTHORIZED_MODELS.size]
            LlmInstance(
                id = "ollama-$port",
                baseUrl = "http://localhost:$port",
                model = model,
                quota = QuotaConfig(limitValue = limit, thresholdPercent = threshold, resetPolicy = ResetPolicy.NEVER)
            )
        }
        pool = OllamaPool(poolInstances, rotationStrategy = RotationStrategy.ROUND_ROBIN)
    }

    /**
     * Appelle nextInstance() N fois et enregistre les résultats.
     */
    fun callPool(times: Int) {
        val p = pool ?: throw IllegalStateException("Pool not initialized — call setupPool first")
        callResults.clear()
        repeat(times) {
            val instance = p.nextInstance()
            callResults.add(instance.id)
        }
    }

    /**
     * Réinitialise le pool (compteurs + index).
     */
    fun resetPool() {
        pool?.resetUsage()
        callResults.clear()
    }
}
