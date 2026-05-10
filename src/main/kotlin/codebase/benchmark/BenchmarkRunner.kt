package codebase.benchmark

import codebase.rag.EmbeddingPipeline
import codebase.rag.VectorStore
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration

data class CircleClassification(
    val predictedCircle: Int = -1,
    val confidenceScore: Double = 0.0,
    val reasoning: String = ""
)

interface SpatialPerceptionExpert {
    @SystemMessage(
        """
        Tu es un classifieur de cercles de confiance dans un workspace structuré en 5 cercles concentriques.
        Ta mission : lire un extrait de document et déterminer à quel cercle de confiance il appartient.

        Les 5 cercles :
        - CERCLE 0 : Workspace racine (hors-CVS). Documents de vision, stratégie, brain dump, notes personnelles.
                     Contient des idées non validées, des intuitions, des ébauches.
        - CERCLE 1 : configuration/ (secrets, tokens, credentials). Fichiers non versionnés.
                     Contient des clés API, mots de passe, variables d'environnement.
        - CERCLE 2 : office/ (données privées, cercle 2). Documents métier, cours, datasets.
                     Contient du contenu pédagogique, des données de formation, des livres.
        - CERCLE 3 : foundry/CSS/ (licence propriétaire). Code source closed-source.
                     Contient du code payant, des algorithmes propriétaires.
        - CERCLE 4 : foundry/OSS/ (Apache 2.0, public). Code open source, documentation publique.
                     Contient des plugins Gradle, de la doc technique publique.

        Format de réponse OBLIGATOIRE — JSON strict, aucun texte avant ou après :
        {
          "predictedCircle": 4,
          "confidenceScore": 0.92,
          "reasoning": "Ce document décrit un plugin Gradle open source avec licence Apache 2.0 — cercle 4 typique."
        }
        """
    )
    @UserMessage(
        """
        {{context}}
        
        ---
        Voici l'extrait de document à classifier. À quel cercle de confiance appartient-il ?
        
        {{documentExcerpt}}
        
        Retourne UNIQUEMENT le JSON avec predictedCircle (0-4), confidenceScore (0.0-1.0) et reasoning.
        """
    )
    fun classify(@V("context") context: String, @V("documentExcerpt") documentExcerpt: String): CircleClassification
}

interface SpatialPerceptionExpertFactory {
    fun create(baseUrl: String, modelName: String, timeoutSeconds: Long): SpatialPerceptionExpert
}

object BenchmarkExpertFactory : SpatialPerceptionExpertFactory {
    private val log = LoggerFactory.getLogger(BenchmarkExpertFactory::class.java)

    override fun create(baseUrl: String, modelName: String, timeoutSeconds: Long): SpatialPerceptionExpert {
        return try {
            val model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build()

            val expert = AiServices.builder(SpatialPerceptionExpert::class.java)
                .chatModel(model)
                .build()

            log.info("Spatial expert connected: {}/{}", baseUrl, modelName)
            expert
        } catch (e: Exception) {
            log.error("Failed to connect LLM: {}", e.message?.take(80))
            throw e
        }
    }
}

data class ClassificationRequest(
    val documentExcerpt: String = "",
    val context: String = ""
)

data class SampleDocument(
    val id: String = "",
    val expectedCircle: Int = 0,
    val content: String = ""
)

