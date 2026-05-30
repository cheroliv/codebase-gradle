package codebase.koog.llm

import codebase.rag.GeminiConfig
import dev.langchain4j.data.message.ImageContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

class GeminiVisionProvider(
    private val config: GeminiConfig = GeminiConfig()
) : VisionProvider {

    private val log = LoggerFactory.getLogger(GeminiVisionProvider::class.java)

    private var cachedModel: GoogleAiGeminiChatModel? = null
    private var cachedModelName: String? = null
    private var cachedMaxTokens: Int = -1

    override suspend fun processImage(
        imageBytes: ByteArray,
        mimeType: String,
        language: String,
        model: String,
        maxTokens: Int
    ): String {
        log.info(
            "[GeminiVision] Processing image: mimeType={}, language={}, model={}, maxTokens={}, imageSize={}bytes",
            mimeType, language, model, maxTokens, imageBytes.size
        )

        val currentModel = resolveModel(model, maxTokens)
        val ocrPrompt = buildOcrPrompt(language, model)
        val base64Image = Base64.getEncoder().encodeToString(imageBytes)
        val userMessage = UserMessage.from(
            ImageContent(base64Image, mimeType),
            TextContent(ocrPrompt)
        )

        return try {
            val response = withContext(Dispatchers.IO) {
                currentModel.chat(userMessage)
            }
            val text = response.aiMessage().text()
            log.info("[GeminiVision] Response received: length={}", text.length)
            text
        } catch (e: Exception) {
            throw IllegalStateException(
                "Gemini Vision OCR failed for model=$model: ${e.message}", e
            )
        }
    }

    private fun resolveModel(modelName: String, maxTokens: Int): GoogleAiGeminiChatModel {
        if (modelName == cachedModelName && maxTokens == cachedMaxTokens && cachedModel != null) {
            return cachedModel!!
        }
        val apiKey = config.resolveApiKey()
        log.info("[GeminiVision] Initializing model: model={}, maxOutputTokens={}", modelName, maxTokens)
        val newModel = GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .maxOutputTokens(maxTokens)
            .build()
        cachedModel = newModel
        cachedModelName = modelName
        cachedMaxTokens = maxTokens
        return newModel
    }

    private fun buildOcrPrompt(language: String, model: String): String {
        val date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return """
            Tu es un expert OCR. Analyse l'image ci-dessous et extrait TOUT le texte visible.
            Langue source : $language
            Format de sortie : AsciiDoc structuré avec :
            - Titres de section (== Titre)
            - Paragraphes
            - Tableaux (|=== ... |===) si présents
            - Listes à puces (* item)
            - Métadonnées en commentaire AsciiDoc (// OCR par Gemini Vision, modèle: $model, date: $date)

            RÈGLES :
            - Ne pas inventer de texte qui n'est pas dans l'image
            - Si l'image est floue ou illisible, indiquer "[OCR] Zone illisible" en commentaire
            - Conserver la mise en page logique (colonnes, tableaux)
            - Indiquer la confiance estimée en commentaire (// Confiance: haute/moyenne/basse)
        """.trimMargin()
    }
}
