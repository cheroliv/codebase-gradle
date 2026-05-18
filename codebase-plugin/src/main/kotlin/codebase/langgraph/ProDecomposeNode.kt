package codebase.langgraph

import codebase.rag.CompositeContext
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import org.bsc.langgraph4j.action.NodeAction
import java.time.Duration

class ProDecomposeNode : NodeAction<PlanningState> {

    private val proModel: ChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("deepseek-v4-pro:cloud")
            .timeout(Duration.ofMinutes(5))
            .build()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }

    override fun apply(state: PlanningState): Map<String, Any> {
        val prompt = buildProPrompt(state.intention, state.compositeContext)

        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val response = proModel.chat(UserMessage.from(prompt))
                val json = response.aiMessage().text()
                return mapOf("planJson" to json)
            } catch (e: Exception) {
                if (attempt == MAX_ATTEMPTS) {
                    return mapOf(
                        "planJson" to "",
                        "error" to "ProDecomposeNode failed after $MAX_ATTEMPTS attempts: ${e.message}"
                    )
                }
            }
        }
        return mapOf("error" to "ProDecomposeNode: unreachable")
    }

    internal fun buildProPrompt(intention: String, context: CompositeContext?): String {
        val eagerContext = context?.eagerSection?.take(2000) ?: "(aucun contexte eager)"
        val ragContext = context?.ragSection?.take(3000) ?: "(aucun contexte rag)"

        return """
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
            |- EPIC names use a short prefix derived from the intention (e.g., PLN, TEST, CAP) followed by a dash and index starting at 0
            |- Decompose logically: 1-4 EPICs, each with 1-4 user stories, each with 1-3 tasks
            |- gradleTask values must be realistic Gradle invocations
            |- Use the governance/RAG context to avoid redundant EPICs (check EPIC statuses in INDEX)
            |- If an EPIC is already TERMINE, do NOT re-plan it — reference it as dependency
            |- Output ONLY the JSON object, no markdown fences, no explanations
            |- The JSON must be valid and parseable by Jackson
            """.trimMargin()
    }
}
