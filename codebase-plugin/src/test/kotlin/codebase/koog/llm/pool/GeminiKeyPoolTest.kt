package codebase.koog.llm.pool

import contracts.llmpool.LlmInstance
import contracts.llmpool.QuotaConfig
import contracts.llmpool.ResetPolicy
import contracts.llmpool.RotationStrategy
import kotlin.test.*

class GeminiKeyPoolTest {

    companion object {
        /** Ports/clés autorisés — plage 11437→11465 (29 clés) */
        val AUTHORIZED_PORTS = (11437..11465).toList()

        /** 5 modèles autorisés, cyclés sur les 29 clés */
        val AUTHORIZED_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.0-pro"
        )

        /** Pool complet : 29 instances, chaque clé reçoit un modèle cyclé */
        fun fullPool(limit: Long = 10, threshold: Int = 50): GeminiKeyPool {
            val instances = AUTHORIZED_PORTS.mapIndexed { i, port ->
                val model = AUTHORIZED_MODELS[i % AUTHORIZED_MODELS.size]
                LlmInstance(
                    id = "gemini-key-$port",
                    baseUrl = "https://generativelanguage.googleapis.com/v1/models/$model:generateContent?key=$port",
                    model = model,
                    quota = QuotaConfig(limitValue = limit, thresholdPercent = threshold, resetPolicy = ResetPolicy.NEVER)
                )
            }
            return GeminiKeyPool(instances, rotationStrategy = RotationStrategy.ROUND_ROBIN)
        }
    }

    private fun instance(id: String, baseUrl: String, model: String = "gemini-2.5-flash", limit: Long = 10, threshold: Int = 50) =
        LlmInstance(
            id = id,
            baseUrl = baseUrl,
            model = model,
            quota = QuotaConfig(limitValue = limit, thresholdPercent = threshold, resetPolicy = ResetPolicy.NEVER)
        )

    private fun instance(id: String, port: Int, limit: Long = 10, threshold: Int = 50) =
        instance(
            id = id,
            baseUrl = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$port",
            limit = limit,
            threshold = threshold
        )

    @Test
    fun `nextInstance should return the only instance when pool size is 1`() {
        val pool = GeminiKeyPool(listOf(instance("gemini-key-1", 11437)))
        assertEquals(1, pool.size())
        val inst = pool.nextInstance()
        assertEquals("gemini-key-1", inst.id)
        assertEquals("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=11437", inst.baseUrl)
    }

    @Test
    fun `nextInstance should rotate ROUND_ROBIN between two instances`() {
        val pool = GeminiKeyPool(
            listOf(instance("gemini-key-1", 11437), instance("gemini-key-2", 11438)),
            rotationStrategy = RotationStrategy.ROUND_ROBIN
        )
        assertEquals("gemini-key-1", pool.nextInstance().id)
        assertEquals("gemini-key-2", pool.nextInstance().id)
        assertEquals("gemini-key-1", pool.nextInstance().id)
    }

    @Test
    fun `nextInstance should pick LEAST_USED instance`() {
        val pool = GeminiKeyPool(
            listOf(instance("gemini-key-1", 11437), instance("gemini-key-2", 11438)),
            rotationStrategy = RotationStrategy.LEAST_USED
        )
        assertEquals("gemini-key-1", pool.nextInstance().id)
        assertEquals("gemini-key-2", pool.nextInstance().id)
        assertEquals("gemini-key-1", pool.nextInstance().id)
    }

    @Test
    fun `nextInstance should skip instance when quota exceeded`() {
        val pool = GeminiKeyPool(
            listOf(
                instance("gemini-key-1", 11437, limit = 5, threshold = 50),  // 50% de 5 = 2
                instance("gemini-key-2", 11438, limit = 100, threshold = 80)  // 80% de 100 = 80
            ),
            rotationStrategy = RotationStrategy.ROUND_ROBIN
        )
        repeat(3) { pool.nextInstance() }  // gemini-key-1, gemini-key-2, gemini-key-1 → gemini-key-1 utilisée 2 fois
        val next = pool.nextInstance()  // gemini-key-1=2 dépasse 2 → skip → gemini-key-2
        assertEquals("gemini-key-2", next.id)
    }

    @Test
    fun `isQuotaExceeded should return true when threshold reached`() {
        val inst = instance("gemini-key-1", 11437, limit = 10, threshold = 50)
        val pool = GeminiKeyPool(listOf(inst))
        repeat(5) { pool.nextInstance() }  // 5 >= 5 → dépassé
        assertTrue(pool.isQuotaExceeded(inst))
    }

    @Test
    fun `resetUsage should clear all counters`() {
        val pool = GeminiKeyPool(listOf(instance("gemini-key-1", 11437, limit = 5, threshold = 50)))
        repeat(3) { pool.nextInstance() }  // 3 >= 2 → dépassé
        assertTrue(pool.isQuotaExceeded(instance("gemini-key-1", 11437, limit = 5, threshold = 50)))
        pool.resetUsage()
        assertFalse(pool.isQuotaExceeded(instance("gemini-key-1", 11437, limit = 5, threshold = 50)))
    }

    @Test
    fun `empty pool should throw`() {
        assertFailsWith<IllegalStateException> {
            GeminiKeyPool(emptyList()).nextInstance()
        }
    }

    @Test
    fun `instances should return all instances`() {
        val instances = listOf(instance("gemini-key-1", 11437), instance("gemini-key-2", 11438))
        val pool = GeminiKeyPool(instances)
        assertEquals(2, pool.instances().size)
        assertEquals(instances, pool.instances())
    }

    @Test
    fun `full pool should cycle through all 29 keys with 5 authorized models`() {
        val pool = fullPool()
        assertEquals(29, pool.size())

        // Vérifie que toutes les clés 11437-11465 sont présentes
        val allKeys = pool.instances().map { it.baseUrl.substringAfter("key=").toInt() }.toSet()
        for (port in 11437..11465) {
            assertTrue(port in allKeys, "Key $port missing from pool")
        }

        // Vérifie que seuls les 5 modèles autorisés sont utilisés
        val allModels = pool.instances().map { it.model }.toSet()
        assertEquals(5, allModels.size, "Exactly 5 authorized models should be present")
        for (authorized in AUTHORIZED_MODELS) {
            assertTrue(authorized in allModels, "Model $authorized missing from pool")
        }

        // Vérifie le cycling ROUND_ROBIN sur les 29 clés
        val first = pool.nextInstance()
        assertEquals("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=11437", first.baseUrl)

        // 28 appels suivants → on atteint la dernière clé (11465, index 28, modèle = AUTHORIZED_MODELS[28%5] = gemini-2.0-flash)
        val last = (2..29).fold<Int, contracts.llmpool.LlmInstance?>(null) { _, _ -> pool.nextInstance() }
        assertEquals("https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key=11465", last?.baseUrl, "29th call should return last key 11465 with model gemini-2.0-flash")

        // Le 30ème appel wrappe → retour à la première clé
        val wrapped = pool.nextInstance()
        assertEquals("https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=11437", wrapped.baseUrl, "30th call should wrap back to 11437")
    }
}
