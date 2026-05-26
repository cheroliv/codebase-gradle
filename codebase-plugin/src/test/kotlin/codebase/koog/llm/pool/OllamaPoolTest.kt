package codebase.koog.llm.pool

import contracts.llmpool.LlmInstance
import contracts.llmpool.QuotaConfig
import contracts.llmpool.ResetPolicy
import contracts.llmpool.RotationStrategy
import kotlin.test.*

class OllamaPoolTest {

    companion object {
        /** Ports autorisés — plage 11437→11465 (29 ports) */
        val AUTHORIZED_PORTS = (11437..11465).toList()

        /** 5 modèles autorisés, cyclés sur les 29 ports */
        val AUTHORIZED_MODELS = listOf(
            "gpt-oss:120b-cloud",
            "gpt-oss:20b-cloud",
            "qwen3-coder-next:cloud",
            "qwen3-next:80b-cloud",
            "qwen3-coder:480b-cloud"
        )

        /** Pool complet : 29 instances, chaque port reçoit un modèle cyclé */
        fun fullPool(limit: Long = 10, threshold: Int = 50): OllamaPool {
            val instances = AUTHORIZED_PORTS.mapIndexed { i, port ->
                val model = AUTHORIZED_MODELS[i % AUTHORIZED_MODELS.size]
                LlmInstance(
                    id = "ollama-$port",
                    baseUrl = "http://localhost:$port",
                    model = model,
                    quota = QuotaConfig(limitValue = limit, thresholdPercent = threshold, resetPolicy = ResetPolicy.NEVER)
                )
            }
            return OllamaPool(instances, rotationStrategy = RotationStrategy.ROUND_ROBIN)
        }
    }

    private fun instance(id: String, port: Int, limit: Long = 10, threshold: Int = 50) =
        LlmInstance(
            id = id,
            baseUrl = "http://localhost:$port",
            model = "gpt-oss:120b-cloud",
            quota = QuotaConfig(limitValue = limit, thresholdPercent = threshold, resetPolicy = ResetPolicy.NEVER)
        )

    @Test
    fun `nextInstance should return the only instance when pool size is 1`() {
        val pool = OllamaPool(listOf(instance("a", 11437)))
        assertEquals(1, pool.size())
        val inst = pool.nextInstance()
        assertEquals("a", inst.id)
        assertEquals("http://localhost:11437", inst.baseUrl)
    }

    @Test
    fun `nextInstance should rotate ROUND_ROBIN between two instances`() {
        val pool = OllamaPool(
            listOf(instance("a", 11437), instance("b", 11438)),
            rotationStrategy = RotationStrategy.ROUND_ROBIN
        )
        assertEquals("a", pool.nextInstance().id)
        assertEquals("b", pool.nextInstance().id)
        assertEquals("a", pool.nextInstance().id)
    }

    @Test
    fun `nextInstance should pick LEAST_USED instance`() {
        val pool = OllamaPool(
            listOf(instance("a", 11437), instance("b", 11438)),
            rotationStrategy = RotationStrategy.LEAST_USED
        )
        // First call — both at 0, picks first
        assertEquals("a", pool.nextInstance().id)
        // Second call — a=1, b=0 → picks b
        assertEquals("b", pool.nextInstance().id)
        // Third call — a=1, b=1 → picks first again (a)
        assertEquals("a", pool.nextInstance().id)
    }

    @Test
    fun `nextInstance should skip instance when quota exceeded`() {
        val pool = OllamaPool(
            listOf(
                instance("a", 11437, limit = 5, threshold = 50),  // 50% de 5 = 2
                instance("b", 11438, limit = 100, threshold = 80)  // 80% de 100 = 80
            ),
            rotationStrategy = RotationStrategy.ROUND_ROBIN
        )
        // Use instance a 3 times → dépassement quota (3 >= 2)
        repeat(3) { pool.nextInstance() }  // a, b, a → a est utilisée 2 fois
        // a est maintenant à 2, pas encore dépassé (threshold=2, 2>=2 → dépassé)
        // Prochain appel devrait skipper a → b
        val next = pool.nextInstance()  // a=2 dépasse 2 → skip → b
        assertEquals("b", next.id)
    }

    @Test
    fun `isQuotaExceeded should return true when threshold reached`() {
        val inst = instance("a", 11437, limit = 10, threshold = 50)
        val pool = OllamaPool(listOf(inst))
        repeat(5) { pool.nextInstance() }  // 5 >= 5 → dépassé
        assertTrue(pool.isQuotaExceeded(inst))
    }

    @Test
    fun `resetUsage should clear all counters`() {
        val pool = OllamaPool(listOf(instance("a", 11437, limit = 5, threshold = 50)))
        // limit=5, threshold=50% → seuil=2.5→2 (int arrondi)
        repeat(3) { pool.nextInstance() }  // 3 >= 2 → dépassé
        assertTrue(pool.isQuotaExceeded(instance("a", 11437, limit = 5, threshold = 50)))
        pool.resetUsage()
        assertFalse(pool.isQuotaExceeded(instance("a", 11437, limit = 5, threshold = 50)))
    }

    @Test
    fun `empty pool should throw`() {
        assertFailsWith<IllegalStateException> {
            OllamaPool(emptyList()).nextInstance()
        }
    }

    @Test
    fun `instances should return all instances`() {
        val instances = listOf(instance("a", 11437), instance("b", 11438))
        val pool = OllamaPool(instances)
        assertEquals(2, pool.instances().size)
        assertEquals(instances, pool.instances())
    }

    @Test
    fun `full pool should cycle through all 29 ports with 5 authorized models`() {
        val pool = fullPool()
        assertEquals(29, pool.size())

        // Vérifie que tous les ports 11437-11465 sont présents
        val allPorts = pool.instances().map { it.baseUrl.removePrefix("http://localhost:").toInt() }.toSet()
        for (port in 11437..11465) {
            assertTrue(port in allPorts, "Port $port missing from pool")
        }

        // Vérifie que seuls les 5 modèles autorisés sont utilisés
        val allModels = pool.instances().map { it.model }.toSet()
        assertEquals(5, allModels.size, "Exactly 5 authorized models should be present")
        for (authorized in AUTHORIZED_MODELS) {
            assertTrue(authorized in allModels, "Model $authorized missing from pool")
        }

        // Vérifie le cycling ROUND_ROBIN sur les 29 ports
        val first = pool.nextInstance()
        assertEquals("http://localhost:11437", first.baseUrl)

        // 28 appels suivants → on atteint le dernier port (11465)
        val last = (2..29).fold<Int, contracts.llmpool.LlmInstance?>(null) { _, _ -> pool.nextInstance() }
        assertEquals("http://localhost:11465", last?.baseUrl, "29th call should return last port 11465")

        // Le 30ème appel wrappe → retour au premier port
        val wrapped = pool.nextInstance()
        assertEquals("http://localhost:11437", wrapped.baseUrl, "30th call should wrap back to 11437")
    }
}
