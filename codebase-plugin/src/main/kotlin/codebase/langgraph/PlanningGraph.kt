package codebase.langgraph

import codebase.rag.CompositeContext
import org.bsc.langgraph4j.StateGraph
import org.bsc.langgraph4j.StateGraph.END
import org.bsc.langgraph4j.StateGraph.START
import org.bsc.langgraph4j.action.AsyncEdgeAction
import org.bsc.langgraph4j.action.AsyncNodeAction
import org.bsc.langgraph4j.action.EdgeAction
import org.bsc.langgraph4j.action.NodeAction
import org.bsc.langgraph4j.state.AgentStateFactory

class PlanningGraph {

    private val graph: StateGraph<PlanningState>

    init {
        val factory = AgentStateFactory<PlanningState> { PlanningState(mutableMapOf()) }

        val classifyNode: NodeAction<PlanningState> = ClassifyNode()
        val proDecomposeNode: NodeAction<PlanningState> = ProDecomposeNode()
        val flashDecomposeNode: NodeAction<PlanningState> = FlashDecomposeNode()
        val formatNode: NodeAction<PlanningState> = FormatNode()

        graph = StateGraph(factory)
            .addNode("classify", AsyncNodeAction.node_async(classifyNode))
            .addNode("pro_decompose", AsyncNodeAction.node_async(proDecomposeNode))
            .addNode("flash_decompose", AsyncNodeAction.node_async(flashDecomposeNode))
            .addNode("format", AsyncNodeAction.node_async(formatNode))
            .addEdge(START, "classify")
            .addConditionalEdges(
                "classify",
                AsyncEdgeAction.edge_async(EdgeAction { state: PlanningState -> state.classification }),
                mapOf(
                    "simple" to "flash_decompose",
                    "complexe" to "pro_decompose"
                )
            )
            .addEdge("pro_decompose", "format")
            .addEdge("flash_decompose", "format")
            .addEdge("format", END)
    }

    fun execute(intention: String, compositeContext: CompositeContext): PlanningState {
        val result = graph.compile().invoke(
            mutableMapOf(
                "intention" to intention,
                "compositeContext" to compositeContext
            )
        )
        return result.orElseThrow {
            IllegalStateException("PlanningGraph execution returned empty result")
        }
    }
}
