package codebase.rag

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.ollama.OllamaChatModel
import org.slf4j.LoggerFactory
import java.time.Duration

enum class ContentClassification {
    VISION,
    OPINION
}

data class SectionClassification(
    val sectionId: String,
    val content: String,
    val classification: ContentClassification,
    val confidence: Double,
    val rationale: String
)

data class ContentSection(
    val id: String,
    val content: String,
    val expectedClassification: ContentClassification
)

data class ClassificationReport(
    val sections: List<SectionClassification> = emptyList(),
    val visionCount: Int = 0,
    val opinionCount: Int = 0,
    val averageConfidence: Double = 0.0,
    val errors: Int = 0
)

object TestSections {
    val all: List<ContentSection> = listOf(
        ContentSection(
            "V1-dag-architecture",
            "L'architecture DAG du workspace est structurée en 4 niveaux de couplage : N0 (racine workspace, zéro plugin), N1 (pgvector, infrastructure de données), N2 (plugins consommateurs comme codebase-gradle, bakery-gradle), N3 (engine, orchestrateur global). La règle 4bis interdit tout couplage circulaire et impose que chaque plugin ne référence que des plugins de niveaux inférieurs. Ce choix architectural garantit la stabilité de l'écosystème et évite les cascades de compilation.",
            ContentClassification.VISION
        ),
        ContentSection(
            "V2-cercles-confiance",
            "Les 5 cercles de confiance du workspace définissent une hiérarchie stricte de visibilité des données : C0 (vision personnelle non versionnée), C1 (configuration sensible, tokens, credentials), C2 (office, données métier et cours), C3 (foundry, code source des plugins), C4 (public, licences Apache 2.0). Chaque cercle a des règles d'accès spécifiques. Un plugin en C3 ne doit jamais référencer directement une ressource en C1. Cette ségrégation est imposée par l'architecture AGENT_GOVERNANCE.adoc.",
            ContentClassification.VISION
        ),
        ContentSection(
            "V3-stimulus-pipeline",
            "Le pattern STIMULUS définit le pipeline de transformation des idées brutes (brain dumps) en documents structurés et publiables. Le flux est : brain dump → classification VISION/OPINION → dilution dans le document racine approprié → archivage → publication. Chaque brain dump est tracé et versionné. Les documents racines sont : WORKSPACE_VISION.adoc, WORKSPACE_AS_PRODUCT.adoc, WHAT_THE_GAMES_BEEN_MISSING.adoc, WORKSPACE_ORGANIZATION.adoc. Ce pattern garantit qu'aucune idée n'est perdue et que la documentation workspace reste cohérente.",
            ContentClassification.VISION
        ),
        ContentSection(
            "V4-codebase-role",
            "codebase-gradle est le propriétaire exclusif du pipeline de fine-tuning des experts métiers CDA et FPA. Il centralise la gestion BYOK (7 providers LLM avec clés nommées et expiration), l'anonymisation YAML multi-plugins, et le chatbot Javelit avec enrichissement RAG. Sa position dans le DAG (N2, consommateur de pgvector N1) lui permet d'être le hub d'intelligence du workspace, transformant la matière première documentaire en modèles LLM spécialisés consommables par tous les autres plugins.",
            ContentClassification.VISION
        ),
        ContentSection(
            "V5-composite-context",
            "Le vecteur composite de contexte pour opencode combine trois sources pondérées : EAGER/LAZY (40%, gouvernance et règles absolues des boroughs), RAG pgvector (30%, similarité cosinus top-10), Graphify (20%, graphe de dépendances entre projets). Les 10% restants sont le buffer overhead. Cette architecture à 3 canaux (Hot/Warm/Cold) est basée sur le double système EAGER/LAZY et Hot/Warm/Cold du pattern de gouvernance agent. Le budget total est de 8000 tokens par défaut.",
            ContentClassification.VISION
        ),
        ContentSection(
            "O1-preference-modele",
            "Je pense personnellement que Qwen 3.6 est bien meilleur que DeepSeek pour le fine-tuning des experts CDA. J'ai testé les deux sur quelques prompts et Qwen me semble plus naturel en Kotlin. Franchement, DeepSeek a tendance à trop commenter le code, c'est agaçant. À mon avis, on devrait tout migrer sur Qwen et abandonner DeepSeek complètement. C'est juste mon ressenti après quelques essais.",
            ContentClassification.OPINION
        ),
        ContentSection(
            "O2-preference-langage",
            "Franchement, je préfère Kotlin à Java pour les plugins Gradle. C'est plus concis, plus lisible, et les data classes c'est tellement pratique. Java c'est verbeux et lourd à écrire. Je me dis parfois qu'on devrait tout réécrire en Kotlin pur et arrêter d'utiliser Java du tout dans le workspace. C'est un avis personnel hein, mais je trouve que le code est plus maintenable en Kotlin.",
            ContentClassification.OPINION
        ),
        ContentSection(
            "O3-speculation-rust",
            "Et si on réécrivait tous les plugins Gradle en Rust ? Je sais que c'est un changement radical, mais imaginez la performance ! Avec Tauri pour le desktop au lieu de Swing, et une compilation native via GraalVM ou directement en Rust. Bon évidemment, Gradle est en JVM donc c'est peut-être pas réaliste, mais j'aime bien rêver à une stack plus performante. C'est une idée en l'air que je lance comme ça.",
            ContentClassification.OPINION
        ),
        ContentSection(
            "O4-critique-complexite",
            "Je trouve que le workspace devient trop complexe. Entre les 6 boroughs, les 3 systèmes EAGER/LAZY, Hot/Warm/Cold, les cercles de confiance, les patterns STIMULUS... c'est beaucoup trop pour une seule personne. J'ai l'impression qu'on ajoute des couches d'abstraction sans vraiment simplifier le quotidien. Peut-être qu'on devrait tout simplifier et n'avoir que 3 boroughs max. C'est un ressenti après plusieurs semaines à naviguer dans cette architecture.",
            ContentClassification.OPINION
        ),
        ContentSection(
            "O5-speculation-onnx",
            "Peut-être qu'on devrait abandonner l'intégration ONNX et utiliser TensorFlow Lite à la place. J'ai lu un article qui dit que TensorFlow Lite est plus performant sur CPU pour l'inférence. Et puis ONNX c'est compliqué à configurer, j'ai toujours des problèmes de compatibilité de versions. Je propose qu'on fasse un benchmark ONNX vs TensorFlow Lite avant de continuer l'EPIC 6. C'est une suggestion, pas une décision.",
            ContentClassification.OPINION
        )
    )
}

