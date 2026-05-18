package codebase.langgraph

import codebase.rag.CompositeContext
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import org.bsc.langgraph4j.action.NodeAction
import java.time.Duration

class ClassifyNode : NodeAction<PlanningState> {

    private val flashModel: ChatModel by lazy {
        OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("deepseek-v4-flash:cloud")
            .timeout(Duration.ofSeconds(30))
            .build()
    }

    override fun apply(state: PlanningState): Map<String, Any> {
        val prompt = buildClassifyPrompt(state.intention, state.compositeContext)

        return try {
            val response = flashModel.chat(UserMessage.from(prompt))
            val raw = response.aiMessage().text().trim().lowercase()

            val classification = when {
                raw.contains("complexe") -> "complexe"
                else -> "simple"
            }

            mapOf("classification" to classification)
        } catch (e: Exception) {
            mapOf("classification" to "simple")
        }
    }

    private fun buildClassifyPrompt(intention: String, context: CompositeContext?): String {
        val ragContext = context?.ragSection?.take(1500) ?: "(aucun contexte)"

        return """
            |Tu es un classifieur de complexité. Analyse l'intention suivante et le contexte fourni.
            |Réponds UNIQUEMENT par "simple" ou "complexe".
            |
            |Critères de complexité :
            |- SIMPLE : intention atomique, 1-2 EPICs, pas de dépendances cross-borough, pattern connu
            |- COMPLEXE : multi-EPICs, intégration multi-plugins, architecture nouvelle, >3 dépendances
            |
            |Intention : $intention
            |
            |Contexte (RAG pgvector) :
            |$ragContext
            |
            |Classification (simple ou complexe) :
            """.trimMargin()
    }
}
