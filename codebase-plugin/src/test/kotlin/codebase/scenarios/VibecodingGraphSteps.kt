package codebase.scenarios

import codebase.koog.VibecodingGraph
import codebase.koog.VibecodingState
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VibecodingGraphSteps(private val world: VibecodingGraphWorld) {

    private var vibecodingGraph: VibecodingGraph? = null

    @Given("a VibecodingGraph World is initialized")
    fun `vibecoding graph world is initialized`() {
        assertNotNull(world.workspaceRoot, "Workspace root should be initialized")
        assertTrue(world.workspaceRoot.isDirectory, "Workspace root should be a directory")
    }

    @When("the VibecodingGraph is instantiated")
    fun `vibecoding graph is instantiated`() {
        vibecodingGraph = VibecodingGraph()
        val mermaidDiagram = vibecodingGraph?.asMermaidDiagram() ?: ""
        world.graphResult = mermaidDiagram
        world.systemPrompt = vibecodingGraph?.buildSystemPrompt(
            VibecodingState(
                intention = "test intention",
                workspaceRoot = "/tmp/test-root",
                dryRun = true,
                maxActions = 5
            )
        ) ?: ""
    }

    @Then("the graph contains node {string}")
    fun `graph contains node`(nodeName: String) {
        assertNotNull(vibecodingGraph, "VibecodingGraph should be instantiated")
        val mermaid = vibecodingGraph?.asMermaidDiagram() ?: ""
        assertTrue(
            mermaid.contains(nodeName),
            "Graph Mermaid diagram should contain node '$nodeName'. Diagram:\n$mermaid"
        )
    }

    @Then("the Mermaid diagram contains {string}")
    fun `mermaid diagram contains`(expected: String) {
        assertNotNull(vibecodingGraph, "VibecodingGraph should be instantiated")
        val mermaid = vibecodingGraph?.asMermaidDiagram() ?: ""
        assertTrue(
            mermaid.contains(expected),
            "Mermaid diagram should contain '$expected'. Diagram:\n$mermaid"
        )
    }

    @Then("the systemPrompt contains {string}")
    fun `system prompt contains`(expected: String) {
        assertTrue(
            world.systemPrompt.contains(expected, ignoreCase = true),
            "systemPrompt should contain '$expected'. Prompt:\n${world.systemPrompt}"
        )
    }

    @Given("a VibecodingState with maxActions {int}")
    fun `vibecoding state with max actions`(maxActions: Int) {
        world.state = codebase.koog.VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp/test",
            maxActions = maxActions
        )
    }

    @When("iteration {int} is reached")
    fun `iteration is reached`(iteration: Int) {
        var state = world.state ?: throw IllegalStateException("State not initialized")
        for (i in 1..iteration) {
            state = state.nextIteration()
        }
        world.state = state
    }

    @Then("the state is NOT final")
    fun `state is not final`() {
        val state = world.state ?: throw IllegalStateException("State not initialized")
        assertTrue(!state.isFinal, "State with iteration ${state.iteration}/${state.maxActions} should NOT be final")
    }

    @Then("the state IS final")
    fun `state is final`() {
        val state = world.state ?: throw IllegalStateException("State not initialized")
        assertTrue(state.isFinal, "State with iteration ${state.iteration}/${state.maxActions} should be final")
    }

    @Then("the augmented context graph is not null")
    fun `augmented context graph is not null`() {
        assertNotNull(vibecodingGraph, "VibecodingGraph should be instantiated")
        assertNotNull(vibecodingGraph!!.augmentedGraph, "Augmented context graph should not be null")
    }
}
