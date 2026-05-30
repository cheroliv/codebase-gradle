package codebase.ocr

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Extension DSL pour l'OCR assisté IA (Gemini Vision).
 *
 * Pattern : `abstract class` + `Property<T>` — aligné sur PlannerExtension/TrainingExtension.
 * Prioirité de résolution : -P CLI > env var > DSL > convention.
 *
 * Usage DSL (build.gradle.kts) :
 * ```
 * codebaseOcr {
 *     ocrProvider = "gemini"
 *     geminiApiKeys = listOf(System.getenv("GEMINI_API_KEY_1") ?: "")
 *     geminiModel = "gemini-2.5-flash"
 *     ocrLanguage = "fr"
 *     outputFormat = "asciidoc"
 *     ocrEnabled = true
 * }
 * ```
 *
 * Usage CLI :
 * ```
 * ./gradlew ocrDocument -PocrProvider=tesseract -PocrLanguage=en
 * ```
 */
abstract class CodebaseOcrExtension {

    /**
     * Fournisseur IA pour l'OCR.
     * Valeurs supportées : `"gemini"`, `"tesseract"` (fallback sans IA).
     * Par défaut : `"gemini"`.
     */
    abstract val ocrProvider: Property<String>

    /**
     * Clés API Gemini, une par compte Google.
     * Injection via env vars `GEMINI_API_KEY_1..N` ou DSL `geminiApiKeys`.
     * Rotation : ROUND_ROBIN via GeminiKeyPool (OCR-1).
     */
    abstract val geminiApiKeys: ListProperty<String>

    /**
     * Modèle Gemini à utiliser pour l'OCR.
     * Supporte les modèles multimodaux : gemini-2.5-flash, gemini-2.5-pro, gemini-1.5-flash.
     * Par défaut : `"gemini-2.5-flash"` (meilleur rapport qualité/coût).
     */
    abstract val geminiModel: Property<String>

    /**
     * Langue source pour l'OCR.
     * Valeurs : `"fr"`, `"en"`, `"auto"` (détection automatique).
     * Par défaut : `"fr"`.
     */
    abstract val ocrLanguage: Property<String>

    /**
     * Répertoire contenant les documents à OCR-iser (mode batch).
     * En mode single-file, utiliser `-PinputFile` en CLI.
     * Par défaut : `project.projectDir`.
     */
    abstract val inputDir: DirectoryProperty

    /**
     * Format de sortie du texte extrait.
     * Valeurs supportées : `"asciidoc"`, `"markdown"`, `"text"`.
     * Par défaut : `"asciidoc"` (format pivot du workspace).
     */
    abstract val outputFormat: Property<String>

    /**
     * Active/désactive l'OCR dans le pipeline.
     * Par défaut `false` — l'utilisateur doit explicitement activer l'OCR
     * pour éviter des appels API Gemini non intentionnels.
     */
    abstract val ocrEnabled: Property<Boolean>

    /**
     * Nombre maximum de tokens pour la requête Gemini.
     * Par défaut : 8192 (suffisant pour des documents A4 standards).
     */
    abstract val maxTokens: Property<Int>
}
