package codebase.rag

import contracts.agent.Epic
import contracts.agent.GradleTask
import contracts.agent.UserStory
import contracts.context.CompositeContext
import vibecoding.contracts.plan.Plan
import vibecoding.contracts.plan.PlanState
import vibecoding.contracts.state.AugmentedState
import org.slf4j.LoggerFactory
import planning.IntentionPlanner
import planning.PlanningContext
import java.time.Instant

object PlannerIntegration {
    private val log = LoggerFactory.getLogger(PlannerIntegration::class.java)

    fun plan(intention: String, compositeContext: CompositeContext): PlanState {
        log.info("PlannerIntegration: planning intention '{}' (EAGER={}B, RAG={}B, Graphify={}B, Docs={}B)",
            intention.take(80),
            compositeContext.eagerSection.length,
            compositeContext.ragSection.length,
            compositeContext.graphifySection.length,
            compositeContext.docsSection.length)

        return try {
            val ctx = PlanningContext(intention = intention)
            val planningPlan = IntentionPlanner.plan(
                intention = intention,
                context = ctx,
                specContents = emptyList(),
                eagerContext = compositeContext.eagerSection,
                ragContext = compositeContext.ragSection,
                graphifyContext = compositeContext.graphifySection,
                docsContext = compositeContext.docsSection
            )
            val plan = planningPlan.toContractPlan()
            log.info("PlannerIntegration: plan OK — {} EPICs, {} pts, {} sessions",
                plan.epics.size, plan.totalPoints, plan.estimatedSessions)
            PlanState(
                intention = intention,
                compositeContext = compositeContext,
                classification = if (intention.length > 80) "complexe" else "simple",
                planJson = "",
                plan = plan,
                error = null
            )
        } catch (e: Exception) {
            log.error("PlannerIntegration: plan failed — {}", e.message)
            PlanState(
                intention = intention,
                compositeContext = compositeContext,
                error = "IntentionPlanner failed: ${e.message}"
            )
        }
    }
}

fun PlanState.toPlanMetadata(source: String = "codebase"): PlanMetadata? {
    if (error != null || plan == null) return null
    val p = plan ?: return null
    return PlanMetadata(
        source = source,
        version = "1.0",
        generatedAt = Instant.now().toString(),
        model = System.getenv("OLLAMA_MODEL") ?: "deepseek-v4-pro:cloud",
        dependencies = listOf("queens", "graphify", "codex"),
        epics = p.epics.size,
        totalPoints = p.totalPoints,
        classification = "",
        estimatedSessions = p.estimatedSessions
    )
}

fun AugmentedState.toPlanMetadata(source: String = "codebase"): PlanMetadata? {
    if (error != null || plan == null) return null
    val p = plan ?: return null
    return PlanMetadata(
        source = source,
        version = "1.0",
        generatedAt = Instant.now().toString(),
        model = System.getenv("OLLAMA_MODEL") ?: "deepseek-v4-pro:cloud",
        dependencies = listOf("queens", "graphify", "codex"),
        epics = p.epics.size,
        totalPoints = p.totalPoints,
        classification = "",
        estimatedSessions = p.estimatedSessions
    )
}

private fun planning.Plan.toContractPlan(): vibecoding.contracts.plan.Plan =
    vibecoding.contracts.plan.Plan(
        title = title,
        epics = epics.map { e ->
            contracts.agent.Epic(
                name = e.name,
                description = e.description,
                points = e.points,
                userStories = e.userStories.map { us ->
                    contracts.agent.UserStory(
                        description = us.description,
                        tasks = us.tasks.map { t ->
                            contracts.agent.GradleTask(
                                description = t.description,
                                gradleTask = t.gradleTask
                            )
                        }
                    )
                }
            )
        },
        totalPoints = totalPoints,
        estimatedSessions = estimatedSessions
    )
