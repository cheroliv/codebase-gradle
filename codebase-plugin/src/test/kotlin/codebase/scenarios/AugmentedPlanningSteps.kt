package codebase.scenarios

import codebase.koog.AugmentedState
import codebase.koog.KoogAugmentedContextGraph
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step Definitions Cucumber pour le pipeline KoogAugmentedContextGraph.
 *
 * Pattern aligné sur les steps codebase existantes (PrepareContextSteps, MetadataContractSteps, etc.) :
 * champs d'instance + @Before, pas d'injection PicoContainer.
 *
 * L-3 : test du pipeline complet buildContext → classify → plan.
 */
class AugmentedPlanningSteps {

    private val log = LoggerFactory.getLogger(AugmentedPlanningSteps::class.java)
    private var world = AugmentedPlanningWorld()

    @Before
    fun setup() {
        world.reset()
        world.workspaceRoot.mkdirs()
        world.graph = KoogAugmentedContextGraph()
        log.info("AugmentedPlanningSteps setup OK — workspace : {}", world.workspaceRoot)
    }

    // ── Given ──

    @Given("a KoogAugmentedContextGraph is instantiated")
    fun `koog augmented context graph instantiated`() {
        assertNotNull(world.graph, "Graph should be instantiated by @Before setup")
    }

    @Given("a temporary workspace root {string} is created")
    fun `temporary workspace root created`(path: String) {
        world.workspaceRoot = File(path)
        world.workspaceRoot.mkdirs()
        assertTrue(world.workspaceRoot.isDirectory, "Workspace root should be a directory")
    }

    // ── When ──

    @When("I execute the augmented context pipeline with intention {string}")
    fun `execute augmented context pipeline`(intention: String) {
        val graph = world.graph
        assertNotNull(graph, "Graph must be initialized before execution")
        world.intention = intention

        val initialState = AugmentedState(
            intention = intention,
            workspaceRoot = world.workspaceRoot.absolutePath
        )

        world.resultState = graph.execute(initialState)
        assertNotNull(world.resultState, "Result state should never be null")
        log.info(
            "Pipeline executed — intention='{}', classification='{}', error={}",
            intention,
            world.resultState?.classification,
            world.resultState?.error ?: "none"
        )
    }

    @When("I execute the augmented context pipeline with intention {string} and workspace {string}")
    fun `execute augmented context pipeline with workspace`(intention: String, workspace: String) {
        val graph = world.graph
        assertNotNull(graph, "Graph must be initialized before execution")
        world.intention = intention
        world.workspaceRoot = File(workspace)
        world.workspaceRoot.mkdirs()

        val initialState = AugmentedState(
            intention = intention,
            workspaceRoot = world.workspaceRoot.absolutePath
        )

        world.resultState = graph.execute(initialState)
        assertNotNull(world.resultState, "Result state should never be null")
    }

    // ── Then ──

    @Then("the result state is not null")
    fun `result state is not null`() {
        assertNotNull(world.resultState, "Result state should be non-null after execution")
    }

    @Then("the classification is {string}")
    fun `classification is`(expected: String) {
        val state = world.resultState
        assertNotNull(state, "Result state should exist")
        val classification = state.classification
        assert(
            classification == expected
        ) { "Expected classification '$expected', got '$classification'" }
    }

    @Then("the error field indicates context is unavailable or partial")
    fun `error field indicates context unavailable or partial`() {
        val state = world.resultState
        assertNotNull(state, "Result state should exist")
        val hasError = state.error != null || state.planError != null
        assertTrue(
            hasError,
            "Without pgvector, error or planError should be set — got error='${state.error}', planError='${state.planError}'"
        )
    }

    @Then("a Mermaid diagram is generated")
    fun `mermaid diagram is generated`() {
        val graph = world.graph
        assertNotNull(graph, "Graph must exist")
        val diagram = graph.asMermaidDiagram()
        assertNotNull(diagram, "Mermaid diagram should not be null")
        assertTrue(diagram.contains("augmented-planning"), "Diagram should contain strategy name")
        assertTrue(diagram.contains("buildContext"), "Diagram should contain buildContext node")
        assertTrue(diagram.contains("classify"), "Diagram should contain classify node")
        assertTrue(diagram.contains("plan"), "Diagram should contain plan node")
    }

    @Then("the intention is preserved in the result state")
    fun `intention is preserved in result state`() {
        val state = world.resultState
        assertNotNull(state, "Result state should exist")
        assertTrue(
            state.intention == world.intention,
            "Intention should be preserved — expected '${world.intention}', got '${state.intention}'"
        )
    }
}
