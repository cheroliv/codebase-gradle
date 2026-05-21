package codebase.koog

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
    val currentTaskDescription: String = ""
) {
    val isFinal: Boolean get() = finished || iteration >= maxActions

    fun nextIteration(): VibecodingState = copy(iteration = iteration + 1)
    fun withPlan(planJson: String, plan: Plan?, classification: String): VibecodingState =
        copy(planJson = planJson, plan = plan, classification = classification)
    fun finish(): VibecodingState = copy(finished = true)
    fun withError(error: String): VibecodingState = copy(error = error, finished = true)
}
