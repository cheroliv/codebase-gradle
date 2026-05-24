package codebase.koog

import education.cccp.contracts.context.ChannelBudget
import education.cccp.contracts.context.ChannelType
import education.cccp.contracts.context.ContextChannel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiChannelContextGraphTest {

    @Test
    fun `graph topology has all four nodes`() {
        val graph = MultiChannelContextGraph()
        val mermaid = graph.asMermaidDiagram()
        assertTrue(mermaid.contains("collectEager"))
        assertTrue(mermaid.contains("collectRag"))
        assertTrue(mermaid.contains("collectGraphify"))
        assertTrue(mermaid.contains("assemble"))
    }

    @Test
    fun `execute collects eager channel from test workspace`() {
        val graph = MultiChannelContextGraph()
        val state = MultiChannelState(
            intention = "test",
            workspaceRoot = "/home/cheroliv/workspace",
            budget = ChannelBudget(totalTokenBudget = 3000)
        )
        val result = graph.execute(state)

        assertTrue(result.channels.isNotEmpty(), "Should have at least eager channel")
        val eager = result.eager
        assertNotNull(eager, "Eager channel should be present")
        assertTrue(eager.isNotEmpty(), "Eager channel should have content")
    }

    @Test
    fun `execute returns all four channels after assembly`() {
        val graph = MultiChannelContextGraph()
        val state = MultiChannelState(
            intention = "architecture",
            workspaceRoot = "/home/cheroliv/workspace",
            budget = ChannelBudget(totalTokenBudget = 3000)
        )
        val result = graph.execute(state)

        assertEquals(5, result.channels.size, "Should have 5 channels")
        assertEquals(ChannelType.EAGER, result.channels[0].type)
        assertEquals(ChannelType.RAG, result.channels[1].type)
        assertEquals(ChannelType.GRAPHIFY, result.channels[2].type)
        assertEquals(ChannelType.DOCS, result.channels[3].type)
        assertEquals(ChannelType.RESOURCE, result.channels[4].type)
    }

    @Test
    fun `assemble produces non-empty assembledContext`() {
        val graph = MultiChannelContextGraph()
        val state = MultiChannelState(
            intention = "workspace context",
            workspaceRoot = "/home/cheroliv/workspace",
            budget = ChannelBudget(totalTokenBudget = 3000)
        )
        val result = graph.execute(state)

        assertTrue(result.assembledContext.isNotEmpty(), "Assembled context should not be empty")
        assertTrue(result.assembledContext.contains("[RÈGLES_EAGER]"))
        assertTrue(result.assembledContext.contains("[RELATIONS_GRAPHIFY]"))
    }

    @Test
    fun `channels respect budget truncation`() {
        val graph = MultiChannelContextGraph()
        val budget = ChannelBudget(totalTokenBudget = 1000)
        val state = MultiChannelState(
            intention = "test",
            workspaceRoot = "/home/cheroliv/workspace",
            budget = budget
        )
        val result = graph.execute(state)

        val totalContentLength = result.channels.sumOf { it.content.length }
        val maxExpectedChars = budget.eagerTokens * 4 + budget.ragTokens * 4 + budget.graphifyTokens * 4 + budget.docsTokens * 4 + budget.resourceTokens * 4
        assertTrue(totalContentLength <= maxExpectedChars + 100,
            "Total content ($totalContentLength) should be within budget ($maxExpectedChars)")
    }

    @Test
    fun `state accessors return correct channels`() {
        val eager = ContextChannel.Eager("eager content")
        val rag = ContextChannel.Rag("rag content")
        val graphify = ContextChannel.Graphify("graphify content")
        val resource = ContextChannel.Resource("resource content")
        val state = MultiChannelState(
            intention = "test",
            workspaceRoot = "/tmp",
            channels = listOf(eager, rag, graphify, resource)
        )
        assertNotNull(state.eager)
        assertEquals("eager content", state.eager!!.content)
        assertNotNull(state.rag)
        assertEquals("rag content", state.rag!!.content)
        assertNotNull(state.graphify)
        assertEquals("graphify content", state.graphify!!.content)
        assertNotNull(state.resource)
        assertEquals("resource content", state.resource!!.content)
    }

    @Test
    fun `execute is resilient when workspaceRoot does not exist`() {
        val graph = MultiChannelContextGraph()
        val state = MultiChannelState(
            intention = "test",
            workspaceRoot = "/nonexistent/path/for/test",
            budget = ChannelBudget(totalTokenBudget = 1000)
        )
        val result = graph.execute(state)

        assertTrue(result.channels.isNotEmpty(), "Should not crash on invalid path")
        assertTrue(result.channels.any { it.content.isBlank() || it.content.contains("non trouve") || it.content.contains("indisponible") },
            "Should gracefully handle missing resources")
    }
}
