package codebase.rag

import codebase.langgraph.Plan
import codebase.langgraph.PlanningGraph
import codebase.langgraph.PlanningState
import org.slf4j.LoggerFactory
import java.time.Instant

object PlannerIntegration {
    private val log = LoggerFactory.getLogger(PlannerIntegration::class.java)
    private val graph = PlanningGraph()

    fun plan(intention: String, compositeContext: CompositeContext): PlanningState {
        log.info("PlannerIntegration: planning intention '{}' (EAGER={}B, RAG={}B, Graphify={}B)",
            intention.take(80),
            compositeContext.eagerSection.length,
            compositeContext.ragSection.length,
            compositeContext.graphifySection.length)

        val result = graph.execute(intention, compositeContext)

        if (result.error != null) {
            log.error("PlannerIntegration: plan failed — {}", result.error)
        } else if (result.plan != null) {
            val plan = result.plan as Plan
            log.info("PlannerIntegration: plan OK — {} EPICs, {} pts, {} sessions",
                plan.epics.size, plan.totalPoints, plan.estimatedSessions)
        }

        return result
    }
}

/**
 * Convertit un PlanningState en PlanMetadata (méta-communication typée Niveau 2).
 * Null si le plan a échoué.
 */
fun PlanningState.toPlanMetadata(source: String = "codebase"): PlanMetadata? {
    if (error != null || plan == null) return null
    val p = plan as codebase.langgraph.Plan
    return PlanMetadata(
        source = source,
        version = "1.0",
        generatedAt = Instant.now().toString(),
        model = System.getenv("OLLAMA_MODEL") ?: "deepseek-v4-pro:cloud",
        dependencies = listOf("queens", "graphify"),
        epics = p.epics.size,
        totalPoints = p.totalPoints,
        classification = classification,
        estimatedSessions = p.estimatedSessions
    )
}
