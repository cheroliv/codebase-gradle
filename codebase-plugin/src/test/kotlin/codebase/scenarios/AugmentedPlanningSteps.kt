package codebase.scenarios

import codebase.koog.AugmentedState
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step Definitions Cucumber pour le pipeline KoogAugmentedContextGraph.
 *
 * Pattern PicoContainer standardisé — aligné sur plantuml-gradle, slider-gradle,
 * bakery-gradle, readme-gradle : le World est injecté par constructeur.
 * PicoContainer crée une nouvelle instance par scénario → pas de @Before reset.
 *
 * L-3 : test du pipeline complet buildContext → classify → plan.
 */
class AugmentedPlanningSteps(private val world: AugmentedPlanningWorld) {

    // ── Given ──

    @Given("a KoogAugmentedContextGraph is instantiated")
    fun `koog augmented context graph instantiated`() {
        assertNotNull(world.graph, "Graph should be lazily instantiated via PicoContainer world")
    }

    @Given("a temporary workspace root {string} is created")
    fun `temporary workspace root created`(path: String) {
        val dir = File(path).also { it.mkdirs() }
        assertTrue(dir.isDirectory, "Workspace root should be a directory: $path")
    }

    // ── When ──

    @When("I execute the augmented context pipeline with intention {string}")
    fun `execute augmented context pipeline`(intention: String) {
        world.intention = intention

        val initialState = AugmentedState(
            intention = intention,
            workspaceRoot = world.workspaceRoot.absolutePath
        )

        world.resultState = world.graph.execute(initialState)
        assertNotNull(world.resultState, "Result state should never be null")
    }

    @When("I execute the augmented context pipeline with intention {string} and workspace {string}")
    fun `execute augmented context pipeline with workspace`(intention: String, workspace: String) {
        world.intention = intention
        val ws = File(workspace).also { it.mkdirs() }

        val initialState = AugmentedState(
            intention = intention,
            workspaceRoot = ws.absolutePath
        )

        world.resultState = world.graph.execute(initialState)
        assertNotNull(world.resultState, "Result state should never be null")
    }

    // ── Then ──

    @Then("the result state is not null")
    fun `result state is not null`() {
        assertNotNull(world.resultState, "Result state should be non-null after execution")
    }

    @Then("the classification is {string}")
    fun `classification is`(expected: String) {
        val classification = world.resultState?.classification
        assertNotNull(world.resultState, "Result state should exist")
        assertTrue(
            classification == expected,
            "Expected classification '$expected', got '$classification'"
        )
    }

    @Then("the error field indicates context is unavailable or partial")
    fun `error field indicates context unavailable or partial`() {
        val state = world.resultState
        assertNotNull(state, "Result state should exist")
        val hasError = state.error != null || state.planError != null
        assertTrue(
            hasError,
            "Without pgvector, error or planError should be set " +
                "(got error='${state.error}', planError='${state.planError}')"
        )
    }

    @Then("a Mermaid diagram is generated")
    fun `mermaid diagram is generated`() {
        val diagram = world.graph.asMermaidDiagram()
        assertNotNull(diagram, "Mermaid diagram should not be null")
        assertTrue(diagram.contains("augmented-planning"), "Diagram should contain strategy name")
        assertTrue(diagram.contains("buildContext"), "Diagram should contain buildContext node")
        assertTrue(diagram.contains("classify"), "Diagram should contain classify node")
        assertTrue(diagram.contains("plan"), "Diagram should contain plan node")
    }

    @Then("the intention is preserved in the result state")
    fun `intention is preserved in result state`() {
        assertNotNull(world.resultState, "Result state should exist")
        assertTrue(
            world.resultState!!.intention == world.intention,
            "Intention should be preserved — expected '${world.intention}', got '${world.resultState!!.intention}'"
        )
    }
}