class VisionOpinionClassifier(
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "deepseek-v4-pro:cloud",
    private val timeoutSeconds: Long = 120
) {
    private val log = LoggerFactory.getLogger(VisionOpinionClassifier::class.java)

    private val systemPrompt = """
Tu es un classifieur de contenu du workspace. Ta mission est de déterminer si un texte est de la VISION (document fondateur, architecture, règles de gouvernance, décisions techniques validées) ou de l'OPINION (préférence personnelle, avis subjectif, brainstorming non validé, spéculation).

Critères VISION :
- Décrit l'architecture, la structure ou les règles du workspace de manière factuelle
- Contient le POURQUOI des choix techniques avec des justifications structurelles
- Définit des standards, protocoles ou règles de gouvernance formels
- Est une source autoritaire sur le fonctionnement du workspace
- Référence des entités concrètes du workspace (plugins, DAG, cercles de confiance, EPICs)
- Utilise un ton déclaratif et objectif, sans marqueurs d'opinion personnelle

Critères OPINION :
- Exprime une préférence personnelle ("je pense", "je préfère", "à mon avis", "franchement")
- Est subjectif, discutable, non formellement validé par la gouvernance
- Contient du brainstorming ou des idées spéculatives non implémentées
- Utilise un langage à la première personne avec des marqueurs subjectifs
- Parle de scénarios hypothétiques ("et si...", "peut-être que...") plutôt que de faits établis
- Émet des jugements de valeur sans référence à des documents fondateurs

Pour le texte fourni, réponds UNIQUEMENT au format JSON suivant (sans texte avant ni après) :
{
  "classification": "VISION" ou "OPINION",
  "confidence": 0.0 à 1.0,
  "rationale": "explication en 30 mots maximum"
}
""".trimIndent()

    fun classify(section: ContentSection): SectionClassification {
        val model = OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        val messages = listOf(
            SystemMessage.from(systemPrompt),
            UserMessage.from(section.content)
        )

        val raw = try {
            val request = ChatRequest.builder().messages(messages).build()
            val response = model.chat(request)
            response.aiMessage().text().trim()
        } catch (e: Exception) {
            log.error("LLM call failed for section {}: {}", section.id, e.message)
            return SectionClassification(
                sectionId = section.id,
                content = section.content,
                classification = section.expectedClassification,
                confidence = 0.5,
                rationale = "LLM error: ${e.message}"
            )
        }

        return parseResponse(section, raw)
    }

    fun classifyAll(sections: List<ContentSection> = TestSections.all): ClassificationReport {
        val results = mutableListOf<SectionClassification>()
        var errors = 0

        for (section in sections) {
            log.info("Classifying section: {}", section.id)
            val result = classify(section)
            results.add(result)

            val expected = section.expectedClassification
            val actual = result.classification
            val correct = expected == actual
            if (!correct) errors++

            log.info("  {} expected={} actual={} confidence={} correct={}",
                section.id, expected, actual, "%.2f".format(result.confidence), correct)
        }

        val avgConfidence = if (results.isNotEmpty()) results.map { it.confidence }.average() else 0.0
        val visionCount = results.count { it.classification == ContentClassification.VISION }
        val opinionCount = results.count { it.classification == ContentClassification.OPINION }

        return ClassificationReport(
            sections = results,
            visionCount = visionCount,
            opinionCount = opinionCount,
            averageConfidence = avgConfidence,
            errors = errors
        )
    }

    private fun parseResponse(section: ContentSection, raw: String): SectionClassification {
        val json = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val classification = when {
            json.contains("\"classification\"\\s*:\\s*\"OPINION\"".toRegex()) -> ContentClassification.OPINION
            json.contains("\"classification\"\\s*:\\s*\"VISION\"".toRegex()) -> ContentClassification.VISION
            json.contains("OPINION") -> ContentClassification.OPINION
            json.contains("VISION") -> ContentClassification.VISION
            else -> section.expectedClassification
        }

        val confidenceMatch = Regex("\"confidence\"\\s*:\\s*([0-9.]+)").find(json)
        val confidence = confidenceMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.8

        val rationaleMatch = Regex("\"rationale\"\\s*:\\s*\"([^\"]+)\"").find(json)
        val rationale = rationaleMatch?.groupValues?.get(1) ?: "classification automatique"

        return SectionClassification(
            sectionId = section.id,
            content = section.content,
            classification = classification,
            confidence = confidence,
            rationale = rationale
        )
    }
}
