package codebase.scenarios.ocr

import codebase.CodebasePlugin
import codebase.ocr.FakeOcrEngine
import codebase.ocr.OcrTask
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testfixtures.ProjectBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OcrSteps {

    private val log = LoggerFactory.getLogger(OcrSteps::class.java)
    private var tmpDir: File? = null
    private var lastOutputPath: File? = null
    private var foundTask: OcrTask? = null
    private var foundGroup: String? = null
    private lateinit var testFileName: String
    private lateinit var baseName: String

    @Given("an OCR test file {string} with text {string}")
    fun createOcrTestFile(filename: String, text: String) {
        tmpDir = Files.createTempDirectory("ocr-cucumber").toFile()
        val file = File(tmpDir, filename)
        file.writeText(text, Charsets.UTF_8)
        testFileName = filename
        baseName = file.nameWithoutExtension
    }

    @Given("the codebase plugin is applied")
    fun applyCodebasePlugin() {
        tmpDir = Files.createTempDirectory("ocr-cucumber").toFile()
        testFileName = "dummy.txt"
        baseName = "dummy"
    }

    @When("I OCR {string} in French")
    fun ocrInFrench(filename: String) = ocr(filename, "fr", "asciidoc")

    @When("I OCR {string} in English")
    fun ocrInEnglish(filename: String) = ocr(filename, "en", "asciidoc")

    @When("I OCR {string} in French with format {string}")
    fun ocrWithFormat(filename: String, format: String) = ocr(filename, "fr", format)

    @When("I check for task {string}")
    fun checkForTask(taskName: String) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tmpDir!!)
            .withName("lookup")
            .build()
        project.pluginManager.apply(CodebasePlugin::class.java)
        foundTask = project.tasks.findByName(taskName) as? OcrTask
        foundGroup = foundTask?.group
    }

    @Then("the OCR result for {string} exists")
    fun ocrResultExists(expectedBase: String) {
        assertNotNull(lastOutputPath, "No OCR output path recorded")
        assertTrue(lastOutputPath!!.exists(), "OCR output should exist: ${lastOutputPath!!.absolutePath}")
    }

    @Then("the OCR result for {string} contains {string}")
    fun ocrResultContains(expectedBase: String, text: String) {
        val content = lastOutputPath!!.readText(Charsets.UTF_8)
        assertTrue(content.contains(text), "Expected '$text' in OCR output. Got:\n$content")
    }

    @Then("the OCR result for {string} ends with {string}")
    fun ocrResultEndsWith(expectedBase: String, suffix: String) {
        assertTrue(lastOutputPath!!.name.endsWith(suffix), "Expected suffix '$suffix': ${lastOutputPath!!.name}")
    }

    @Then("task {string} should be registered")
    fun taskShouldBeRegistered(taskName: String) {
        assertNotNull(foundTask, "Task '$taskName' should be registered")
    }

    @Then("task {string} should be in group {string}")
    fun taskShouldBeInGroup(taskName: String, group: String) {
        assertNotNull(foundTask, "Task '$taskName' should exist")
        assertEquals(group, foundGroup, "Task group mismatch")
    }

    @After
    fun cleanup() {
        tmpDir?.deleteRecursively()
    }

    private fun ocr(filename: String, language: String, format: String) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tmpDir!!)
            .withName("ocr-exec")
            .build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val inputFile = File(tmpDir, filename)
        val task = project.tasks.getByName("ocrDocument") as OcrTask
        task.ocrEngine = FakeOcrEngine()
        task.inputFile.set(project.layout.projectDirectory.file(filename))
        task.ocrLanguage.set(language)
        task.outputFormat.set(format)
        task.executeOcr()

        val ext = when (format) {
            "markdown" -> ".md"
            "text" -> ".txt"
            else -> ".adoc"
        }
        lastOutputPath = project.layout.buildDirectory.dir("ocr").get().asFile
            .resolve("${inputFile.nameWithoutExtension}_ocr$ext")
    }
}
