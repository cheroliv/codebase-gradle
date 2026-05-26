package codebase.koog

import codebase.koog.llm.LlmProvider
import codebase.koog.session.SessionRecord
import codebase.koog.session.SessionRepository
import codebase.koog.tracking.TokenTracker
import contracts.vibecoding.registry.ToolRegistry
import io.r2dbc.spi.ConnectionFactory
import vibecoding.contracts.state.AugmentedState
import vibecoding.contracts.state.VibecodingState
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
 * Architecture (pattern koog+langchain4j) — V-5 enriched :
 * ```
 * buildContext → callLLM → executeTools → persistState → checkProgress ─┬─→ callLLM (↺)
 *                                                                        └─→ finish
 * ```
 *
 * - **buildContext** : appelle KoogAugmentedContextGraph (RAG/pgvector + classification + plan)
 * - **callLLM** : décision LLM — quelle tâche exécuter (Gemini/Ollama/Fake)
 * - **executeTools** : exécute l'action décidée via ToolRegistry
 * - **persistState** : sauvegarde l'état via SessionRepository
 * - **checkProgress** : vérifie si fini (maxActions, erreur, plan vide) ou continue la boucle
 *
 * koog orchestre, langchain4j exécute (RAG/LLM). ToolRegistry pour les actions filesystem/shell.
 *
 * Rétrocompatibilité : si llmProvider est null → pas de callLLM (mode déterministe comme avant V-4).
 * Si sessionRepository ET connectionFactory sont null → pas de persistance.
 * Si connectionFactory est fourni → création automatique d'un SessionRepository interne.
 */
