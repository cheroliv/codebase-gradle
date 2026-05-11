package codebase.rag

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.ollama.OllamaChatModel
import org.slf4j.LoggerFactory
import java.time.Duration

data class PertinenceQuestion(
    val id: String,
    val question: String,
    val domain: String,
    val expectedKeywords: List<String> = emptyList(),
    val minExpectedLength: Int = 100
)

object PertinenceQuestions {
    val all: List<PertinenceQuestion> = listOf(
        PertinenceQuestion(
            "Q1-gouvernance",
            "Quelles sont les regles absolues de gouvernance agent qui s'appliquent a TOUS les projets du workspace ?",
            "Gouvernance",
            listOf("commit", "session", "test", "secret", "interdiction", "EAGER", "LAZY"),
            150
        ),
        PertinenceQuestion(
            "Q2-architecture",
            "Explique l'architecture DAG N0->N1->N2->N3 et la regle 4bis de couplage entre plugins Gradle.",
            "Architecture",
            listOf("DAG", "N0", "N1", "N2", "N3", "pgvector", "couplage"),
            150
        ),
        PertinenceQuestion(
            "Q3-cercles",
            "Decris les 5 cercles de confiance du workspace et ce que chaque cercle contient comme type de donnees.",
            "Securite",
            listOf("C0", "C1", "C2", "C3", "C4", "secrets", "configuration", "foundry"),
            150
        ),
        PertinenceQuestion(
            "Q4-rag",
            "Comment fonctionne le pipeline RAG pgvector dans codebase-gradle ? Explique le flux walk -> index -> query.",
            "RAG",
            listOf("pgvector", "walk", "index", "embedding", "cosine", "chunk", "token"),
            150
        ),
        PertinenceQuestion(
            "Q5-opencode",
            "Qu'est-ce que le vecteur composite de contexte pour opencode et comment est-il injecte dans le prompt systeme ?",
            "Opencode",
            listOf("composite", "EAGER", "RAG", "Graphify", "inject", "opencode", "contexte"),
            150
        ),
        PertinenceQuestion(
            "Q6-anonymisation",
            "Comment fonctionne le pipeline d'anonymisation RGPD multi-sources (YAML, JSON, properties) dans le workspace ?",
            "Anonymisation",
            listOf("anonymisation", "RGPD", "PII", "YAML", "JSON", "secret", "mask"),
            150
        ),
        PertinenceQuestion(
            "Q7-plugins",
            "Liste les plugins Gradle gouvernes dans le workspace et leur niveau DAG respectif.",
            "Ecosysteme",
            listOf("plantuml", "bakery", "slider", "codebase", "graphify", "readme", "magic_stick"),
            120
        ),
        PertinenceQuestion(
            "Q8-benchmark",
            "Quel est le protocole du benchmark de perception spatiale (EPIC 4) et quels sont les 5 scenarios de test ?",
            "Benchmark",
            listOf("EPIC", "perception", "spatiale", "BASELINE", "RAG_ONLY", "threshold", "token"),
            150
        ),
        PertinenceQuestion(
            "Q9-stimulus",
            "Explique le pattern STIMULUS et la cascade de dilution automatique des brain dumps vers les documents racine.",
            "Workflow",
            listOf("STIMULUS", "dilution", "brain", "dump", "archivage", "mapping", "vision"),
            150
        ),
        PertinenceQuestion(
            "Q10-stack",
            "Quelles sont les technologies utilisees dans l'ecosysteme workspace (LangChain4j, ONNX, pgvector, Ollama, LangGraph4j) et leurs roles respectifs ?",
            "Stack",
            listOf("LangChain4j", "ONNX", "pgvector", "Ollama", "LangGraph4j", "deepseek", "embedding"),
            150
        )
    )
}

data class PertinenceAnswer(
    val question: String = "",
    val answer: String = "",
    val keywordHits: Int = 0,
    val totalKeywords: Int = 0,
    val answerLength: Int = 0,
    val isRelevant: Boolean = false
)

data class PertinencePair(
    val baseline: PertinenceAnswer,
    val augmented: PertinenceAnswer,
    val deltaKeywords: Int = 0,
    val deltaLength: Int = 0,
    val improvement: Boolean = false
)

data class PertinenceBenchmarkReport(
    val executionTimestamp: String = "",
    val modelName: String = "",
    val totalQuestions: Int = 0,
    val improvedCount: Int = 0,
    val degradedCount: Int = 0,
    val unchangedCount: Int = 0,
    val improvementRate: Double = 0.0,
    val pairs: List<Pair<String, PertinencePair>> = emptyList(),
    val mvp0Validated: Boolean = false
)

