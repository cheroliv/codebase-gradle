package codebase.scenarios

import codebase.koog.VibecodingState
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step Definitions Cucumber pour le pipeline VibecodingGraph.
 *
 * Pattern PicoContainer standardisé — aligné sur AugmentedPlanningSteps.
 * Toutes les étapes sont préfixées "vibecoding" pour éviter les conflits
 * avec les autres step classes (Cucumber interdit les doublons dans un même runner).
 */
class VibecodingSteps(private val world: VibecodingWorld) {

    // ── Given ──

    @Given("a VibecodingGraph is instantiated without augmented graph")
    fun `vibecoding graph instantiated`() {
        assertNotNull(world.graph, "Graph should be lazily instantiated via PicoContainer world")
    }

    // ── When ──

    @When("I execute vibecoding with intention {string} and dryRun {word}")
    fun `execute vibecoding with dryRun`(intention: String, dryRunFlag: String) {
        world.intention = intention
        world.dryRun = dryRunFlag.toBooleanStrictOrNull() ?: false

        val state = VibecodingState(
            intention = intention,
            workspaceRoot = "/tmp",
            dryRun = world.dryRun,
            maxActions = world.maxActions
        )

        world.resultState = world.graph.execute(state)
        assertNotNull(world.resultState, "Result state should never be null")
    }

    @When("I execute vibecoding with intention {string} and maxActions {int}")
    fun `execute vibecoding with maxActions`(intention: String, maxActions: Int) {
        world.intention = intention
        world.maxActions = maxActions

        val state = VibecodingState(
            intention = intention,
            workspaceRoot = "/tmp",
            maxActions = maxActions
        )

        world.resultState = world.graph.execute(state)
        assertNotNull(world.resultState, "Result state should never be null")
    }

    @When("I execute vibecoding with intention {string}")
    fun `execute vibecoding simple`(intention: String) {
        world.intention = intention

        val state = VibecodingState(
            intention = intention,
            workspaceRoot = "/tmp"
        )

        world.resultState = world.graph.execute(state)
        assertNotNull(world.resultState, "Result state should never be null")
    }

    @When("I request the Mermaid diagram from vibecoding")
    fun `request mermaid diagram`() {
        world.mermaidDiagram = world.graph.asMermaidDiagram()
        assertNotNull(world.mermaidDiagram)
    }

    // ── Then ──

    @Then("the vibecoding result state is not null")
    fun `vibecoding result state is not null`() {
        assertNotNull(world.resultState, "Result state should not be null")
    }

    @Then("the vibecoding dry run flag is true")
    fun `vibecoding dry run flag is true`() {
        assertTrue(world.resultState?.dryRun == true, "Dry run flag should be true")
    }

    @Then("vibecoding had no error")
    fun `vibecoding no error occurred`() {
        assertTrue(world.resultState?.error == null,
            "No error should occur: ${world.resultState?.error}")
    }

    @Then("the vibecoding intention {string} is preserved in the result state")
    fun `vibecoding intention is preserved`(expected: String) {
        assertTrue(world.resultState?.intention == expected,
            "Intention should be '$expected' but was '${world.resultState?.intention}'")
    }

    @Then("the vibecoding result state iteration is at most {int}")
    fun `vibecoding iteration is at most`(max: Int) {
        val iter = world.resultState?.iteration ?: -1
        assertTrue(iter <= max, "Iteration $iter should be at most $max")
    }

    @Then("the vibecoding result state is finished or final")
    fun `vibecoding result state is finished or final`() {
        val state = world.resultState
        assertTrue(state != null, "Result state should not be null")
        assertTrue(state.isFinal || state.finished,
            "State should be finished or final (isFinal=${state.isFinal}, finished=${state.finished}, iteration=${state.iteration})")
    }

    @Then("the vibecoding classification is {string}")
    fun `vibecoding classification is`(expected: String) {
        val state = world.resultState
        assertTrue(state != null, "Result state should not be null")
        assertTrue(state.classification == expected,
            "Classification should be '$expected' but was '${state.classification}'")
    }

    @Then("the vibecoding diagram contains {string} and {string} and {string}")
    fun `vibecoding diagram contains nodes`(node1: String, node2: String, node3: String) {
        val diagram = world.mermaidDiagram
        assertTrue(diagram.contains(node1), "Mermaid missing '$node1'")
        assertTrue(diagram.contains(node2), "Mermaid missing '$node2'")
        assertTrue(diagram.contains(node3), "Mermaid missing '$node3'")
    }

    @Then("the vibecoding iteration count is greater than or equal to {int}")
    fun `vibecoding iteration count is ge`(min: Int) {
        val iter = world.resultState?.iteration ?: -1
        assertTrue(iter >= min, "Iteration $iter should be >= $min")
    }
}
