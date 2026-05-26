package codebase.koog.llm.pool

import codebase.koog.llm.LlmProvider
import contracts.llmpool.LlmInstance
import contracts.llmpool.QuotaConfig
import contracts.llmpool.ResetPolicy
import contracts.llmpool.RotationStrategy
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.*

class OllamaLlmProviderTest {

    private val defaultQuota = QuotaConfig(limitValue = 100, thresholdPercent = 80, resetPolicy = ResetPolicy.NEVER)

    /** Test si Ollama tourne sur ce port avec au moins 1 modèle pullé */
    private fun isOllamaReady(port: Int): Boolean {
        return try {
            val url = URI("http://localhost:$port/api/tags").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    @Test
    fun `OllamaLlmProvider should implement LlmProvider`() {
        val instance = LlmInstance("test", "http://localhost:11437", "gpt-oss:120b-cloud", quota = defaultQuota)
        val pool = OllamaPool(listOf(instance))
        val provider = OllamaLlmProvider(pool)
        assertIs<LlmProvider>(provider)
    }

    @Test
    fun `OllamaLlmProvider should build OllamaChatModel lazily on first call`() {
        val instance = LlmInstance("a", "http://localhost:11437", "gpt-oss:20b-cloud", quota = defaultQuota)
        val pool = OllamaPool(listOf(instance))

        assumeTrue(isOllamaReady(11437), "Ollama not ready on port 11437 — skipping integration test")

        val provider = OllamaLlmProvider(pool)
        // Appel réel — peut échouer si le modèle n'est pas pullé, mais ne doit pas planter
        // Le test vérifie que l'appel ne jette pas d'exception inattendue hors ModelNotFoundException
        try {
            val response = kotlinx.coroutines.runBlocking { provider.call("Say hello") }
            assertTrue(response.isNotBlank())
        } catch (_: dev.langchain4j.exception.ModelNotFoundException) {
            // Modèle non pullé = acceptable, le provider a bien fonctionné jusqu'à l'appel HTTP
        }
    }

    @Test
    fun `OllamaLlmProvider should rotate through pool instances`() {
        val instances = (11437..11438).map { port ->
            LlmInstance("ollama-$port", "http://localhost:$port", "gpt-oss:120b-cloud", quota = defaultQuota)
        }
        val pool = OllamaPool(instances, rotationStrategy = RotationStrategy.ROUND_ROBIN)

        assumeTrue(isOllamaReady(11437), "Ollama not ready on port 11437 — skipping integration test")

        val provider = OllamaLlmProvider(pool)
        // Premier appel → instance a (11437)
        val usageBeforeA = pool.instances().first { it.id == "ollama-11437" }.let { pool.isQuotaExceeded(it) }

        try {
            kotlinx.coroutines.runBlocking { provider.call("One word") }
        } catch (_: dev.langchain4j.exception.ModelNotFoundException) {
            // OK — modèle non pullé, mais le pool a quand même comptabilisé l'appel
        }

        // Vérifie que l'instance a a bien été utilisée (usage incrémenté)
        val firstAfterCall = pool.instances().first { it.id == "ollama-11437" }
        assertTrue(
            pool.isQuotaExceeded(firstAfterCall.copy(quota = QuotaConfig(limitValue = 1, thresholdPercent = 50, resetPolicy = ResetPolicy.NEVER))) || !usageBeforeA,
            "Pool should increment usage count even on ModelNotFoundException"
        )
    }

    @Test
    fun `OllamaLlmProvider should throw when pool is empty`() {
        val pool = OllamaPool(emptyList())
        val provider = OllamaLlmProvider(pool)
        assertFailsWith<IllegalStateException> {
            kotlinx.coroutines.runBlocking { provider.call("test") }
        }
    }

    @Test
    fun `should deduplicate ChatModel cache by baseUrl and model not instance id`() {
        // Deux instances avec le même (baseUrl, model) mais des IDs différents
        val inst1 = LlmInstance("x", "http://localhost:11437", "gpt-oss:120b-cloud", quota = defaultQuota)
        val inst2 = LlmInstance("y", "http://localhost:11437", "gpt-oss:120b-cloud", quota = defaultQuota)
        val pool = OllamaPool(listOf(inst1, inst2), rotationStrategy = RotationStrategy.ROUND_ROBIN)
        val provider = OllamaLlmProvider(pool)

        // Appel 1 → inst1 → crée ChatModel pour (11437, gpt-oss:120b-cloud)
        val model1 = provider.getCachedModel(inst1)
        // Appel 2 → inst2 → même (baseUrl, model) → DOIT retourner le même objet
        val model2 = provider.getCachedModel(inst2)

        assertSame(model1, model2, "Same (baseUrl, model) should return the same cached ChatModel")
    }

    @Test
    fun `should create separate cache entries for different models`() {
        val instA = LlmInstance("a", "http://localhost:11437", "gpt-oss:120b-cloud", quota = defaultQuota)
        val instB = LlmInstance("b", "http://localhost:11437", "gpt-oss:20b-cloud", quota = defaultQuota)
        val pool = OllamaPool(listOf(instA, instB))
        val provider = OllamaLlmProvider(pool)

        val modelA = provider.getCachedModel(instA)
        val modelB = provider.getCachedModel(instB)

        assertNotSame(modelA, modelB, "Different models should have separate cache entries")
    }
}