class VibecodingGraph(
    val augmentedGraph: KoogAugmentedContextGraph? = null,
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val llmProvider: LlmProvider? = null,
    val sessionRepository: SessionRepository? = null,
    val connectionFactory: ConnectionFactory? = null,
    val tokenTracker: TokenTracker = TokenTracker()
) {

    private val log = LoggerFactory.getLogger(VibecodingGraph::class.java)

    /** SessionRepository effectif : priorité injection explicite, sinon création depuis ConnectionFactory */
    private val effectiveSessionRepository: SessionRepository? by lazy {
        sessionRepository ?: connectionFactory?.let { SessionRepository(it) }
    }

    private var sessionId: String? = null

    /**
     * Graphe koog déclaratif — 5 nœuds (V-5 : callLLM + persistState ajoutés).
     */
    val graph: AIAgentGraphStrategy<VibecodingState, VibecodingState> = strategy<VibecodingState, VibecodingState>(
        name = "vibecoding",
        toolSelectionStrategy = ToolSelectionStrategy.NONE
    ) {
        val buildContext by node<VibecodingState, VibecodingState> { state ->
            buildContextNode(state)
        }

        val callLLM by node<VibecodingState, VibecodingState> { state ->
            callLLMNode(state)
        }

        val executeTools by node<VibecodingState, VibecodingState> { state ->
            executeToolsNode(state)
        }

        val persistState by node<VibecodingState, VibecodingState> { state ->
            persistStateNode(state)
        }

        val checkProgress by node<VibecodingState, VibecodingState> { state ->
            checkProgressNode(state)
        }

        edge(nodeStart forwardTo buildContext onCondition { _ -> true } transformed { it })
        edge(buildContext forwardTo callLLM onCondition { _ -> true } transformed { it })
        edge(callLLM forwardTo executeTools onCondition { _ -> true } transformed { it })
        edge(executeTools forwardTo persistState onCondition { _ -> true } transformed { it })
        edge(persistState forwardTo checkProgress onCondition { _ -> true } transformed { it })
        edge(checkProgress forwardTo callLLM onCondition { state ->
            !state.finished && !state.isFinal && state.error == null
        } transformed { it })
        edge(checkProgress forwardTo nodeFinish onCondition { state ->
            state.finished || state.isFinal || state.error != null
        } transformed { it })
    }

    /**
     * Point d'entrée principal — pipeline complet (V-5 : callLLM + persistState).
     * Résilient : chaque étape catch ses erreurs.
     * Si maxActions=0 (ou déjà isFinal), retourne immédiatement.
     */
    fun execute(initialState: VibecodingState): VibecodingState {
        // Court-circuit : si déjà final, retour immédiat
        if (initialState.isFinal) {
            log.info("[VibecodingGraph] initialState is already final (iteration=${initialState.iteration}, maxActions=${initialState.maxActions})")
            return initialState.finish()
        }

        // Vérification timeout avant de démarrer
        if (isTimedOut(initialState)) {
            log.warn("[VibecodingGraph] Session timed out before starting (elapsed=${elapsedSeconds(initialState)}s, timeout=${initialState.sessionTimeoutSeconds}s)")
            return initialState.withError("Timeout: session exceeded ${initialState.sessionTimeoutSeconds}s (elapsed ${elapsedSeconds(initialState)}s)")
        }

        // Persistance initiale — crée la session avant la boucle
        var state = initialState
        sessionId = try {
            runBlocking { effectiveSessionRepository?.createSession(state) }?.also {
                log.info("[VibecodingGraph] Session created: id={}", it)
            }
        } catch (e: Exception) {
            log.warn("[VibecodingGraph] createSession failed: {}", e.message)
            null
        }

        // Étape 1 : buildContext
        state = try {
            buildContextNode(state)
        } catch (e: Exception) {
            log.warn("[VibecodingGraph] buildContext failed: {}", e.message)
            state.withError("BuildContextFailed: ${e.message}")
        }
        if (state.error != null) return state
        if (state.isFinal) return state.finish()

        // Étape 2 : boucle d'exécution V-5 (callLLM + executeTools + persistState)
        while (!state.finished && !state.isFinal && state.error == null) {
            // Vérification timeout à chaque itération
            if (isTimedOut(state)) {
                log.warn("[VibecodingGraph] Session timed out during loop (elapsed=${elapsedSeconds(state)}s > timeout=${state.sessionTimeoutSeconds}s, iteration=${state.iteration})")
                state = state.withError("Timeout: session exceeded ${state.sessionTimeoutSeconds}s at iteration ${state.iteration}")
                return state
            }

            // S'il n'y a pas de plan, le LLM décide (ou on itère)
            val plan = state.plan
            if (plan == null || plan.epics.isEmpty()) {
                // Mode LLM : décision autonome
                if (llmProvider != null) {
                    state = try {
                        callLLMNode(state)
                    } catch (e: Exception) {
                        log.warn("[VibecodingGraph] callLLM failed: {}", e.message)
                        state.withError("CallLLMFailed: ${e.message}")
                    }
                    if (state.error != null) return state
                } else {
                    // Mode déterministe : pas de plan = itère
                    log.info("[VibecodingGraph] No plan, no LLM — iteration ${state.iteration + 1}/${state.maxActions}")
                    state = state.nextIteration()
                }
            } else {
                // Mode plan déterministe : executeTools sur les tâches du plan
                state = try {
                    executeToolsNode(state)
                } catch (e: Exception) {
                    log.warn("[VibecodingGraph] executeTools failed: {}", e.message)
                    state.withError("ExecuteToolsFailed: ${e.message}")
                }
                if (state.error != null) return state
            }

            // persistState après chaque itération
            state = try {
                persistStateNode(state)
            } catch (e: Exception) {
                log.warn("[VibecodingGraph] persistState failed: {}", e.message)
                state // ne casse pas la boucle sur erreur de persistence
            }

            state = checkProgressNode(state)
        }

        return if (state.isFinal) state.finish() else state
    }

    fun asMermaidDiagram(): String = runBlocking { graph.asMermaidDiagram() }

    // === Companion — helpers statiques ===

    companion object {
        /**
         * Reconstruit un [VibecodingState] depuis un [SessionRecord]
         * pour reprendre une session interrompue (--resume).
         *
         * Le state reprend à iteration=0 (le graphe redémarre) mais conserve
         * le plan, la classification, le workspaceRoot et l'intention.
         * Si la session était finie, le state est marqué finished.
         */
        fun resumeSession(record: SessionRecord): VibecodingState {
            val intentionWithId = "[Resume ${record.id}] ${record.intention}"
            return VibecodingState(
                intention = intentionWithId,
                workspaceRoot = record.workspaceRoot,
                dryRun = record.dryRun,
                maxActions = record.maxActions,
                iteration = 0,
                planJson = record.planJson ?: "",
                plan = null, // sera reconstruit par buildContext si nécessaire
                classification = record.classification,
                finished = record.finished,
                error = record.error
            )
        }
    }

    // === Méthodes privées — partagées entre le graphe koog et execute() ===

    /**
     * Nœud 1 : buildContext — pipeline KoogAugmentedContextGraph (optionnel).
     * Si aucun graphe augmenté n'est fourni, ou si pgvector est down,
     * le contexte est vide mais le pipeline continue (mode résilient).
     */
    private fun buildContextNode(state: VibecodingState): VibecodingState {
        // Si le state a déjà un plan (injecté directement), on le conserve
        if (state.plan != null) {
            log.info("[VibecodingGraph] State already has a plan — skipping buildContext")
            return state
        }
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
     * Nœud 2 (V-5) : callLLM — le LLM décide de la prochaine action.
     * Si llmProvider est null → identique au mode déterministe pré-V-5.
     */
    private fun callLLMNode(state: VibecodingState): VibecodingState {
        if (llmProvider == null) {
            return state // pas de LLM = on continue déterministe
        }

        val prompt = buildPromptForIteration(state)
        tokenTracker.trackPrompt(prompt)

        return try {
            val response = runBlocking { llmProvider.call(prompt) }
            tokenTracker.trackCompletion(response)
            log.info("[VibecodingGraph] LLM response: {} chars, first 80: {}", response.length, response.take(80))

            state.nextIteration().copy(
                lastToolResult = "LLM decided: $response"
            )
        } catch (e: Exception) {
            log.warn("[VibecodingGraph] LLM call failed: {}", e.message)
            state.withError("LLMCallFailed: ${e.message}")
        }
    }

    private fun buildPromptForIteration(state: VibecodingState): String {
        val statusLine = state.error?.let { "ERROR: $it" } ?: "OK"
        return buildString {
            appendLine("Vibecoding session — iteration ${state.iteration + 1}/${state.maxActions}")
            appendLine("Intention: ${state.intention}")
            appendLine("Workspace: ${state.workspaceRoot}")
            appendLine("Dry run: ${state.dryRun}")
            appendLine("Status: $statusLine")
            if (state.executedTasks.isNotEmpty()) {
                appendLine("Tasks done: ${state.executedTasks.joinToString(", ")}")
            }
            state.plan?.let { plan ->
                appendLine("Plan remaining tasks: ${plan.epics.sumOf { it.userStories.sumOf { s -> s.tasks.size } } - state.executedTasks.size}")
            }
            appendLine()
            appendLine("What should be the next action? Respond with a single tool name and parameters, or 'DONE' if finished.")
        }
    }

    /**
     * Nœud 3 : executeTools — exécute une action via ToolRegistry.
     * Alias de executeActionNode (rétrocompatibilité pré-V-5).
     * En dryRun : ne fait qu'incrémenter le compteur.
     * Sans plan : incrémente et continue (mode résilient).
     */
    private fun executeToolsNode(state: VibecodingState): VibecodingState {
        // dryRun : parcourt les tâches du plan mais sans exécution réelle
        if (state.dryRun) {
            return executePlanTasks(state, realExecution = false)
        }
        return executePlanTasks(state, realExecution = true)
    }

    /**
     * Nœud 4 (V-5) : persistState — sauvegarde l'état courant via SessionRepository.
     * Ne casse pas le pipeline en cas d'erreur de persistance.
     * Résilient : si sessionRepository est null ou si la session n'a pas été créée,
     * on passe simplement (mode sans persistance).
     */
    private fun persistStateNode(state: VibecodingState): VibecodingState {
        val repo = effectiveSessionRepository ?: return state
        val sid = sessionId ?: return state
        return try {
            val tracker = tokenTracker
            runBlocking {
                repo.updateSession(sid, state, "unknown", tracker)
            }
            log.debug("[VibecodingGraph] Session {} updated: iteration={}, promptTokens={}, cost={}", sessionId, state.iteration, tracker.promptTokens, tracker.estimatedCost("deepseek-v4-pro"))
            state
        } catch (e: Exception) {
            log.warn("[VibecodingGraph] persistState failed: {}", e.message)
            state
        }
    }

    private fun executePlanTasks(state: VibecodingState, realExecution: Boolean): VibecodingState {
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

    // ── Timeout helpers ──

    private fun isTimedOut(state: VibecodingState): Boolean {
        return elapsedSeconds(state) > state.sessionTimeoutSeconds
    }

    private fun elapsedSeconds(state: VibecodingState): Long {
        return (System.currentTimeMillis() - state.sessionStartTimeMs) / 1000
    }
}
