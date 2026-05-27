package vibecoding.contracts.state

import vibecoding.contracts.plan.Plan

/**
 * État mutable du vibecoding agent.
 * Data class pure — extraite de codebase.koog dans N0 pour partage N1→N2 sans violation DAG.
 */
data class VibecodingState(
    val intention: String,
    val workspaceRoot: String,
    val dryRun: Boolean = false,
    val maxActions: Int = 10,
    val sessionTimeoutSeconds: Int = 300,
    val sessionStartTimeMs: Long = System.currentTimeMillis(),
    val iteration: Int = 0,
    val planJson: String = "",
    val plan: Plan? = null,
    val classification: String = "",
    val lastToolResult: String = "",
    val error: String? = null,
    val finished: Boolean = false,
    val executedTasks: List<String> = emptyList(),
    val currentTaskDescription: String = "",
    /** V-6 Feedback Loop: compteur de retry pour erreurs récupérables */
    val retryCount: Int = 0,
    /** V-6 Feedback Loop: nombre maximum de retry avant abandon */
    val maxRetries: Int = 3
) {
    val isFinal: Boolean get() = finished || iteration >= maxActions

    fun nextIteration(): VibecodingState = copy(iteration = iteration + 1)
    fun withPlan(planJson: String, plan: Plan?, classification: String): VibecodingState =
        copy(planJson = planJson, plan = plan, classification = classification)
    fun finish(): VibecodingState = copy(finished = true)
    /** V-6: avec retryCount, l'erreur est récupérable (ne force pas finished) */
    fun withRecoverableError(error: String): VibecodingState =
        copy(error = error, retryCount = retryCount + 1, finished = false)
    /** Erreur fatale: termine la session */
    fun withError(error: String): VibecodingState = copy(error = error, finished = true)
    /** V-6: clear l'erreur après un retry réussi */
    fun clearError(): VibecodingState = copy(error = null)
    /** V-6: incrémente le compteur de retry sans erreur (comptabilité) */
    fun incrementRetry(): VibecodingState = copy(retryCount = retryCount + 1)
}