object BenchmarkFixtures {
    val samples: List<SampleDocument> = listOf(
        SampleDocument(
            "C0-strategie",
            0,
            """
            WORKSPACE_AS_PRODUCT.adoc — Ce document décrit la vision du workspace comme produit.
            L'idée est de transformer l'écosystème de plugins Gradle en SaaS provisionnable.
            Hypothèse : le marché des formations RNCP est sous-exploité, Edster pourrait capter 15%.
            Stratégie de pricing : freemium avec paliers à 49€/mois (individuel) et 499€/mois (pro).
            Note personnelle : vérifier si Qualiopi impose un audit par un organisme tiers.
            """.trimIndent()
        ),
        SampleDocument(
            "C1-tokens",
            1,
            """
            configuration/codebase.yml — Fichier de configuration contenant des secrets.
            ollama.baseUrl=http://localhost:11434
            anthropic.defaultKey=chatbot
            anthropic.accounts[0].keys[prod]=sk-ant-api03-xxxxxxxxxxxxx
            github.token=ghp_xxxxxxxxxxxxxxxxxxxx
            """.trimIndent()
        ),
        SampleDocument(
            "C2-pedagogie",
            2,
            """
            office/metiers/FPA/SPG_A2SP.adoc — Support Pédagogique Groupé, module A2SP.
            Objectif pédagogique : concevoir une action de formation professionnelle.
            Taxonomie de Bloom appliquée : niveau 4 (Analyse) et niveau 5 (Synthèse).
            Critères Qualiopi : indicateur 1 — information du public, indicateur 5 — qualification des formateurs.
            Exercice : rédiger un scénario pédagogique pour 12 apprenants de niveau Bac+2.
            """.trimIndent()
        ),
        SampleDocument(
            "C3-closed",
            3,
            """
            foundry/CSS/proprietary-algo/ — Code source closed-source, licence commerciale.
            Algorithme de matching propriétaire pour apprenants-formateurs.
            Utilise un graphe bipartite pondéré avec heuristique brevetée (dépôt INPI n°2026-XXXXX).
            Licence : commerciale, redistribution interdite. Tarif : 2500€/an/licence.
            """.trimIndent()
        ),
        SampleDocument(
            "C4-plugin",
            4,
            """
            foundry/OSS/plantuml-gradle/ — Plugin Gradle open source pour génération de diagrammes.
            Licence Apache 2.0 (SPDX: Apache-2.0).
            Utilise LangChain4j + Ollama pour générer des diagrammes PlantUML via RAG.
            Tests : 138 sessions agent, Cucumber BDD, JUnit 5, TestContainers.
            Dépendances : graphify-gradle (N0), codebase-gradle (N1). DAG N2.
            """.trimIndent()
        ),
        SampleDocument(
            "C4-readme",
            4,
            """
            build.gradle.kts — Script de build Gradle open source.
            plugins { kotlin("jvm") version "2.1.0" }
            dependencies { implementation("dev.langchain4j:langchain4j:1.12.2") }
            tasks.register("verifyReadMeToAnonymizedYaml") { ... }
            Licence : Apache 2.0 — tout le code dans foundry/OSS/ est public.
            """.trimIndent()
        ),
        SampleDocument(
            "C2-livre",
            2,
            """
            office/books-collection/kotlin-in-action.pdf — Livre technique Kotlin.
            Chapitre 6 : Types nullables et safety. Elvis operator ?:, safe call ?., let().
            Les data classes Kotlin génèrent automatiquement equals(), hashCode(), toString(), copy().
            Exercice 6.3 : implémenter une sealed class pour un résultat HTTP (Success/Error/Loading).
            """.trimIndent()
        )
    )
}

/**
 * Génère un contexte de remplissage pour atteindre un seuil de tokens.
 * Simule un contexte EAGER (règles, backlog, historique) ou un contexte
 * RAG (chunks de code source) selon le scénario.
 */
