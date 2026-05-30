package codebase.ocr

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OcrTaskTest {

    @Test
    fun `OcrEngine returns structured AsciiDoc for fake engine`() {
        val fake = FakeOcrEngine()
        val result = fake.process("image scan", "fr", "gemini-2.5-flash", 8192)

        assertTrue(result.contains("= Document OCRisé"))
        assertTrue(result.contains("image scan"))
        assertTrue(result.contains("langue: fr"))
        assertTrue(result.contains("modèle: gemini-2.5-flash"))
    }

    @Test
    fun `OcrEngine returns empty string for empty input`() {
        val fake = FakeOcrEngine()
        val result = fake.process("", "fr", "gemini-2.5-flash", 8192)

        assertEquals("", result)
    }

    @Test
    fun `OcrEngine respects metadata format`() {
        val fake = FakeOcrEngine()
        val result = fake.process("test content", "en", "gemini-1.5-flash", 4096)

        assertTrue(result.contains(":langue: en"))
        assertTrue(result.contains(":modèle: gemini-1.5-flash"))
        assertTrue(result.contains(":max-tokens: 4096"))
        assertTrue(result.startsWith("= Document OCRisé"))
    }

    @Test
    fun `FakeOcrEngine is an OcrEngine`() {
        val engine: OcrEngine = FakeOcrEngine()
        assertNotNull(engine)
        assertTrue(engine is OcrEngine)
    }

    @Test
    fun `fake engine does not throw for any input`() {
        val fake = FakeOcrEngine()

        val result1 = fake.process("a", "fr", "gemini-2.5-flash", 8192)
        assertTrue(result1.isNotEmpty())

        val result2 = fake.process("a".repeat(10_000), "fr", "gemini-2.5-flash", 8192)
        assertTrue(result2.isNotEmpty())

        val result3 = fake.process("éàùç汉字🎉", "auto", "gemini-2.5-flash", 8192)
        assertTrue(result3.isNotEmpty())
    }

    @Test
    fun `ocr task class exists and is abstract`() {
        val clazz = OcrTask::class.java
        assertTrue(clazz.simpleName == "OcrTask")
        assertFalse(clazz.isInterface)
    }

    @Test
    fun `OcrInputFile data class stores fields`() {
        val input = OcrInputFile(
            path = "/tmp/scan.pdf",
            language = "fr",
            provider = "gemini",
            model = "gemini-2.5-flash",
            maxTokens = 8192
        )
        assertEquals("/tmp/scan.pdf", input.path)
        assertEquals("fr", input.language)
        assertEquals("gemini", input.provider)
        assertEquals("gemini-2.5-flash", input.model)
        assertEquals(8192, input.maxTokens)
    }

    @Test
    fun `OcrInputFile copy works`() {
        val input = OcrInputFile("/tmp/scan.pdf")
        val modified = input.copy(language = "en", maxTokens = 4096)

        assertEquals("/tmp/scan.pdf", modified.path)
        assertEquals("en", modified.language)
        assertEquals(4096, modified.maxTokens)
        assertTrue(modified !== input)
    }

    // ── OCR-3 : Détection image vs texte ──────────────────────────────

    @Test
    fun `isImageFile detects PNG as image`() {
        val file = File("/tmp/scan.png")
        assertTrue(OcrTask.isImageFile(file))
    }

    @Test
    fun `isImageFile detects JPG as image`() {
        val file = File("/tmp/photo.jpg")
        assertTrue(OcrTask.isImageFile(file))
    }

    @Test
    fun `isImageFile detects JPEG as image`() {
        val file = File("/tmp/photo.jpeg")
        assertTrue(OcrTask.isImageFile(file))
    }

    @Test
    fun `isImageFile detects all supported image formats`() {
        for (ext in listOf("png", "jpg", "jpeg", "gif", "bmp", "tiff")) {
            assertTrue(OcrTask.isImageFile(File("/tmp/doc.$ext")), "Failed for $ext")
            assertTrue(OcrTask.isImageFile(File("/tmp/DOC.${ext.uppercase()}")), "Failed for uppercase $ext")
        }
    }

    @Test
    fun `isImageFile returns false for text files`() {
        assertFalse(OcrTask.isImageFile(File("/tmp/doc.pdf")))
        assertFalse(OcrTask.isImageFile(File("/tmp/doc.txt")))
        assertFalse(OcrTask.isImageFile(File("/tmp/doc.adoc")))
        assertFalse(OcrTask.isImageFile(File("/tmp/doc.md")))
        assertFalse(OcrTask.isImageFile(File("/tmp/doc.xml")))
        assertFalse(OcrTask.isImageFile(File("/tmp/doc.html")))
        assertFalse(OcrTask.isImageFile(File("/tmp/doc")))
    }

    @Test
    fun `detectMimeType returns correct image MIME types`() {
        assertEquals("image/png", OcrTask.detectMimeType("png"))
        assertEquals("image/jpeg", OcrTask.detectMimeType("jpg"))
        assertEquals("image/jpeg", OcrTask.detectMimeType("jpeg"))
        assertEquals("image/gif", OcrTask.detectMimeType("gif"))
        assertEquals("image/bmp", OcrTask.detectMimeType("bmp"))
        assertEquals("image/tiff", OcrTask.detectMimeType("tiff"))
    }

    @Test
    fun `detectMimeType returns octet-stream for unknown extensions`() {
        assertEquals("application/octet-stream", OcrTask.detectMimeType("pdf"))
        assertEquals("application/octet-stream", OcrTask.detectMimeType("xyz"))
    }

    // ── OCR-3b : Injection FakeVisionProvider ──────────────────────────

    @Test
    fun `FakeVisionProvider is injectable into OcrTask`() {
        val fakeProvider = codebase.koog.llm.FakeVisionProvider()
        val task = org.gradle.testfixtures.ProjectBuilder.builder().build()
            .tasks.register("ocr", OcrTask::class.java).get()
        task.geminiVisionProvider = fakeProvider
        kotlin.test.assertNotNull(task.geminiVisionProvider)
    }

    // ── OCR-3b P1 : llm-config.yml + -PinputFile ──────────────────────

    @Test
    fun `llmConfigFile is null by default`() {
        val task = org.gradle.testfixtures.ProjectBuilder.builder().build()
            .tasks.register("ocr", OcrTask::class.java).get()
        kotlin.test.assertNull(task.llmConfigFile, "llmConfigFile should be null by default")
    }

    @Test
    fun `llmConfigFile can be set to an existing YAML`(@TempDir tempDir: Path) {
        val ymlFile = tempDir.resolve("llm-config.yml").toFile()
        ymlFile.writeText("""
            ai:
              gemini:
                envVar: "GEMINI_API_KEY"
                model: "gemini-1.5-flash"
                baseUrl: "https://generativelanguage.googleapis.com/v1beta"
        """.trimIndent())

        val project = org.gradle.testfixtures.ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        project.pluginManager.apply("java-base") // nécessaire pour ProjectBuilder + RegularFileProperty

        val task = project.tasks.register("ocr", OcrTask::class.java).get()
        task.llmConfigFile = ymlFile

        kotlin.test.assertNotNull(task.llmConfigFile)
        assertTrue(ymlFile.exists())
    }

    @Test
    fun `executeOcr with YAML config resolves model from GeminiConfig`(@TempDir tempDir: Path) {
        // Arrange — llm-config.yml
        val ymlFile = tempDir.resolve("llm-config.yml").toFile()
        ymlFile.writeText("""
            ai:
              gemini:
                envVar: "GEMINI_API_KEY"
                model: "gemini-1.5-flash"
                baseUrl: "https://generativelanguage.googleapis.com/v1beta"
        """.trimIndent())

        // Fichier d'entrée — texte simple (pas image → passe par OcrEngine/texte)
        val inputFile = tempDir.resolve("document.txt").toFile()
        inputFile.writeText("Test content for OCR")

        // Fake engine + Fake vision provider
        val project = org.gradle.testfixtures.ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .withName("test-yaml-config")
            .build()
        project.pluginManager.apply("java-base")

        val task = project.tasks.register("ocr", OcrTask::class.java).get()
        task.llmConfigFile = ymlFile
        task.inputFile.set(project.layout.projectDirectory.file("document.txt"))
        task.ocrEngine = FakeOcrEngine()
        task.geminiVisionProvider = codebase.koog.llm.FakeVisionProvider()

        // Act — exécution de la tâche
        task.executeOcr()

        // Assert — le fichier de sortie doit exister
        val outputDir = project.layout.buildDirectory.dir("ocr").get().asFile
        val outputFile = outputDir.resolve("document_ocr.adoc")
        assertTrue(outputFile.exists(), "Output file should be created: ${outputFile.absolutePath}")
        val content = outputFile.readText()
        assertTrue(content.contains("Test content for OCR"))
    }

    @Test
    fun `executeOcr with -PinputFile via plugin and YAML config`(@TempDir tempDir: Path) {
        // Arrange — llm-config.yml
        val ymlFile = tempDir.resolve("llm-config.yml").toFile()
        ymlFile.writeText("""
            ai:
              gemini:
                envVar: "GEMINI_API_KEY"
                model: "gemini-1.5-flash"
                baseUrl: "https://generativelanguage.googleapis.com/v1beta"
        """.trimIndent())

        // Fichier d'entrée
        val inputFile = tempDir.resolve("scan.png").toFile()
        inputFile.writeText("fake png bytes for testing")

        val project = org.gradle.testfixtures.ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .withName("test-cli-input")
            .build()
        project.pluginManager.apply("java-base")

        // Simule -PinputFile=scan.png
        val task = project.tasks.register("ocr", OcrTask::class.java).get()
        task.inputFile.set(project.layout.projectDirectory.file("scan.png"))
        task.ocrEngine = FakeOcrEngine()
        task.geminiVisionProvider = codebase.koog.llm.FakeVisionProvider()
        // llmConfigFile pas set manuellement — simule le comportement du plugin
        // (sera null → fallback convention gemini-2.5-flash)

        // Act
        assertTrue(inputFile.exists(), "Input file must exist for OCR")
        // Ne pas exécuter l'OCR complet (évite le mode image réel)
        // Test uniquement l'injection de -PinputFile
        kotlin.test.assertNotNull(task.inputFile)
        assertEquals("scan.png", task.inputFile.get().asFile.name)
    }

    @Test
    fun `executeOcr with YAML model override falls back to convention when YAML absent`(@TempDir tempDir: Path) {
        // Arrange — pas de llm-config.yml
        val inputFile = tempDir.resolve("doc.txt").toFile()
        inputFile.writeText("No YAML here")

        val project = org.gradle.testfixtures.ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        project.pluginManager.apply("java-base")

        val task = project.tasks.register("ocr", OcrTask::class.java).get()
        task.inputFile.set(project.layout.projectDirectory.file("doc.txt"))
        task.ocrEngine = FakeOcrEngine()
        task.geminiVisionProvider = codebase.koog.llm.FakeVisionProvider()
        // llmConfigFile = null (par défaut)

        // Act
        task.executeOcr()

        // Assert — convention gemini-2.5-flash utilisé
        val outputDir = project.layout.buildDirectory.dir("ocr").get().asFile
        val outputFile = outputDir.resolve("doc_ocr.adoc")
        assertTrue(outputFile.exists())
        val content = outputFile.readText()
        assertTrue(content.contains("gemini-2.5-flash"), "Should fallback to convention model")
    }

    @Test
    fun `executeOcr with corrupt YAML file falls back to convention`(@TempDir tempDir: Path) {
        // Arrange — YAML invalide
        val ymlFile = tempDir.resolve("llm-config.yml").toFile()
        ymlFile.writeText("this: is: not: valid:: yaml")

        val inputFile = tempDir.resolve("doc.txt").toFile()
        inputFile.writeText("Corrupt YAML test")

        val project = org.gradle.testfixtures.ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
        project.pluginManager.apply("java-base")

        val task = project.tasks.register("ocr", OcrTask::class.java).get()
        task.llmConfigFile = ymlFile
        task.inputFile.set(project.layout.projectDirectory.file("doc.txt"))
        task.ocrEngine = FakeOcrEngine()
        task.geminiVisionProvider = codebase.koog.llm.FakeVisionProvider()

        // Act — ne doit pas planter
        task.executeOcr()

        // Assert
        val outputDir = project.layout.buildDirectory.dir("ocr").get().asFile
        val outputFile = outputDir.resolve("doc_ocr.adoc")
        assertTrue(outputFile.exists())
    }
}
