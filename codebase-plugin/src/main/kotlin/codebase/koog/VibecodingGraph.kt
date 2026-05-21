package codebase.koog

import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Graphe koog d'exécution vibecoding — pipeline autonome.
 *
 * Architecture (pattern koog+langchain4j) :
 * ```
 * buildContext → executeAction (loop) → checkProgress ─┬─→ executeAction (↺)
 *                                                        └─→ finish
 * ```
 *
 * - **buildContext** : appelle KoogAugmentedContextGraph (RAG/pgvector + classification + plan)
 * - **executeAction** : exécute une action via ToolRegistry (itère sur le plan)
 * - **checkProgress** : vérifie si fini (maxActions, erreur, plan vide) ou continue la boucle
 *
 * koog orchestre, langchain4j exécute (RAG/LLM). ToolRegistry pour les actions filesystem/shell.
 */
class VibecodingGraph(
    val augmentedGraph: KoogAugmentedContextGraph? = null,
    val toolRegistry: ToolRegistry = ToolRegistry()
) {

    private val log = LoggerFactory.getLogger(VibecodingGraph::class.java)

    /**
     * Graphe koog déclaratif — nœuds réels (pas de stubs).
     * Chaque nœud délègue à la même méthode privée que execute().
     */
    val graph: AIAgentGraphStrategy<VibecodingState, VibecodingState> = strategy<VibecodingState, VibecodingState>(
        name = "vibecoding",
        toolSelectionStrategy = ToolSelectionStrategy.NONE
    ) {
        val buildContext by node<VibecodingState, VibecodingState> { state ->
            buildContextNode(state)
        }

        val executeAction by node<VibecodingState, VibecodingState> { state ->
            executeActionNode(state)
        }

        val checkProgress by node<VibecodingState, VibecodingState> { state ->
            checkProgressNode(state)
        }

        edge(nodeStart forwardTo buildContext onCondition { _ -> true } transformed { it })
        edge(buildContext forwardTo executeAction onCondition { _ -> true } transformed { it })
        edge(executeAction forwardTo checkProgress onCondition { _ -> true } transformed { it })
        edge(checkProgress forwardTo executeAction onCondition { state ->
            !state.finished && !state.isFinal && state.error == null
        } transformed { it })
        edge(checkProgress forwardTo nodeFinish onCondition { state ->
            state.finished || state.isFinal || state.error != null
        } transformed { it })
    }

    /**
     * Point d'entrée principal — pipeline complet.
     * Résilient : chaque étape catch ses erreurs.
     * Si maxActions=0 (ou déjà isFinal), retourne immédiatement.
     */
    fun execute(initialState: VibecodingState): VibecodingState {
        // Court-circuit : si déjà final, retour immédiat
        if (initialState.isFinal) {
            log.info("[VibecodingGraph] initialState is already final (iteration=${initialState.iteration}, maxActions=${initialState.maxActions})")
            return initialState.finish()
        }

        // Étape 1 : buildContext
        var state = try {
            buildContextNode(initialState)
        } catch (e: Exception) {
            log.warn("[VibecodingGraph] buildContext failed: {}", e.message)
            initialState.withError("BuildContextFailed: ${e.message}")
        }
        if (state.error != null) return state
        if (state.isFinal) return state.finish()

        // Étape 2 : boucle d'exécution
        while (!state.finished && !state.isFinal && state.error == null) {
            state = try {
                executeActionNode(state)
            } catch (e: Exception) {
                log.warn("[VibecodingGraph] executeAction failed: {}", e.message)
                state.withError("ExecuteActionFailed: ${e.message}")
            }
            if (state.error != null) return state

            state = checkProgressNode(state)
        }

        return if (state.isFinal) state.finish() else state
    }

    fun asMermaidDiagram(): String = runBlocking { graph.asMermaidDiagram() }

    // === Méthodes privées — partagées entre le graphe koog et execute() ===

    /**
     * Nœud 1 : buildContext — pipeline KoogAugmentedContextGraph (optionnel).
     * Si aucun graphe augmenté n'est fourni, ou si pgvector est down,
     * le contexte est vide mais le pipeline continue (mode résilient).
     */
    private fun buildContextNode(state: VibecodingState): VibecodingState {
        if (augmentedGraph == null) {
            log.info("[VibecodingGraph] No augmented graph — skipping buildContext")
            return state.withPlan(planJson = "", plan = null, classification = "simple")
        }
        val augmentedState = AugmentedState(
            intention = state.intention,
            workspaceRoot = state.workspaceRoot
        )
        return try {
            val result = augmentedGraph.execute(augmentedState)
            if (result.error != null) {
                log.info("[VibecodingGraph] buildContext returned error: {}", result.error)
                state.withPlan(planJson = "", plan = null, classification = "simple")
            } else {
                state.withPlan(
                    planJson = result.planJson,
                    plan = result.plan,
                    classification = result.classification
                )
            }
        } catch (e: Exception) {
            log.warn("[VibecodingGraph] buildContext exception: {}", e.message)
            state.withPlan(planJson = "", plan = null, classification = "simple")
        }
    }

    /**
     * Nœud 2 : executeAction — exécute une action du plan via ToolRegistry.
     * En dryRun : ne fait qu'incrémenter le compteur.
     * Sans plan : incrémente et continue (mode résilient).
     */
    private fun executeActionNode(state: VibecodingState): VibecodingState {
        if (state.dryRun) {
            log.info("[VibecodingGraph] dryRun iteration ${state.iteration + 1}/${state.maxActions}")
            return state.nextIteration().copy(
                lastToolResult = "DRY RUN: iteration ${state.iteration + 1}"
            )
        }

        // Cherche la prochaine tâche à exécuter dans le plan
        val plan = state.plan
        // Si pas de plan ou plan vide, on itère simplement (mode résilient)
        if (plan == null || plan.epics.isEmpty()) {
            log.info("[VibecodingGraph] No plan to execute, iteration ${state.iteration + 1}/${state.maxActions}")
            return state.nextIteration().copy(
                lastToolResult = "No plan tasks to execute"
            )
        }

        // Exécute la prochaine tâche non faite
        val allTasks = plan.epics.flatMap { epic ->
            epic.userStories.flatMap { story -> story.tasks.map { task ->
                Triple(epic.name, story.description, task)
            }}
        }

        val nextIndex = state.executedTasks.size
        if (nextIndex >= allTasks.size) {
            // Toutes les tâches sont exécutées
            return state.copy(finished = true, lastToolResult = "All plan tasks executed")
        }

        val (epicName, storyDesc, task) = allTasks[nextIndex]
        log.info("[VibecodingGraph] Executing task: ${task.description} (${task.gradleTask})")

        return try {
            val result = toolRegistry.execute(
                toolName = "exec_gradle",
                arguments = mapOf("task" to task.gradleTask),
                workspaceRoot = state.workspaceRoot,
                dryRun = state.dryRun
            )
            state.nextIteration().copy(
                executedTasks = state.executedTasks + task.description,
                lastToolResult = result,
                currentTaskDescription = task.description
            )
        } catch (e: Exception) {
            log.warn("[VibecodingGraph] Task execution failed: {}", e.message)
            state.nextIteration().copy(
                executedTasks = state.executedTasks + task.description,
                lastToolResult = "Failed: ${e.message}",
                currentTaskDescription = task.description,
                error = "TaskFailed: ${e.message}"
            )
        }
    }

    /**
     * Nœud 3 : checkProgress — vérifie si le travail est terminé.
     */
    private fun checkProgressNode(state: VibecodingState): VibecodingState {
        if (state.error != null) return state.finish()
        if (state.iteration >= state.maxActions) return state.finish()
        if (state.finished) return state

        // Vérifie si le plan est vide (rien à faire)
        val noWorkRemaining = state.plan?.epics?.isEmpty() ?: true
        if (noWorkRemaining && state.iteration > 0) return state.finish()

        return state
    }
}
