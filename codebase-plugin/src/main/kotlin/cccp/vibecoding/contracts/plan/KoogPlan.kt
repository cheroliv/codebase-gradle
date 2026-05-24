package cccp.vibecoding.contracts.plan

import education.cccp.contracts.context.CompositeContext

/**
 * Plan de décomposition — structure de données pure.
 * Extraite de codebase.koog.KoogPlanningGraph dans N0 pour partage N1→N2 sans violation DAG.
 */
data class Plan(
    val title: String,
    val epics: List<Epic>,
    val totalPoints: Int,
    val estimatedSessions: String
)

data class Epic(
    val name: String,
    val description: String,
    val points: Int,
    val userStories: List<UserStory>
)

data class UserStory(
    val description: String,
    val tasks: List<Task>
)

data class Task(
    val description: String,
    val gradleTask: String
)

/**
 * État du graphe de planification.
 */
data class PlanState(
    val intention: String = "",
    val compositeContext: CompositeContext? = null,
    val classification: String = "",
    val planJson: String = "",
    val plan: Plan? = null,
    val error: String? = null
)
