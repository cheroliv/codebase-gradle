package codebase.koog

import cccp.vibecoding.contracts.context.CompositeContext
import cccp.vibecoding.contracts.plan.Plan
import cccp.vibecoding.contracts.plan.PlanState
import cccp.vibecoding.contracts.plan.Epic
import cccp.vibecoding.contracts.plan.UserStory
import cccp.vibecoding.contracts.plan.Task
import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.runBlocking
import java.time.Duration

// === Données du plan (importées de cccp.vibecoding.contracts.plan) ===

// === Orchestrateur koog + langchain4j ===

/**
 * Planning Graph — koog (JetBrains, Apache 2.0) + langchain4j pour le scope opérationnel.
 *
 * Architecture :
 * - **koog** : orchestrateur (`strategy {}` DSL, type-safe, asMermaidDiagram)
 * - **langchain4j** : appels LLM (OllamaChatModel deepseek-v4)
 * - **pgvector/embeddings/vector stores** : scope opérationnel langchain4j (L-2)
 *
 * Remplacé l'ancien orchestrateur externe (langgraph4j, supprimé).
 */
class KoogPlanningGraph {

    val graph: AIAgentGraphStrategy<PlanState, PlanState> = strategy<PlanState, PlanState>(
        name = "planning",
        toolSelectionStrategy = ToolSelectionStrategy.NONE
    ) {
        val classify by node<PlanState, PlanState> { state ->
            val classification = classifyIntention(state.intention, state.compositeContext)
            state.copy(classification = classification)
        }

        val flashDecompose by node<PlanState, PlanState> { state ->
            val (planJson, error) = decomposeFlash(state.intention, state.compositeContext)
            state.copy(planJson = planJson, error = error)
        }

        val proDecompose by node<PlanState, PlanState> { state ->
            val (planJson, error) = decomposePro(state.intention, state.compositeContext)
            state.copy(planJson = planJson, error = error)
        }

        val format by node<PlanState, PlanState> { state ->
            val (plan, error) = parsePlan(state.planJson)
            state.copy(plan = plan, error = error)
        }

        edge(nodeStart forwardTo classify onCondition { _ -> true } transformed { it })
        edge(classify forwardTo flashDecompose onCondition { it.classification == "simple" } transformed { it })
        edge(classify forwardTo proDecompose onCondition { it.classification == "complexe" } transformed { it })
        edge(flashDecompose forwardTo format onCondition { _ -> true } transformed { it })
        edge(proDecompose forwardTo format onCondition { _ -> true } transformed { it })
        edge(format forwardTo nodeFinish onCondition { _ -> true } transformed { it })
    }

    fun execute(intention: String, compositeContext: CompositeContext): PlanState {
        var state = PlanState(intention = intention, compositeContext = compositeContext)

        state = state.copy(classification = classifyIntention(intention, compositeContext))

        val (planJson, error) = if (state.classification == "complexe") {
            decomposePro(intention, compositeContext)
        } else {
            decomposeFlash(intention, compositeContext)
        }
        state = state.copy(planJson = planJson, error = error)

        if (state.error == null && state.planJson.isNotBlank()) {
            val (plan, parseError) = parsePlan(state.planJson)
            state = state.copy(plan = plan, error = parseError)
        }

        return state
    }

    fun asMermaidDiagram(): String = runBlocking { graph.asMermaidDiagram() }
}

// === Modèles LLM (langchain4j — préservé) ===

private val flashModel: ChatModel by lazy {
    OllamaChatModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("deepseek-v4-flash:cloud")
        .timeout(Duration.ofSeconds(30))
        .build()
}

private val proModel: ChatModel by lazy {
    OllamaChatModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("deepseek-v4-pro:cloud")
        .timeout(Duration.ofMinutes(5))
        .build()
}

private val mapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

// === Logique métier (langchain4j LLM) ===

