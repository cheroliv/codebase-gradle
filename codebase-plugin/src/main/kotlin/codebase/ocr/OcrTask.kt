package codebase.ocr

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Tâche Gradle d'OCR assisté IA (Gemini Vision).
 *
 * Pattern : `abstract class DefaultTask` + `@Option` + `@Input`/`@Output` —
 * aligné sur QualityGateTask, VibecodingTask.
 *
 * Usage CLI :
 * ```
 * ./gradlew ocrDocument -PinputFile=/tmp/scan.pdf -PocrLanguage=en
 * ./gradlew ocrDocument -PinputDir=/tmp/scans/   # mode batch
 * ```
 *
 * Usage DSL :
 * ```
 * codebaseOcr {
 *     ocrProvider = "gemini"
 *     geminiApiKeys = listOf(System.getenv("GEMINI_API_KEY_1") ?: "")
 * }
 * ```
 *
 * Injection OcrEngine : en test → FakeOcrEngine, en production → GeminiVisionEngine.
 */
@DisableCachingByDefault(because = "OCR IA — appel LLM non-déterministe, non-cacheable")
abstract class OcrTask : DefaultTask() {

    /**
     * Moteur OCR injectable.
     * En test : `FakeOcrEngine`. En production : `GeminiVisionEngine` (OCR-2).
     * `@get:Internal` car n'est pas un paramètre de build, mais une dépendance d'exécution.
     */
    @get:Internal
    var ocrEngine: OcrEngine = NoOpOcrEngine()

    @get:Input
    @get:Optional
    @get:Option(option = "ocrProvider", description = "Fournisseur IA : gemini ou tesseract")
    abstract val ocrProvider: Property<String>

    @get:InputFile
    @get:Optional
    @get:Option(option = "inputFile", description = "Fichier à OCR-iser (mode single-file)")
    abstract val inputFile: RegularFileProperty

    @get:Input
    @get:Optional
    @get:Option(option = "ocrLanguage", description = "Langue source : fr, en, auto")
    abstract val ocrLanguage: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "geminiModel", description = "Modèle Gemini : gemini-2.5-flash, gemini-2.5-pro")
    abstract val geminiModel: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "maxTokens", description = "Nombre maximum de tokens pour la requête")
    abstract val maxTokens: Property<Int>

    @get:OutputFile
    @get:Optional
    @get:Option(option = "outputFile", description = "Fichier de sortie (défaut : build/ocr/{filename}.adoc)")
    abstract val outputFile: RegularFileProperty

    @get:Input
    @get:Optional
    @get:Option(option = "outputFormat", description = "Format de sortie : asciidoc, markdown, text")
    abstract val outputFormat: Property<String>

    init {
        group = "collect"
        description = "OCR assisté IA — extrait le texte structuré d'un document scanné via Gemini Vision"
        ocrProvider.convention("gemini")
        ocrLanguage.convention("fr")
        geminiModel.convention("gemini-2.5-flash")
        maxTokens.convention(8192)
        outputFormat.convention("asciidoc")
    }

    @TaskAction
    fun executeOcr() {
        val provider = ocrProvider.orNull ?: "gemini"

        val inputPath = if (inputFile.isPresent) {
            inputFile.get().asFile.absolutePath
        } else {
            throw IllegalArgumentException(
                "Aucun fichier d'entrée spécifié. Utilisez -PinputFile=/path/to/file " +
                "ou configurez codebaseOcr { inputDir = file(\"...\") }"
            )
        }

        val file = File(inputPath)
        if (!file.exists()) {
            throw IllegalArgumentException("Fichier d'entrée introuvable : $inputPath")
        }

        val lang = ocrLanguage.orNull ?: "fr"
        val model = geminiModel.orNull ?: "gemini-2.5-flash"
        val tokens = maxTokens.orNull ?: 8192
        val format = outputFormat.orNull ?: "asciidoc"

        logger.lifecycle(
            "[OCR] Démarrage : fichier={}, langue={}, fournisseur={}, modèle={}, maxTokens={}",
            file.name, lang, provider, model, tokens
        )

        val content = file.readText(Charsets.UTF_8)
        val result = ocrEngine.process(content, lang, model, tokens)

        val outputDir = project.layout.buildDirectory.dir("ocr").get().asFile
        outputDir.mkdirs()

        val ext = when (format) {
            "markdown" -> ".md"
            "text" -> ".txt"
            else -> ".adoc"
        }
        val baseName = file.nameWithoutExtension
        val outputPath = if (outputFile.isPresent) {
            outputFile.get().asFile
        } else {
            File(outputDir, "${baseName}_ocr$ext")
        }

        outputPath.writeText(result, Charsets.UTF_8)
        logger.lifecycle("[OCR] Résultat écrit dans : {}", outputPath.absolutePath)
    }
}
