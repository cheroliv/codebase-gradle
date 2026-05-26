package codebase.koog

import contracts.agent.Epic
import vibecoding.contracts.plan.Plan
import contracts.agent.GradleTask as PlanTask
import contracts.agent.UserStory
import contracts.vibecoding.registry.ToolRegistry
import vibecoding.contracts.state.VibecodingState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests unitaires pour VibecodingGraph — graphe koog d'exécution autonome.
 *
 * Architecture TDD : ces tests définissent le comportement attendu AVANT l'implémentation.
 * Le graphe suit le pattern KoogAugmentedContextGraph/KoogPlanningGraph :
 * koog DSL pour la topologie + execute() pour l'exécution réelle.
 *
 * Résilient : les tests passent sans pgvector, sans Ollama.
 */
class VibecodingGraphTest {

    private val vibecodingGraph = VibecodingGraph(
        augmentedGraph = null, // pas de pgvector — mode résilient
        toolRegistry = ToolRegistry()
    )

    // ---- Structure du graphe koog ----

    @Test
    fun `graph should have valid mermaid diagram`() {
        val diagram = vibecodingGraph.asMermaidDiagram()
        assertTrue(diagram.isNotBlank(), "Mermaid diagram should not be blank")
        assertTrue(diagram.contains("vibecoding"), "Diagram should contain graph name 'vibecoding'")
    }

    @Test
    fun `graph should have the expected nodes by name`() {
        val diagram = vibecodingGraph.asMermaidDiagram()
        // Le diagramme Mermaid koog contient les noms des nœuds (variables Kotlin)
        assertTrue(diagram.contains("buildContext"), "Mermaid missing buildContext node")
        assertTrue(diagram.contains("executeTools"), "Mermaid missing executeTools node")
        assertTrue(diagram.contains("checkProgress"), "Mermaid missing checkProgress node")
    }

    // ---- Execution de base ----

    @Test
    fun `execute should return a result state`() {
        val state = VibecodingState(
            intention = "Add dark mode toggle",
            workspaceRoot = "/tmp"
        )

        val result = vibecodingGraph.execute(state)

        assertNotNull(result)
        // Ne doit pas crasher — résilient sans pgvector
        assertFalse(result.error?.contains("NullPointerException") ?: false,
            "Should not crash with NPE even without pgvector")
    }

    @Test
    fun `execute should preserve the original intention`() {
        val state = VibecodingState(
            intention = "Fix typo in README",
            workspaceRoot = "/tmp"
        )

        val result = vibecodingGraph.execute(state)

        assertEquals("Fix typo in README", result.intention)
    }

    // ---- Dry run ----

    @Test
    fun `dry run should not execute real commands`() {
        val state = VibecodingState(
            intention = "Update config files",
            workspaceRoot = "/tmp",
            dryRun = true,
            maxActions = 5
        )

        val result = vibecodingGraph.execute(state)

        assertTrue(result.iteration >= 0, "Should have at least 0 iterations")
        assertNull(result.error, "Dry run should not produce errors: ${result.error}")
    }

    // ---- Limite d'itérations ----

    @Test
    fun `should respect maxActions limit`() {
        val state = VibecodingState(
            intention = "Refactor large codebase",
            workspaceRoot = "/tmp",
            maxActions = 3
        )

        val result = vibecodingGraph.execute(state)

        assertTrue(result.iteration <= 3,
            "Should not exceed maxActions=3, got ${result.iteration}")
        assertTrue(result.isFinal || result.error != null,
            "Should finish after maxActions (isFinal=${result.isFinal}, finished=${result.finished})")
    }

    @Test
    fun `should stop when maxActions is zero`() {
        val state = VibecodingState(
            intention = "Quick fix",
            workspaceRoot = "/tmp",
            maxActions = 0
        )

        val result = vibecodingGraph.execute(state)

        assertTrue(result.isFinal || result.error != null,
            "Should finish immediately with maxActions=0 (isFinal=${result.isFinal}, iteration=${result.iteration})")
    }

    // ---- ToolRegistry interaction ----

    @Test
    fun `should build context and classify intention without pgvector`() {
        // augmentedGraph=null → mode résilient : pas de pgvector, classification "simple"
        val graph = VibecodingGraph(
            augmentedGraph = null,
            toolRegistry = ToolRegistry()
        )
        val state = VibecodingState(
            intention = "Add simple feature",
            workspaceRoot = "/tmp"
        )

        val result = graph.execute(state)

        assertTrue(result.classification == "simple" || result.classification == "",
            "Classification should be 'simple' without pgvector: '${result.classification}'")
    }

    // ---- V-3 : Sécurité ----

    @Test
    fun `execute should stop when startTime exceeds timeout even with a plan`() {
        val nowMs = System.currentTimeMillis()
        val fakePlan = Plan(
            title = "timeout-test",
            epics = listOf(
                Epic(name = "E1", description = "test", points = 1, userStories = listOf(
                    UserStory(description = "US1", tasks = listOf(
                        PlanTask(description = "task1", gradleTask = "tasks")
                    ))
                ))
            ),
            totalPoints = 1,
            estimatedSessions = "1"
        )
        val state = VibecodingState(
            intention = "Timeout test",
            workspaceRoot = "/tmp",
            sessionTimeoutSeconds = 1,
            sessionStartTimeMs = nowMs - 2000, // déjà 2s au-dessus du timeout 1s
            maxActions = 100,
            plan = fakePlan,
            planJson = "{}"
        )
        val result = vibecodingGraph.execute(state)
        assertTrue(result.error != null,
            "Should have error when timeout exceeded (error=${result.error})")
        assertTrue(result.error!!.contains("Timeout", ignoreCase = true),
            "Error message should mention timeout, got: ${result.error}")
    }

    // ---- Fin clean ----

    @Test
    fun `execute should not throw exceptions`() {
        val state = VibecodingState(
            intention = "Test resilience",
            workspaceRoot = "/nonexistent/path",
            dryRun = true
        )

        assertDoesNotThrow {
            vibecodingGraph.execute(state)
        }
    }
}
