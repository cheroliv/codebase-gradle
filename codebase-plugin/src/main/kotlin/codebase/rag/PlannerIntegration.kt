package codebase.rag

import codebase.koog.AugmentedState
import codebase.koog.KoogPlanningGraph
import codebase.koog.Plan
import codebase.koog.PlanState
import org.slf4j.LoggerFactory
import java.time.Instant

object PlannerIntegration {
    private val log = LoggerFactory.getLogger(PlannerIntegration::class.java)
    private val graph = KoogPlanningGraph()

    fun plan(intention: String, compositeContext: CompositeContext): PlanState {
        log.info("PlannerIntegration: planning intention '{}' (EAGER={}B, RAG={}B, Graphify={}B)",
            intention.take(80),
            compositeContext.eagerSection.length,
            compositeContext.ragSection.length,
            compositeContext.graphifySection.length)

        val result = graph.execute(intention, compositeContext)

        if (result.error != null) {
            log.error("PlannerIntegration: plan failed — {}", result.error)
        } else if (result.plan != null) {
            log.info("PlannerIntegration: plan OK — {} EPICs, {} pts, {} sessions",
                result.plan.epics.size, result.plan.totalPoints, result.plan.estimatedSessions)
        }

        return result
    }
}

/**
 * Convertit un PlanState (koog) en PlanMetadata (méta-communication typée Niveau 2).
 * Null si le plan a échoué.
 */
fun PlanState.toPlanMetadata(source: String = "codebase"): PlanMetadata? {
    if (error != null || plan == null) return null
    return PlanMetadata(
        source = source,
        version = "1.0",
        generatedAt = Instant.now().toString(),
        model = System.getenv("OLLAMA_MODEL") ?: "deepseek-v4-pro:cloud",
        dependencies = listOf("queens", "graphify"),
        epics = plan.epics.size,
        totalPoints = plan.totalPoints,
        classification = classification,
        estimatedSessions = plan.estimatedSessions
    )
}

/**
 * L-3 : Convertit un AugmentedState (wrapper koog+langchain4j) en PlanMetadata.
 * Null si le plan a échoué ou est absent.
 */
fun AugmentedState.toPlanMetadata(source: String = "codebase"): PlanMetadata? {
    if (error != null || plan == null) return null
    return PlanMetadata(
        source = source,
        version = "1.0",
        generatedAt = Instant.now().toString(),
        model = System.getenv("OLLAMA_MODEL") ?: "deepseek-v4-pro:cloud",
        dependencies = listOf("queens", "graphify"),
        epics = plan.epics.size,
        totalPoints = plan.totalPoints,
        classification = classification,
        estimatedSessions = plan.estimatedSessions
    )
}
