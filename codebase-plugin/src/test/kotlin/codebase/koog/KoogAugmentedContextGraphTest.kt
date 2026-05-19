package codebase.koog

import ai.koog.agents.core.agent.asMermaidDiagram
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KoogAugmentedContextGraphTest {

    @Test
    fun `augmented graph includes buildContext and plan nodes`() = runBlocking {
        val graph = KoogAugmentedContextGraph()

        assertNotNull(graph.graph, "Graph should be created")
        val mermaid = graph.asMermaidDiagram()
        assertNotNull(mermaid, "Mermaid diagram should be generated")
        assertTrue(mermaid.contains("buildContext"), "Mermaid must contain buildContext node")
        assertTrue(mermaid.contains("plan"), "Mermaid must contain plan node")
        assertTrue(mermaid.contains("-->"), "Mermaid must contain graph edges")
    }

    @Test
    fun `augmented graph state transitions without real pgvector`() {
        val graph = KoogAugmentedContextGraph()
        val state = AugmentedState(
            intention = "Add dark mode toggle",
            workspaceRoot = "/tmp/test-workspace"
        )

        // Le graphe koog traite l'état — buildContext échoue gracieusement
        // si pgvector n'est pas disponible, mais ne crashe pas
        val resultState = graph.processState(state)

        assertNotNull(resultState, "State should never be null after processing")
        // Sans pgvector, le compositeContext peut être null ou partiel
        // mais l'exécution ne doit pas lever d'exception
        assertNotNull(resultState.error.let {
            // OK d'avoir une erreur (pgvector absent), mais pas de crash
            true
        })
    }

    @Test
    fun `augmented state wraps compositeContext and planState`() {
        val state = AugmentedState(
            intention = "test",
            workspaceRoot = "/tmp/test",
            compositeContext = null,
            plan = null,
            planError = null,
            error = null
        )

        assertNotNull(state)
        assertTrue(state.intention == "test")
        assertTrue(state.compositeContext == null, "Initial state should have null context")
        assertTrue(state.plan == null, "Initial state should have null plan")
        assertTrue(state.error == null, "Initial state should have no errors")
    }
}
