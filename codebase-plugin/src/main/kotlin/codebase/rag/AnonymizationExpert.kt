package codebase.rag

import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import java.time.Duration

data class ExpertConfig(
    val baseUrl: String = "http://localhost:11434",
    val modelName: String = "deepseek-v4-pro:cloud",
    val timeoutSeconds: Long = 120
)

interface AnonymizationExpert {
    @SystemMessage(
        """
        Tu es un expert RGPD spécialisé en protection des données personnelles.
        Ta mission : détecter et anonymiser toute information personnellement identifiable (PII)
        dans des fichiers de configuration, code source, et documentation technique.

        Niveaux de sensibilité RGPD :
        - 0 : public — aucune donnée personnelle
        - 1 : interne — emails, noms d'utilisateur
        - 2 : confidentiel — tokens API, clés SSH, mots de passe
        - 3 : restreint — données bancaires, santé, biométrie
        - 4 : secret — secrets d'État, défense nationale (ne devrait jamais apparaître)

        Format de réponse OBLIGATOIRE — JSON strict, aucun texte avant ou après :
        {
          "anonymizedContent": "le contenu après anonymisation",
          "confidenceScore": 0.95,
          "detectedPiiCategories": ["email", "api_token", "password"],
          "replacedCount": 3,
          "summary": "3 PII détectées : 1 email, 1 token API, 1 mot de passe. Anonymisées."
        }

        RÈGLES :
        - Remplace TOUJOURS les tokens API par "***"
        - Remplace les emails par "anonymous@acme.com"
        - Remplace les mots de passe par "***"
        - Remplace les clés API (aws, gcp, azure) par "***"
        - Remplace les noms/prénoms par "Anonymous"
        - Préserve la structure exacte du document (YAML, JSON, XML, code)
        - Ne modifie QUE les valeurs sensibles, jamais les clés ou la syntaxe
        """
    )
    @UserMessage(
        """
        Analyse le contenu de ce fichier {{request.sourcePath}} au format {{request.targetFormat}}.
        
        Contenu :
        {{request.content}}
        
        Retourne UNIQUEMENT le JSON d'anonymisation. Aucun autre texte.
        """
    )
    fun anonymizeRequest(request: AnonymizationRequest): AnonymizationResult
}

object AnonymizationExpertFactory {

    private val log = System.err

    fun create(config: ExpertConfig = ExpertConfig()): AnonymizationExpert {
        return try {
            val model = OllamaChatModel.builder()
                .baseUrl(config.baseUrl)
                .modelName(config.modelName)
                .timeout(Duration.ofSeconds(config.timeoutSeconds))
                .build()

            val expert = AiServices.builder(AnonymizationExpert::class.java)
                .chatModel(model)
                .build()

            log.println("[EPIC-2] Expert LLM connecté: ${config.baseUrl}/${config.modelName}")
            expert
        } catch (e: Exception) {
            log.println("[EPIC-2] Fallback déterministe (${e.message?.take(80)})")
            DeterministicExpert
        }
    }
}

object DeterministicExpert : AnonymizationExpert {
    override fun anonymizeRequest(request: AnonymizationRequest): AnonymizationResult {
        val result = YamlConfigAnonymizer.anonymize(request.content, request.targetFormat)

        val replacements = listOf("***", "anonymous@acme.com", "Anonymous")
            .count { it in result }

        val categories = mutableListOf<String>()
        val lower = request.content.lowercase()
        if ("token" in lower || "key" in lower) categories.add("api_token")
        if ("password" in lower || "passwd" in lower) categories.add("password")
        if ("@" in request.content) categories.add("email")

        val cleanLines = result.lines().filterNot { it.isBlank() }.size
        val originalLines = request.content.lines().size

        return AnonymizationResult(
            anonymizedContent = result,
            confidenceScore = if (replacements > 0) 0.85 else 1.0,
            detectedPiiCategories = categories,
            replacedCount = replacements,
            summary = "Déterministe: $replacements PII (${categories.joinToString()}). $cleanLines/$originalLines lignes."
        )
    }
}
