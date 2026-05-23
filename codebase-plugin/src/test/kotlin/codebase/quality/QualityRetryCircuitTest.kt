package codebase.quality

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QualityRetryCircuitTest {

    @Test
    fun `LLM that produces clean output on first try returns instantly`() = runBlocking {
        val llm = FakeLlm(listOf("class Calculator { fun add(a: Int, b: Int) = a + b }"))
        val circuit = QualityRetryCircuit(
            llm = llm.toSuspend(),
            gate = createGate(),
            domain = Domain.CDA
        )
        val result = circuit.invoke("Write a Calculator class")
        assertNotNull(result.bestOutput)
        assertTrue(result.bestOutput?.contains("Calculator") == true)
        assertEquals(1, result.attempts)
        assertTrue(result.passed)
        assertEquals(1, result.history.size)
    }

    @Test
    fun `LLM that produces PII retries and eventually succeeds`() = runBlocking {
        val llm = FakeLlm(
            listOf(
                "token=ghp_1234567890abcdefghijkl class X {}",
                "val x = 42 fun add(a: Int, b: Int) = a + b"
            )
        )
        val circuit = QualityRetryCircuit(
            llm = llm.toSuspend(),
            gate = createGate(),
            domain = Domain.CDA,
            maxRetries = 2
        )
        val result = circuit.invoke("Write code")
        assertEquals(2, result.attempts)
        assertTrue(result.passed)
    }

    @Test
    fun `LLM that produces PII on every attempt fails after max retries`() = runBlocking {
        val llm = FakeLlm(
            List(10) { "token=ghp_${"abc".repeat(10)} class Broken$it {}" }
        )
        val circuit = QualityRetryCircuit(
            llm = llm.toSuspend(),
            gate = createGate(),
            domain = Domain.CDA,
            maxRetries = 2
        )
        val result = circuit.invoke("Write code")
        assertEquals(3, result.attempts)
        assertTrue(!result.passed)
        assertNotNull(result.bestOutput)
    }

    @Test
    fun `off-topic output gets retried with domain feedback`() = runBlocking {
        val llm = FakeLlm(
            listOf(
                "La recette du gâteau au chocolat.",
                "class BakerService { fun bake(): Cake = Cake() }"
            )
        )
        val circuit = QualityRetryCircuit(
            llm = llm.toSuspend(),
            gate = createGate(),
            domain = Domain.CDA,
            maxRetries = 2
        )
        val result = circuit.invoke("Write a baking service")
        assertEquals(2, result.attempts)
        assertTrue(result.passed)
    }

    @Test
    fun `history records each attempt with its quality assessment`() = runBlocking {
        val llm = FakeLlm(
            listOf(
                "token=bad1",
                "token=bad2",
                "val x = 42"
            )
        )
        val circuit = QualityRetryCircuit(
            llm = llm.toSuspend(),
            gate = createGate(),
            domain = Domain.CDA,
            maxRetries = 3
        )
        val result = circuit.invoke("Write code")
        assertEquals(3, result.attempts)
        assertEquals(3, result.history.size)
        assertTrue(result.history[0].qualityAssessment.results.any { it.checkerName == "pii-residual" })
        assertTrue(result.history[1].qualityAssessment.results.any { it.checkerName == "pii-residual" })
        assertEquals(QualityVerdict.PASS, result.history[2].qualityAssessment.overallVerdict)
    }

    @Test
    fun `best output is the one that passed`() = runBlocking {
        val llm = FakeLlm(
            listOf(
                "juste un mot sans contexte",
                "fun calculateSum(a: Int, b: Int) = a + b",
                "fun computeTotal(x: Int, y: Int) = x * y"
            )
        )
        val circuit = QualityRetryCircuit(
            llm = llm.toSuspend(),
            gate = createGate(),
            domain = Domain.CDA,
            maxRetries = 3
        )
        val result = circuit.invoke("Write a Kotlin function")
        assertEquals("fun calculateSum(a: Int, b: Int) = a + b", result.bestOutput)
    }

    @Test
    fun `best output from failed run is the highest scoring attempt`() = runBlocking {
        val llm = FakeLlm(listOf(
            "ghp_bad_token_xxx_output_1 class A",
            "ghp_bad_token_xxx_output_2 class B val x",
            "token=ghp_bad class C fun f val y",
        ))
        val circuit = QualityRetryCircuit(
            llm = llm.toSuspend(),
            gate = createGate(),
            domain = Domain.CDA,
            maxRetries = 2
        )
        val result = circuit.invoke("Write code")
        assertEquals(3, result.attempts)
        assertTrue(!result.passed)
        assertNotNull(result.bestOutput)
    }

    @Test
    fun `result summary contains gate status`() = runBlocking {
        val llm = FakeLlm(listOf("val x = 42"))
        val circuit = QualityRetryCircuit(
            llm = llm.toSuspend(),
            gate = createGate(),
            domain = Domain.CDA
        )
        val result = circuit.invoke("Write code")
        assertTrue(result.summary.contains("PASS"))
        assertTrue(result.summary.contains("1 attempt"))
    }

    private fun createGate(config: QualityGateConfig = QualityGateConfig()): QualityGate {
        return QualityGate(
            sentimentAnalyzer = DeterministicSentimentAnalyzer(),
            offTopicDetector = DeterministicOffTopicDetector(),
            piiDetector = DeterministicPiiResidualDetector(),
            config = config
        )
    }

    private class FakeLlm(private val responses: List<String>) {
        private var callCount = 0
        fun generate(prompt: String): String {
            val index = callCount.coerceAtMost(responses.lastIndex)
            callCount++
            return responses[index]
        }

        fun toSuspend(): suspend (String) -> String = { prompt -> generate(prompt) }
    }
}
