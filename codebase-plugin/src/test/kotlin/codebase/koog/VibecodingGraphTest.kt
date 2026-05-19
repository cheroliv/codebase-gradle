package codebase.koog

import ai.koog.agents.core.agent.asMermaidDiagram
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VibecodingGraphTest {

    @Test
    fun `graph includes all required nodes`() = runBlocking {
        val graph = VibecodingGraph()
        val mermaid = graph.asMermaidDiagram()

        assertNotNull(mermaid, "Mermaid diagram should be generated")
        assertTrue(mermaid.contains("buildContext"), "Mermaid must contain buildContext node")
        assertTrue(mermaid.contains("classify"), "Mermaid must contain classify node")
        assertTrue(mermaid.contains("plan"), "Mermaid must contain plan node")
        assertTrue(mermaid.contains("executeTools"), "Mermaid must contain executeTools node")
        assertTrue(mermaid.contains("sendToolResult"), "Mermaid must contain sendToolResult node")
    }

    @Test
    fun `mermaid contains graph edges`() = runBlocking {
        val graph = VibecodingGraph()
        val mermaid = graph.asMermaidDiagram()

        assertNotNull(mermaid)
        assertTrue(mermaid.contains("-->"), "Mermaid must contain graph edges")
    }

    @Test
    fun `buildSystemPrompt contains required fields`() {
        val graph = VibecodingGraph()
        val state = VibecodingState(
            intention = "Add dark mode toggle",
            workspaceRoot = "/tmp/test",
            dryRun = true,
            maxActions = 5
        )
        val prompt = graph.buildSystemPrompt(state)

        assertTrue(prompt.contains("Intention"), "Prompt must contain 'Intention'")
        assertTrue(prompt.contains("Plan"), "Prompt must contain 'Plan'")
        assertTrue(prompt.contains("WorkspaceRoot"), "Prompt must contain 'WorkspaceRoot'")
        assertTrue(prompt.contains("DryRun"), "Prompt must contain 'DryRun'")
        assertTrue(prompt.contains("Add dark mode toggle"), "Prompt must contain the intention")
        assertTrue(prompt.contains("/tmp/test"), "Prompt must contain the workspaceRoot")
    }

    @Test
    fun `state is not final when iteration less than maxActions`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp",
            maxActions = 3,
            iteration = 2
        )
        assertFalse(state.isFinal, "State with iteration 2/3 should NOT be final")
    }

    @Test
    fun `state is final when iteration equals maxActions`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp",
            maxActions = 3,
            iteration = 3
        )
        assertTrue(state.isFinal, "State with iteration 3/3 should be final")
    }

    @Test
    fun `state is final when finished flag is set`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp",
            finished = true
        )
        assertTrue(state.isFinal, "State with finished=true should be final")
    }

    @Test
    fun `nextIteration increments counter`() {
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp", iteration = 0)
        val next = state.nextIteration()
        assertEquals(1, next.iteration, "nextIteration should increment from 0 to 1")
    }

    @Test
    fun `execute does not crash on simple intention`() {
        val graph = VibecodingGraph()
        val state = VibecodingState(
            intention = "Add dark mode toggle",
            workspaceRoot = "/tmp/test"
        )
        val result = graph.execute(state)
        assertNotNull(result, "Execute should return a non-null state")
    }

    @Test
    fun `withPlan sets plan fields correctly`() {
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp")
        val updated = state.withPlan(
            planJson = "{\"title\":\"test\"}",
            plan = null,
            classification = "simple"
        )
        assertEquals("{\"title\":\"test\"}", updated.planJson)
        assertEquals("simple", updated.classification)
    }

    @Test
    fun `augmented graph is not null`() {
        val graph = VibecodingGraph()
        assertNotNull(graph.augmentedGraph, "Augmented context graph should not be null")
    }
}
