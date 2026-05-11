package codebase.scenarios

import codebase.rag.ClassificationReport
import codebase.rag.ContentClassification
import codebase.rag.SectionClassification
import codebase.rag.TestSections
import codebase.rag.VisionOpinionClassifier
import codebase.rag.VisionOpinionClassifierMain
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VisionOpinionClassifierSteps {

    private val log = LoggerFactory.getLogger(VisionOpinionClassifierSteps::class.java)

    @Given("the vision/opinion test sections dataset")
    fun `load test sections`() {
        val sections = TestSections.all
        assertNotNull(sections, "Test sections dataset should not be null")
        log.info("Vision/Opinion test sections loaded")
    }

    @Then("there are exactly {int} test sections")
    fun `exact section count`(expected: Int) {
        assertEquals(expected, TestSections.all.size,
            "Expected $expected sections, got ${TestSections.all.size}")
    }

    @And("exactly {int} sections are expected VISION")
    fun `vision count`(expected: Int) {
        val actual = TestSections.all.count { it.expectedClassification == ContentClassification.VISION }
        assertEquals(expected, actual,
            "Expected $expected VISION sections, got $actual")
    }

    @And("exactly {int} sections are expected OPINION")
    fun `opinion count`(expected: Int) {
        val actual = TestSections.all.count { it.expectedClassification == ContentClassification.OPINION }
        assertEquals(expected, actual,
            "Expected $expected OPINION sections, got $actual")
    }

    @And("each test section has a non-empty content")
    fun `non empty content`() {
        for (s in TestSections.all) {
            assertTrue(s.content.isNotBlank(),
                "Section ${s.id} has empty content")
        }
        log.info("All sections have non-empty content")
    }

    @And("each test section has a valid expected classification")
    fun `valid classification`() {
        for (s in TestSections.all) {
            assertTrue(s.expectedClassification == ContentClassification.VISION
                || s.expectedClassification == ContentClassification.OPINION,
                "Section ${s.id} has invalid classification: ${s.expectedClassification}")
        }
        log.info("All sections have valid expected classification")
    }

    @Then("the vision/opinion classifier can be instantiated")
    fun `classifier instantiated`() {
        val classifier = VisionOpinionClassifier(
            baseUrl = "http://localhost:11434",
            modelName = "deepseek-v4-pro:cloud"
        )
        assertNotNull(classifier, "VisionOpinionClassifier should be instantiable")
        log.info("VisionOpinionClassifier instantiated")
    }

    @And("the classifier has the correct system prompt with classification criteria")
    fun `system prompt valid`() {
        val classifier = VisionOpinionClassifier(
            baseUrl = "http://localhost:11434",
            modelName = "deepseek-v4-pro:cloud"
        )

        val field = VisionOpinionClassifier::class.java.getDeclaredField("systemPrompt")
        field.isAccessible = true
        val prompt = field.get(classifier) as String

        assertTrue(prompt.contains("VISION"),
            "System prompt should contain VISION criteria")
        assertTrue(prompt.contains("OPINION"),
            "System prompt should contain OPINION criteria")
        assertTrue(prompt.contains("classification"),
            "System prompt should specify classification format")
        assertTrue(prompt.contains("confidence"),
            "System prompt should specify confidence format")
        log.info("System prompt validated ({} chars)", prompt.length)
    }

    @Then("the classification report JSON export produces valid format")
    fun `json export format valid`() {
        val report = ClassificationReport(
            sections = listOf(
                SectionClassification(
                    sectionId = "V1-test",
                    content = "test architecture",
                    classification = ContentClassification.VISION,
                    confidence = 0.95,
                    rationale = "contient des regles d'architecture"
                ),
                SectionClassification(
                    sectionId = "O1-test",
                    content = "je pense que",
                    classification = ContentClassification.OPINION,
                    confidence = 0.88,
                    rationale = "marqueurs subjectifs"
                )
            ),
            visionCount = 1,
            opinionCount = 1,
            averageConfidence = 0.915,
            errors = 0
        )

        val field = VisionOpinionClassifierMain::class.java.getDeclaredMethod("exportJson", ClassificationReport::class.java)
        field.isAccessible = true
        val json = field.invoke(null, report) as String

        assertNotNull(json, "JSON export should not be null")
        assertTrue(json.contains("\"totalSections\""), "JSON should contain totalSections")
        assertTrue(json.contains("\"visionCount\""), "JSON should contain visionCount")
        assertTrue(json.contains("\"opinionCount\""), "JSON should contain opinionCount")
        assertTrue(json.contains("\"errors\""), "JSON should contain errors")
        assertTrue(json.contains("\"sections\""), "JSON should contain sections")
        log.info("Classification JSON export format is valid ({} chars)", json.length)
    }

    @Then("the classification report AsciiDoc export produces valid format")
    fun `adoc export format valid`() {
        val report = ClassificationReport(
            sections = listOf(
                SectionClassification(
                    sectionId = "V1-test",
                    content = "test architecture",
                    classification = ContentClassification.VISION,
                    confidence = 0.95,
                    rationale = "contient des regles d'architecture"
                ),
                SectionClassification(
                    sectionId = "O1-test",
                    content = "je pense que",
                    classification = ContentClassification.OPINION,
                    confidence = 0.88,
                    rationale = "marqueurs subjectifs"
                )
            ),
            visionCount = 1,
            opinionCount = 1,
            averageConfidence = 0.915,
            errors = 0
        )

        val field = VisionOpinionClassifierMain::class.java.getDeclaredMethod("exportAsciiDoc", ClassificationReport::class.java)
        field.isAccessible = true
        val adoc = field.invoke(null, report) as String

        assertNotNull(adoc, "AsciiDoc export should not be null")
        assertTrue(adoc.contains("Segregation Vision/Opinion"), "AsciiDoc should contain title")
        assertTrue(adoc.contains("Synthese"), "AsciiDoc should contain synthesis section")
        assertTrue(adoc.contains("Resultats par Section"), "AsciiDoc should contain results section")
        assertTrue(adoc.contains("Decision US-9.14"), "AsciiDoc should contain decision section")
        log.info("Classification AsciiDoc export format is valid ({} chars)", adoc.length)
    }

    @Then("the US-9.14 gate requires at least {int} percent classification precision")
    fun `gate threshold`(threshold: Int) {
        assertTrue(threshold == 80, "US-9.14 gate threshold must be 80%")

        val reportFail = ClassificationReport(
            sections = (1..10).map {
                SectionClassification(
                    sectionId = "S$it",
                    content = "test",
                    classification = ContentClassification.VISION,
                    confidence = 0.5,
                    rationale = "test"
                )
            },
            visionCount = 5,
            opinionCount = 5,
            averageConfidence = 0.5,
            errors = 3
        )
        val precisionFail = if (reportFail.sections.isNotEmpty())
            (reportFail.sections.size - reportFail.errors).toDouble() / reportFail.sections.size * 100
        else 0.0
        assertTrue(precisionFail < threshold,
            "With 3 errors out of 10, precision ($precisionFail%) should be below $threshold%")

        val reportPass = ClassificationReport(
            sections = (1..10).map {
                SectionClassification(
                    sectionId = "S$it",
                    content = "test",
                    classification = ContentClassification.VISION,
                    confidence = 0.9,
                    rationale = "test"
                )
            },
            visionCount = 5,
            opinionCount = 5,
            averageConfidence = 0.9,
            errors = 0
        )
        val precisionPass = if (reportPass.sections.isNotEmpty())
            (reportPass.sections.size - reportPass.errors).toDouble() / reportPass.sections.size * 100
        else 0.0
        assertTrue(precisionPass >= threshold,
            "With 0 errors out of 10, precision ($precisionPass%) should be >= $threshold%")

        log.info("US-9.14 gate validated at {}% precision threshold", threshold)
    }
}