private fun classifyIntention(intention: String, context: CompositeContext?): String {
    val ragContext = context?.ragSection?.take(1500) ?: "(aucun contexte)"
    val prompt = """
        |Tu es un classifieur de complexite. Analyse l'intention suivante et le contexte fourni.
        |Reponds UNIQUEMENT par "simple" ou "complexe".
        |
        |Criteres de complexite :
        |- SIMPLE : intention atomique, 1-2 EPICs, pas de dependances cross-borough, pattern connu
        |- COMPLEXE : multi-EPICs, integration multi-plugins, architecture nouvelle, >3 dependances
        |
        |Intention : $intention
        |
        |Contexte (RAG pgvector) :
        |$ragContext
        |
        |Classification (simple ou complexe) :
        """.trimMargin()
    return try {
        val raw = flashModel.chat(UserMessage.from(prompt)).aiMessage().text().trim().lowercase()
        if (raw.contains("complexe")) "complexe" else "simple"
    } catch (_: Exception) {
        "simple"
    }
}

private fun decomposeFlash(intention: String, context: CompositeContext?): Pair<String, String?> {
    val ragContext = context?.ragSection?.take(1500) ?: "(aucun contexte)"
    val prompt = """
        |You are a Planning Expert. Decompose this simple intention into a plan.
        |
        |Intention: $intention
        |
        |RELEVANT CONTEXT:
        |$ragContext
        |
        |Output a valid JSON:
        |{
        |  "title": "<summary>",
        |  "epics": [{"name":"<EPIC-ID>","description":"...","points":<N>,"userStories":[...]}],
        |  "totalPoints": <N>,
        |  "estimatedSessions": "<range>"
        |}
        |
        |Keep it concise. Output ONLY JSON, no markdown.
        """.trimMargin()
    for (attempt in 1..2) {
        try {
            return Pair(flashModel.chat(UserMessage.from(prompt)).aiMessage().text(), null)
        } catch (e: Exception) {
            if (attempt == 2) return Pair("", "FlashDecompose failed after 2 attempts: ${e.message}")
        }
    }
    return Pair("", "FlashDecompose: unreachable")
}

private fun decomposePro(intention: String, context: CompositeContext?): Pair<String, String?> {
    val eagerContext = context?.eagerSection?.take(2000) ?: "(aucun contexte eager)"
    val ragContext = context?.ragSection?.take(3000) ?: "(aucun contexte rag)"
    val prompt = """
        |You are a Planning Expert. Your role is to decompose a high-level intention
        |into a structured execution plan for a Gradle plugin project.
        |
        |Intention: $intention
        |
        |GOVERNANCE CONTEXT (EAGER rules and EPIC status):
        |$eagerContext
        |
        |SEMANTIC CONTEXT (RAG from pgvector — relevant codebase chunks):
        |$ragContext
        |
        |Output a valid JSON object with this exact structure:
        |{
        |  "title": "<intention summary>",
        |  "epics": [
        |    {
        |      "name": "<EPIC-ID>",
        |      "description": "<epic description>",
        |      "points": <story points, integer>,
        |      "userStories": [
        |        {
        |          "description": "<user story description>",
        |          "tasks": [
        |            {
        |              "description": "<task description>",
        |              "gradleTask": "./gradlew <task>"
        |            }
        |          ]
        |        }
        |      ]
        |    }
        |  ],
        |  "totalPoints": <sum of all epic points>,
        |  "estimatedSessions": "<range like '3-5'>"
        |}
        |
        |Rules:
        |- EPIC names use a short prefix (e.g., PLN, TEST, CAP) + dash + index starting at 0
        |- Decompose: 1-4 EPICs, each 1-4 user stories, each 1-3 tasks
        |- gradleTask values must be realistic Gradle invocations
        |- Use governance/RAG context to avoid redundant EPICs
        |- If an EPIC is already TERMINE, do NOT re-plan it — reference it as dependency
        |- Output ONLY the JSON object, no markdown fences, no explanations
        """.trimMargin()
    for (attempt in 1..3) {
        try {
            return Pair(proModel.chat(UserMessage.from(prompt)).aiMessage().text(), null)
        } catch (e: Exception) {
            if (attempt == 3) return Pair("", "ProDecompose failed after 3 attempts: ${e.message}")
        }
    }
    return Pair("", "ProDecompose: unreachable")
}

private fun parsePlan(raw: String): Pair<Plan?, String?> {
    if (raw.isBlank()) return Pair(null, "parsePlan: empty planJson")
    return try {
        Pair(mapper.readValue(extractJson(raw)), null)
    } catch (e: Exception) {
        Pair(null, "parsePlan: failed to parse JSON — ${e.message}")
    }
}

private fun extractJson(raw: String): String {
    var cleaned = raw.trim()
    if (cleaned.startsWith("```")) {
        cleaned = cleaned.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
}
