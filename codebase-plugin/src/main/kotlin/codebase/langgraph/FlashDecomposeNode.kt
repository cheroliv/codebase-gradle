package codebase.langgraph

import codebase.rag.CompositeContext
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import org.bsc.langgraph4j.action.NodeAction
import java.time.Duration

class FlashDecomposeNode : NodeAction<PlanningState> {

    private val flashModel: ChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("deepseek-v4-flash:cloud")
            .timeout(Duration.ofMinutes(2))
            .build()
    }

    companion object {
        private const val MAX_ATTEMPTS = 2
    }

    override fun apply(state: PlanningState): Map<String, Any> {
        val prompt = buildFlashPrompt(state.intention, state.compositeContext)

        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                val response = flashModel.chat(UserMessage.from(prompt))
                val json = response.aiMessage().text()
                return mapOf("planJson" to json)
            } catch (e: Exception) {
                if (attempt == MAX_ATTEMPTS) {
                    return mapOf(
                        "planJson" to "",
                        "error" to "FlashDecomposeNode failed after $MAX_ATTEMPTS attempts: ${e.message}"
                    )
                }
            }
        }
        return mapOf("error" to "FlashDecomposeNode: unreachable")
    }

    private fun buildFlashPrompt(intention: String, context: CompositeContext?): String {
        val ragContext = context?.ragSection?.take(1500) ?: "(aucun contexte)"

        return """
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
    }
}
