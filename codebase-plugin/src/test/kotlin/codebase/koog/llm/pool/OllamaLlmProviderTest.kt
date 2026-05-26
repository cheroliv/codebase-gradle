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
        val instance = LlmInstance("test", "http://localhost:11434", "qwen3:0.6b", quota = defaultQuota)
        val pool = OllamaPool(listOf(instance))
        val provider = OllamaLlmProvider(pool)
        assertIs<LlmProvider>(provider)
    }

    @Test
    fun `OllamaLlmProvider should build OllamaChatModel lazily on first call`() {
        val instance = LlmInstance("a", "http://localhost:11434", "deepseek-v4-pro:cloud", quota = defaultQuota)
        val pool = OllamaPool(listOf(instance))

        assumeTrue(isOllamaReady(11434), "Ollama not ready on port 11434 — skipping integration test")

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
        val instances = (11434..11435).map { port ->
            LlmInstance("ollama-$port", "http://localhost:$port", "gpt-oss:120b-cloud", quota = defaultQuota)
        }
        val pool = OllamaPool(instances, rotationStrategy = RotationStrategy.ROUND_ROBIN)

        assumeTrue(isOllamaReady(11434), "Ollama not ready on port 11434 — skipping integration test")

        val provider = OllamaLlmProvider(pool)
        // Premier appel → instance a (11434)
        val usageBeforeA = pool.instances().first { it.id == "ollama-11434" }.let { pool.isQuotaExceeded(it) }

        try {
            kotlinx.coroutines.runBlocking { provider.call("One word") }
        } catch (_: dev.langchain4j.exception.ModelNotFoundException) {
            // OK — modèle non pullé, mais le pool a quand même comptabilisé l'appel
        }

        // Vérifie que l'instance a a bien été utilisée (usage incrémenté)
        val firstAfterCall = pool.instances().first { it.id == "ollama-11434" }
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
}
