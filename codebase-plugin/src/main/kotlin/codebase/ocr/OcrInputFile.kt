package codebase.ocr

/**
 * Fichier d'entrée pour l'OCR.
 *
 * Transporte les métadonnées du fichier à traiter et les paramètres Gemini.
 * Pattern data class immuable — testable unitairement sans Gradle.
 */
data class OcrInputFile(
    /** Chemin absolu du fichier à OCR-iser */
    val path: String,
    /** Langue source (fr, en, auto) */
    val language: String = "fr",
    /** Fournisseur IA (gemini, tesseract) */
    val provider: String = "gemini",
    /** Modèle Gemini */
    val model: String = "gemini-2.5-flash",
    /** Nombre maximum de tokens pour la requête */
    val maxTokens: Int = 8192
)