object ContextFiller {
    fun technicalDocumentationParagraph(tokenCount: Int): String {
        val sentences = listOf(
            "Le système d'orchestration dispatcher+experts s'appuie sur LangChain4j 1.12.2 pour la communication avec les modèles LLM locaux via Ollama.",
            "L'architecture DAG N0→N1→N2→N3 garantit que les plugins de niveau supérieur ne créent pas de dépendances cycliques avec les plugins de niveau inférieur.",
            "Le pattern builder Gradle utilise convention plugins et precompiled script plugins pour standardiser les configurations de build à travers les 8 plugins de l'écosystème.",
            "La couche d'abstraction BYOK (Bring Your Own Key) supporte 7 providers LLM : Anthropic, Gemini, HuggingFace, Mistral, Ollama, Grok et Groq, chacun avec multi-compte et expiration.",
            "Le pipeline d'embedding utilise AllMiniLmL6V2 via ONNX Runtime pour générer des vecteurs 384 dimensions stockés dans pgvector avec index IVFFlat pour la recherche de similarité cosinus.",
            "Les tests Cucumber BDD suivent le pattern Given-When-Then avec TestContainers pour provisionner un PostgreSQL éphémère contenant l'extension pgvector chargée automatiquement.",
            "Le pattern STIMULUS formalise la cascade de dilution automatique : détection de brain dump → mapping vers documents cibles → insertion contextuelle → archivage → suppression du stimulus.",
            "La classification Vision/Opinion via LangGraph4j utilise des conditional edges pour router les sections VISION vers la dilution publique et les sections OPINION vers le confinement privé.",
            "L'anonymisation RGPD multi-niveaux détecte les PII (tokens, emails, passwords) dans 5 formats (YAML, JSON, XML, properties, .env) avec fallback déterministe si le LLM est indisponible.",
            "Le serveur JBake intégré compile les templates Thymeleaf avec données YAML pour générer des sites statiques déployables via GitHub Pages avec workflow CI/CD automatisé.",
            "La task Gradle indexCodebase scanne récursivement le workspace, extrait le contenu des fichiers .kt/.adoc/.yml/.json, anonymise les secrets, tokenize en chunks de 500 tokens avec overlap 50, et indexe dans pgvector.",
            "Le fine-tuning des experts métiers CDA et FPA utilise la méthode continual pre-training avec 10% du corpus cible, validée par l'article ACL 2024 arXiv 2311.08545.",
            "Le dispatcher deepseek-v4-pro décompose les tâches complexes en sous-tâches atomiques distribuées par batch aux experts spécialisés, avec collecte et synthèse des résultats.",
            "La boucle de qualité ONNX valide chaque output expert via analyse de sentiment, détection de hors-sujet et contrôle de cohérence avant transmission au dispatcher pour la synthèse finale.",
            "Le protocole de mesure EPIC 4 évalue la dégradation de la perception spatiale du LLM à 10K, 30K, 60K, 100K et 128K tokens avec 5 scénarios de couverture incrémentale des canaux convergents."
        )
        val builder = StringBuilder()
        var remaining = tokenCount
        while (remaining > 0) {
            for (sentence in sentences) {
                if (remaining <= 0) break
                builder.appendLine(sentence)
                builder.appendLine()
                remaining -= (sentence.length / 4)
            }
        }
        return builder.toString()
    }

    fun fillerForThreshold(targetTokens: Int): String =
        technicalDocumentationParagraph(targetTokens)
}

/**
 * Exécute le benchmark de perception spatiale pour un scénario donné,
 * sur tous les seuils de tokens, et produit un BenchmarkReport.
 */
