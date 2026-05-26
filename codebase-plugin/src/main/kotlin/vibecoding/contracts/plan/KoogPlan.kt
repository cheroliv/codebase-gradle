package vibecoding.contracts.plan

import contracts.agent.Epic
import contracts.agent.UserStory
import contracts.agent.GradleTask
import contracts.context.CompositeContext

/**
 * Plan de décomposition — structure de données pure.
 * Utilise les types Epic/UserStory/GradleTask du N0 (agent-contracts).
 * Extraite de codebase.koog.KoogPlanningGraph dans N0 pour partage N1→N2 sans violation DAG.
 */
data class Plan(
    val title: String,
    val epics: List<Epic>,
    val totalPoints: Int,
    val estimatedSessions: String
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
