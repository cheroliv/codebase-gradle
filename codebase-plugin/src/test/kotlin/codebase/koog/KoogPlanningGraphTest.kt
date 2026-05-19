package codebase.koog

import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KoogPlanningGraphTest {

    @Test
    fun `build planning graph with koog DSL`() = runBlocking {
        val planGraph = strategy<String, String>("planning-test") {
            val classifyNode by node<String, String> { input ->
                if (input.length > 20) "complexe" else "simple"
            }
            val simpleNode by node<String, String> { input ->
                "handled-simply: $input"
            }
            val complexNode by node<String, String> { input ->
                "handled-with-care: $input"
            }

            // edge() est une methode du receiver builder — pas un import standalone
            edge(nodeStart forwardTo classifyNode onCondition { input: String -> input.isNotEmpty() } transformed { it })
            edge(classifyNode forwardTo simpleNode onCondition { it == "simple" } transformed { it })
            edge(classifyNode forwardTo complexNode onCondition { it == "complexe" } transformed { it })
            edge(simpleNode forwardTo nodeFinish onCondition { it.startsWith("handled-simply") } transformed { it })
            edge(complexNode forwardTo nodeFinish onCondition { it.startsWith("handled-with-care") } transformed { it })
        }

        assertNotNull(planGraph)
        val mermaid = planGraph.asMermaidDiagram()
        assertNotNull(mermaid)
        assertTrue(mermaid.contains("classifyNode"), "Mermaid should contain classifyNode")
        assertTrue(mermaid.contains("simpleNode"), "Mermaid should contain simpleNode")
        assertTrue(mermaid.contains("complexNode"), "Mermaid should contain complexNode")
    }

    @Test
    fun `planning graph mirrors current PlanningGraph structure`() = runBlocking {
        val planGraph = strategy<String, String>("planning") {
            val classify by node<String, String> { input -> input }
            val flashDecompose by node<String, String> { input -> "flash: $input" }
            val proDecompose by node<String, String> { input -> "pro: $input" }
            val format by node<String, String> { input -> "formatted($input)" }

            edge(nodeStart forwardTo classify onCondition { _: String -> true } transformed { it })
            edge(classify forwardTo flashDecompose onCondition { it == "simple" } transformed { it })
            edge(classify forwardTo proDecompose onCondition { it == "complexe" } transformed { it })
            edge(flashDecompose forwardTo format transformed { it })
            edge(proDecompose forwardTo format transformed { it })
            edge(format forwardTo nodeFinish onCondition { _: String -> true } transformed { it })
        }

        assertNotNull(planGraph)
        val mermaid = planGraph.asMermaidDiagram()
        assertNotNull(mermaid)
        assertTrue(mermaid.contains("classify"), "Mermaid missing classify node")
        assertTrue(mermaid.contains("flashDecompose"), "Mermaid missing flashDecompose node")
        assertTrue(mermaid.contains("proDecompose"), "Mermaid missing proDecompose node")
        assertTrue(mermaid.contains("format"), "Mermaid missing format node")
        // koog Mermaid: verifie la presence d'aretes conditionnelles (syntaxe natif Mermaid, pas "onCondition")
        assertTrue(mermaid.contains("-->"), "Mermaid should contain conditional edges")
    }
}
