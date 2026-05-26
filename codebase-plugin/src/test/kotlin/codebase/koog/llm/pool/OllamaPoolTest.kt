package codebase.koog.llm.pool

import contracts.llmpool.LlmInstance
import contracts.llmpool.QuotaConfig
import contracts.llmpool.ResetPolicy
import contracts.llmpool.RotationStrategy
import kotlin.test.*

class OllamaPoolTest {

    private fun instance(id: String, port: Int, limit: Long = 10, threshold: Int = 50) =
        LlmInstance(
            id = id,
            baseUrl = "http://localhost:$port",
            model = "gpt-oss:120b-cloud",
            quota = QuotaConfig(limitValue = limit, thresholdPercent = threshold, resetPolicy = ResetPolicy.NEVER)
        )

    @Test
    fun `nextInstance should return the only instance when pool size is 1`() {
        val pool = OllamaPool(listOf(instance("a", 11434)))
        assertEquals(1, pool.size())
        val inst = pool.nextInstance()
        assertEquals("a", inst.id)
        assertEquals("http://localhost:11434", inst.baseUrl)
    }

    @Test
    fun `nextInstance should rotate ROUND_ROBIN between two instances`() {
        val pool = OllamaPool(
            listOf(instance("a", 11434), instance("b", 11435)),
            rotationStrategy = RotationStrategy.ROUND_ROBIN
        )
        assertEquals("a", pool.nextInstance().id)
        assertEquals("b", pool.nextInstance().id)
        assertEquals("a", pool.nextInstance().id)
    }

    @Test
    fun `nextInstance should pick LEAST_USED instance`() {
        val pool = OllamaPool(
            listOf(instance("a", 11434), instance("b", 11435)),
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
                instance("a", 11434, limit = 5, threshold = 50),  // 50% de 5 = 2
                instance("b", 11435, limit = 100, threshold = 80)  // 80% de 100 = 80
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
        val inst = instance("a", 11434, limit = 10, threshold = 50)
        val pool = OllamaPool(listOf(inst))
        repeat(5) { pool.nextInstance() }  // 5 >= 5 → dépassé
        assertTrue(pool.isQuotaExceeded(inst))
    }

    @Test
    fun `resetUsage should clear all counters`() {
        val pool = OllamaPool(listOf(instance("a", 11434, limit = 5, threshold = 50)))
        // limit=5, threshold=50% → seuil=2.5→2 (int arrondi)
        repeat(3) { pool.nextInstance() }  // 3 >= 2 → dépassé
        assertTrue(pool.isQuotaExceeded(instance("a", 11434, limit = 5, threshold = 50)))
        pool.resetUsage()
        assertFalse(pool.isQuotaExceeded(instance("a", 11434, limit = 5, threshold = 50)))
    }

    @Test
    fun `empty pool should throw`() {
        assertFailsWith<IllegalStateException> {
            OllamaPool(emptyList()).nextInstance()
        }
    }

    @Test
    fun `instances should return all instances`() {
        val instances = listOf(instance("a", 11434), instance("b", 11435))
        val pool = OllamaPool(instances)
        assertEquals(2, pool.instances().size)
        assertEquals(instances, pool.instances())
    }
}
