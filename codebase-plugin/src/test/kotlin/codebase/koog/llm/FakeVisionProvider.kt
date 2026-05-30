package codebase.koog.llm

import codebase.rag.GeminiConfig
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Fake Vision Provider for tests — zero network call.
 *
 * Same signature as [GeminiVisionProvider.processImage] but returns
 * a deterministic structured AsciiDoc snippet built from the input metadata.
 * Useful for validating the OCR pipeline plumbing without a real Gemini API key.
 */
class FakeVisionProvider(
    private val config: GeminiConfig = GeminiConfig()
) : VisionProvider {
    private val log = LoggerFactory.getLogger(FakeVisionProvider::class.java)

    override suspend fun processImage(
        imageBytes: ByteArray,
        mimeType: String,
        language: String,
        model: String,
        maxTokens: Int
    ): String {
        log.info("[FakeVision] Returning fake OCR for mimeType={}, size={}bytes", mimeType, imageBytes.size)
        val encodedPreview = Base64.getEncoder().encodeToString(imageBytes.take(16).toByteArray())
        val langLabel = when (language.lowercase()) {
            "fr" -> "Titre Principal"
            "en" -> "Main Title"
            "de" -> "Haupttitel"
            else -> "Title"
        }
        return """
            = $langLabel
            // OCR par FakeVisionProvider (test)
            // Confiance: haute (mock)
            // Modèle mocké: $model
            // Langue: $language
            // Taille image: ${imageBytes.size} bytes
            // MIME: $mimeType
            // Preview base64: $encodedPreview

            == Section 1

            Ceci est un paragraphe extrait automatiquement par le moteur OCR factice.
            Il simule un résultat structuré sans appel réseau.

            == Section 2

            * Premier élément de liste
            * Deuxième élément
            * Troisième avec des **caractères gras**

            |===
            | Colonne A | Colonne B
            | Valeur 1  | Valeur 2
            | Valeur 3  | Valeur 4
            |===
        """.trimIndent()
    }
}
