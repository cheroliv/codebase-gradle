package codebase.koog

import ai.koog.agents.core.agent.asMermaidDiagram
import vibecoding.contracts.state.AugmentedState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KoogAugmentedContextGraphTest {

    @Test
    fun `graph includes buildContext classify and plan nodes`() = runBlocking {
        val graph = KoogAugmentedContextGraph()

        assertNotNull(graph.graph, "Graph should be created")
        val mermaid = graph.asMermaidDiagram()
        assertNotNull(mermaid, "Mermaid diagram should be generated")
        assertTrue(mermaid.contains("buildContext"), "Mermaid must contain buildContext node")
        assertTrue(mermaid.contains("classify"), "Mermaid must contain classify node")
        assertTrue(mermaid.contains("plan"), "Mermaid must contain plan node")
        assertTrue(mermaid.contains("-->"), "Mermaid must contain graph edges")
    }

    @Test
    fun `execute state transitions without pgvector does not crash`() {
        val graph = KoogAugmentedContextGraph()
        val state = AugmentedState(
            intention = "Add dark mode toggle",
            workspaceRoot = "/tmp/test-workspace"
        )

        // L-3 : execute() remplace processState()
        // Sans pgvector, buildContext échoue gracieusement — pas de crash
        val resultState = graph.execute(state)

        assertNotNull(resultState, "State should never be null after processing")
        assertTrue(resultState.intention == "Add dark mode toggle")
        // Sans pgvector, le compositeContext peut être null ou partiel
        // mais l'exécution ne doit pas lever d'exception
        assertTrue(true) // si on arrive ici, pas de crash
    }

    @Test
    fun `execute classifies simple intention correctly`() {
        val graph = KoogAugmentedContextGraph()
        val state = AugmentedState(
            intention = "Add dark mode toggle",
            workspaceRoot = "/tmp/test-workspace"
        )

        val resultState = graph.execute(state)

        assertNotNull(resultState)
        // Sans pgvector : classification = "simple" (fallback)
        assertTrue(
            resultState.classification == "simple",
            "Short intention without pgvector should classify as 'simple'"
        )
    }

    @Test
    fun `execute classifies complex cross-borough intention`() {
        val graph = KoogAugmentedContextGraph()
        val state = AugmentedState(
            intention = "Refactor cross-borough DAG N1→N2→N3 pour intégration multi-plugins avec architecture distribuée",
            workspaceRoot = "/tmp/test-workspace"
        )

        val resultState = graph.execute(state)

        assertNotNull(resultState)
        // Heuristique : longueur >80 ou mots-clés cross-borough/multi-plugins/architecture → "complexe"
        assertTrue(
            resultState.classification == "complexe",
            "Long cross-borough intention should classify as 'complexe'"
        )
    }

    @Test
    fun `augmented state wraps compositeContext and plan fields`() {
        val state = AugmentedState(
            intention = "test",
            workspaceRoot = "/tmp/test",
            compositeContext = null,
            plan = null,
            planError = null,
            error = null
        )

        assertNotNull(state)
        assertEquals("test", state.intention)
        assertNull(state.compositeContext, "Initial state should have null context")
        assertNull(state.plan, "Initial state should have null plan")
        assertNull(state.error, "Initial state should have no errors")
    }
}
