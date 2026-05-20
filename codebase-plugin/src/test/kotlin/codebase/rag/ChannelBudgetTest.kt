package codebase.rag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChannelBudgetTest {

    @Test
    fun `default budget sums to 1 point 0`() {
        val budget = ChannelBudget()
        assertEquals(8000, budget.totalTokenBudget)
        assertEquals(3200, budget.eagerTokens)
        assertEquals(2400, budget.ragTokens)
        assertEquals(1600, budget.graphifyTokens)
        assertEquals(800, budget.resourceTokens)
    }

    @Test
    fun `budget proportions must sum to 1`() {
        ChannelBudget(budgetEager = 0.40, budgetRag = 0.30, budgetGraphify = 0.20, budgetResource = 0.10)
    }

    @Test
    fun `budget rejects proportions not summing to 1`() {
        val ex = assertThrows<IllegalArgumentException> {
            ChannelBudget(budgetEager = 0.50, budgetRag = 0.30, budgetGraphify = 0.30, budgetResource = 0.10)
        }
        assertTrue(ex.message!!.contains("sum to 1.0"))
    }

    @Test
    fun `tokensFor by ChannelType returns correct budget`() {
        val budget = ChannelBudget(totalTokenBudget = 1000)
        assertEquals(400, budget.tokensFor(ChannelType.EAGER))
        assertEquals(300, budget.tokensFor(ChannelType.RAG))
        assertEquals(200, budget.tokensFor(ChannelType.GRAPHIFY))
        assertEquals(100, budget.tokensFor(ChannelType.RESOURCE))
    }

    @Test
    fun `tokensFor by ContextChannel returns correct budget`() {
        val budget = ChannelBudget(totalTokenBudget = 1000)
        assertEquals(400, budget.tokensFor(ContextChannel.Eager()))
        assertEquals(300, budget.tokensFor(ContextChannel.Rag()))
        assertEquals(200, budget.tokensFor(ContextChannel.Graphify()))
        assertEquals(100, budget.tokensFor(ContextChannel.Resource()))
    }

    @Test
    fun `applyBudget truncates each channel to its budget`() {
        val budget = ChannelBudget(totalTokenBudget = 500)
        val longContent = (1..200).joinToString("\n") { "line $it with enough text to need truncation" }
        val channels = listOf(
            ContextChannel.Eager(longContent),
            ContextChannel.Rag(longContent),
            ContextChannel.Graphify(longContent),
            ContextChannel.Resource(longContent)
        )
        val truncated = budget.applyBudget(channels)
        assertEquals(4, truncated.size)

        val eagerLen = truncated[0].content.length
        val ragLen = truncated[1].content.length
        val graphifyLen = truncated[2].content.length
        val resourceLen = truncated[3].content.length

        assertTrue(eagerLen > 0)
        assertTrue(ragLen > 0)
        assertTrue(graphifyLen > 0)
        assertTrue(resourceLen >= 0)

        assertTrue(eagerLen >= ragLen || abs(eagerLen - ragLen) < 50,
            "Eager ($eagerLen) should be >= RAG ($ragLen), budget 40% vs 30%")
        assertTrue(graphifyLen >= resourceLen || abs(graphifyLen - resourceLen) < 50,
            "Graphify ($graphifyLen) should be >= Resource ($resourceLen), budget 20% vs 10%")
    }

    @Test
    fun `fromConfig converts CompositeContextConfig to ChannelBudget`() {
        val config = CompositeContextConfig(
            totalTokenBudget = 6000,
            budgetEagerLazy = 0.50,
            budgetRag = 0.25,
            budgetGraphify = 0.15,
            budgetOverhead = 0.10
        )
        val budget = ChannelBudget.fromConfig(config)
        assertEquals(6000, budget.totalTokenBudget)
        assertEquals(3000, budget.eagerTokens)
        assertEquals(1500, budget.ragTokens)
        assertEquals(900, budget.graphifyTokens)
        assertEquals(600, budget.resourceTokens)
    }

    @Test
    fun `custom budget proportions allocate tokens correctly`() {
        val budget = ChannelBudget(
            totalTokenBudget = 10000,
            budgetEager = 0.50,
            budgetRag = 0.30,
            budgetGraphify = 0.10,
            budgetResource = 0.10
        )
        assertEquals(5000, budget.eagerTokens)
        assertEquals(3000, budget.ragTokens)
        assertEquals(1000, budget.graphifyTokens)
        assertEquals(1000, budget.resourceTokens)
    }
}