class PertinenceBenchmarkRunner(
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "deepseek-v4-pro:cloud",
    private val timeoutSeconds: Long = 300,
    private val contextFilePath: String = "/tmp/opencode-context.txt"
) {
    private val log = LoggerFactory.getLogger(PertinenceBenchmarkRunner::class.java)

    fun run(): PertinenceBenchmarkReport {
        val model = try {
            OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build()
        } catch (e: Exception) {
            log.error("Failed to connect Ollama: {}", e.message)
            throw e
        }

        val contextText = try {
            java.io.File(contextFilePath).readText()
        } catch (e: Exception) {
            log.warn("Context file {} not readable: {}", contextFilePath, e.message)
            ""
        }
        val hasContext = contextText.isNotBlank()

        val pairs = mutableListOf<Pair<String, PertinencePair>>()

        for (q in PertinenceQuestions.all) {
            log.info("Processing {}: {}", q.id, q.question.take(80))

            val baselineAnswer = ask(model, q.question, "")

            val augmentedAnswer = if (hasContext) {
                ask(model, q.question, contextText)
            } else {
                log.warn("No context file — augmented = baseline")
                baselineAnswer
            }

            val deltaKeywords = augmentedAnswer.keywordHits - baselineAnswer.keywordHits
            val deltaLength = augmentedAnswer.answerLength - baselineAnswer.answerLength
            val improvement = augmentedAnswer.isRelevant && (!baselineAnswer.isRelevant || deltaKeywords > 0)

            pairs.add(q.id to PertinencePair(
                baseline = baselineAnswer,
                augmented = augmentedAnswer,
                deltaKeywords = deltaKeywords,
                deltaLength = deltaLength,
                improvement = improvement
            ))

            log.info("{} — Baseline: relevant={}, hits={}/{} | Augmented: relevant={}, hits={}/{}, improvement={}",
                q.id, baselineAnswer.isRelevant, baselineAnswer.keywordHits, baselineAnswer.totalKeywords,
                augmentedAnswer.isRelevant, augmentedAnswer.keywordHits, augmentedAnswer.totalKeywords,
                improvement
            )
        }

        val improved = pairs.count { it.second.improvement }
        val degraded = pairs.count { !it.second.improvement && it.second.deltaKeywords < 0 }
        val unchanged = pairs.size - improved - degraded
        val improvementRate = if (pairs.isNotEmpty()) improved.toDouble() / pairs.size else 0.0
        val mvp0 = improvementRate >= 0.70

        return PertinenceBenchmarkReport(
            executionTimestamp = java.time.Instant.now().toString(),
            modelName = modelName,
            totalQuestions = pairs.size,
            improvedCount = improved,
            degradedCount = degraded,
            unchangedCount = unchanged,
            improvementRate = improvementRate,
            pairs = pairs,
            mvp0Validated = mvp0
        )
    }

    private fun ask(model: OllamaChatModel, question: String, context: String): PertinenceAnswer {
        val q = PertinenceQuestions.all.firstOrNull { it.question == question }
            ?: PertinenceQuestions.all[0]

        val systemPrompt = if (context.isNotBlank()) {
            """
            Tu es un assistant expert du workspace. Utilise le CONTEXTE fourni ci-dessous pour repondre avec precision.
            
            === CONTEXTE WORKSPACE ===
            $context
            === FIN CONTEXTE ===
            
            Reponds en francais, de maniere precise et detaillee, en t'appuyant sur le contexte fourni.
            """.trimIndent()
        } else {
            """
            Tu es un assistant expert du workspace. Reponds a la question en francais de maniere precise et detaillee.
            Tu n'as PAS de contexte supplementaire — reponds avec tes connaissances generales.
            """.trimIndent()
        }

        val answer = try {
            val messages = listOf(
                SystemMessage.from(systemPrompt),
                UserMessage.from(question)
            )
            val request = ChatRequest.builder().messages(messages).build()
            val response = model.chat(request)
            response.aiMessage().text().trim()
        } catch (e: Exception) {
            log.error("Ollama call failed for {}: {}", q.id, e.message?.take(100))
            "ERREUR: ${e.message}"
        }

        val answerLower = answer.lowercase()
        val keywordHits = q.expectedKeywords.count { kw -> answerLower.contains(kw.lowercase()) }
        val isRelevant = keywordHits >= (q.expectedKeywords.size / 2).coerceAtLeast(1)

        return PertinenceAnswer(
            question = question,
            answer = answer,
            keywordHits = keywordHits,
            totalKeywords = q.expectedKeywords.size,
            answerLength = answer.length,
            isRelevant = isRelevant
        )
    }
}