class BenchmarkRunner(
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "deepseek-v4-pro:cloud",
    private val timeoutSeconds: Long = 300,
    private val pgJdbcUrl: String? = null,
    private val pgUser: String? = null,
    private val pgPassword: String? = null,
    private val graphJsonPath: String? = null
) {
    private val log = LoggerFactory.getLogger(BenchmarkRunner::class.java)

    fun run(scenarioId: String, scenarioChannels: List<String>): String {
        val expert = BenchmarkExpertFactory.create(baseUrl, modelName, timeoutSeconds)
        val samples = BenchmarkFixtures.samples
        val thresholds = listOf(10000, 30000, 60000, 100000, 128000)
        val thresholdLabels = listOf("10K", "30K", "60K", "100K", "128K")

        val useRag = scenarioChannels.contains("RAG")
        val useGraphify = scenarioChannels.contains("Graphify")
        val vectorStore = if (useRag && pgJdbcUrl != null) VectorStore(pgJdbcUrl, pgUser ?: "codebase", pgPassword ?: "codebase") else null
        val embeddingPipeline = if (useRag && pgJdbcUrl != null) {
            log.info("RAG channel active — connecting to pgvector")
            EmbeddingPipeline(vectorStore!!)
        } else null
        val graphModel = if (useGraphify && graphJsonPath != null) {
            val f = File(graphJsonPath)
            if (f.exists()) {
                log.info("Graphify channel active — loading graph.json from {}", graphJsonPath)
                GraphContextBuilder.load(f)
            } else {
                log.warn("Graphify channel active but graph.json not found at {}", graphJsonPath)
                null
            }
        } else null

        val results = mutableListOf<String>()

        for ((idx, threshold) in thresholds.withIndex()) {
            val label = thresholdLabels[idx]
            val crossings = mutableListOf<String>()
            var errors = 0

            for (sample in samples) {
                val context = buildContext(scenarioChannels, sample, threshold, vectorStore, embeddingPipeline, graphModel)

                log.debug("{} @ {} — classifying {} (context={} chars)", scenarioId, label, sample.id, context.length)

                val classification = try {
                    expert.classify(context, sample.content)
                } catch (e: Exception) {
                    log.error("{} classification failed for {}: {}", scenarioId, sample.id, e.message?.take(100))
                    continue
                }

                if (classification.predictedCircle != sample.expectedCircle) {
                    errors++
                    crossings.add(
                        """{"documentId":"${sample.id}","expectedCircle":${sample.expectedCircle},""" +
                        """"actualCircle":${classification.predictedCircle},"confidenceScore":${classification.confidenceScore},""" +
                        """"excerpt":"${sample.content.take(100).replace("\n"," ").replace("\"","\\\"")}"}"""
                    )
                }
            }

            val errorRate = if (samples.isNotEmpty()) errors.toDouble() / samples.size else 0.0
            results.add(
                """{"threshold":"$label","totalSamples":${samples.size},"errorRate":$errorRate,"boundaryCrossings":[${crossings.joinToString(",")}]}"""
            )
        }

        val summary = "Scenario $scenarioId (${scenarioChannels.ifEmpty { listOf("BASELINE") }.joinToString("+")}) execute sur ${thresholds.size} seuils, ${samples.size} echantillons/seuil."

        val channelsJson = scenarioChannels.joinToString(", ") { "\"$it\"" }
        return """{"scenario":"$scenarioId","channels":[$channelsJson],"summary":"$summary","results":[${results.joinToString(",")}]}"""
    }

    private fun buildContext(
        channels: List<String>,
        sample: SampleDocument,
        targetTokens: Int,
        vectorStore: VectorStore?,
        embeddingPipeline: EmbeddingPipeline?,
        graphModel: GraphModel?
    ): String {
        val isBaseline = channels.isEmpty()
        val sb = StringBuilder()

        var ragTokens = 0
        var graphTokens = 0
        val fillerBudget = if (isBaseline) targetTokens else targetTokens / 2

        if (channels.contains("RAG") && vectorStore != null && embeddingPipeline != null) {
            sb.appendLine("=== RAG Context (pgvector) ===")
            try {
                val queryVec = embeddingPipeline.embedQuery("cercles de confiance workspace foundry office configuration")
                val results = vectorStore.querySimilar(queryVec, topK = 10)
                for (r in results) {
                    val chunkTokens = r.text.length / 4
                    if (ragTokens + chunkTokens > targetTokens / 3) break
                    sb.appendLine("[${String.format("%.2f", r.similarity)}] ${r.text}")
                    sb.appendLine()
                    ragTokens += chunkTokens
                }
            } catch (e: Exception) {
                log.error("RAG query failed: {}", e.message?.take(80))
            }
        }

        if (channels.contains("Graphify") && graphModel != null) {
            sb.appendLine("=== Graphify Context (knowledge graph) ===")
            val samplePath = when (sample.id) {
                "C0-strategie" -> "WORKSPACE_AS_PRODUCT.adoc"
                "C4-plugin" -> "foundry/OSS/plantuml-gradle"
                "C4-readme" -> "foundry/OSS/codebase-gradle"
                else -> ""
            }
            if (samplePath.isNotEmpty()) {
                val neighborhood = GraphContextBuilder.neighborhood(graphModel, samplePath)
                sb.append(neighborhood)
                sb.appendLine()
                graphTokens = neighborhood.length / 4
            } else {
                sb.appendLine("Graph summary: ${graphModel.nodes.size} nodes, ${graphModel.edges.size} edges")
                val topDirs = graphModel.nodes.filter { it.type == "directory" }.take(6)
                for (d in topDirs) sb.appendLine("  - ${d.label} [${d.id}]")
            }
        }

        val remaining = (fillerBudget - ragTokens - graphTokens).coerceAtLeast(500)
        sb.append(ContextFiller.fillerForThreshold(remaining))

        return sb.toString()
    }
}
