package codebase.koog

import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import kotlinx.coroutines.runBlocking

class VibecodingGraph(
    val augmentedGraph: KoogAugmentedContextGraph = KoogAugmentedContextGraph()
) {

    val graph: AIAgentGraphStrategy<VibecodingState, VibecodingState> = strategy<VibecodingState, VibecodingState>(
        name = "vibecoding",
        toolSelectionStrategy = ToolSelectionStrategy.NONE
    ) {
        val buildContext by node<VibecodingState, VibecodingState> { state ->
            state // TODO: delegate to augmentedGraph
        }

        val classify by node<VibecodingState, VibecodingState> { state ->
            state // TODO: delegate to augmentedGraph
        }

        val plan by node<VibecodingState, VibecodingState> { state ->
            state // TODO: delegate to augmentedGraph
        }

        val executeTools by node<VibecodingState, VibecodingState> { state ->
            state // TODO
        }

        val sendToolResult by node<VibecodingState, VibecodingState> { state ->
            if (state.dryRun) state.finish() else state.copy(finished = true)
        }

        edge(nodeStart forwardTo buildContext onCondition { _ -> true } transformed { it })
        edge(buildContext forwardTo classify onCondition { _ -> true } transformed { it })
        edge(classify forwardTo plan onCondition { _ -> true } transformed { it })
        edge(plan forwardTo executeTools onCondition { _ -> true } transformed { it })
        edge(executeTools forwardTo sendToolResult onCondition { _ -> true } transformed { it })
        edge(sendToolResult forwardTo nodeFinish onCondition { _ -> true } transformed { it })
    }

    fun asMermaidDiagram(): String = runBlocking { graph.asMermaidDiagram() }

    fun buildSystemPrompt(state: VibecodingState): String = """
        |You are a Vibecoding Agent operating in a Gradle workspace.
        |
        |Intention: ${state.intention}
        |Plan: ${state.planJson.take(2000)}
        |WorkspaceRoot: ${state.workspaceRoot}
        |DryRun: ${state.dryRun}
        |Iteration: ${state.iteration} / ${state.maxActions}
        |
        |Use available tools to execute the plan step by step.
        """.trimMargin()

    fun execute(initialState: VibecodingState): VibecodingState {
        var state = initialState

        state = buildContextNode(state)
        if (state.error != null) return state

        state = classifyNode(state)
        if (state.error != null) return state

        state = planNode(state)
        if (state.error != null) return state

        state = executeToolsNode(state)

        if (state.dryRun) {
            return state.finish()
        }

        return state
    }

    private fun buildContextNode(state: VibecodingState): VibecodingState {
        val augmentedState = AugmentedState(
            intention = state.intention,
            workspaceRoot = state.workspaceRoot
        )
        return try {
            val result = augmentedGraph.execute(augmentedState)
            if (result.error != null) state.withError(result.error)
            else state
        } catch (e: Exception) {
            state.withError("buildContext failed: ${e.message}")
        }
    }

    private fun classifyNode(state: VibecodingState): VibecodingState {
        val augmentedState = AugmentedState(
            intention = state.intention,
            workspaceRoot = state.workspaceRoot
        )
        val result = augmentedGraph.execute(augmentedState)
        return state.copy(classification = result.classification)
    }

    private fun planNode(state: VibecodingState): VibecodingState {
        val augmentedState = AugmentedState(
            intention = state.intention,
            workspaceRoot = state.workspaceRoot
        )
        val result = augmentedGraph.execute(augmentedState)
        return state.withPlan(
            planJson = result.planJson,
            plan = result.plan,
            classification = result.classification
        )
    }

    private fun executeToolsNode(state: VibecodingState): VibecodingState {
        // V-1: graph structure only — tool execution loop implemented in V-2
        return state
    }
}
