package codebase.scenarios

import codebase.rag.AnonymizationExpertFactory
import codebase.rag.AnonymizationRequest
import codebase.rag.AnonymizationResult
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnonymizationExpertSteps {

    private lateinit var currentResult: AnonymizationResult
    private var originalContent: String = ""

    @Given("the anonymization expert is initialized")
    fun `the anonymization expert is initialized`() {
    }

    @When("I anonymize the file {string}")
    fun `i anonymize the file`(fileName: String) {
        val file = File("src/test/resources/datasets/$fileName")
        assertTrue(file.exists(), "File $fileName not found")
        originalContent = file.readText()

        val extension = file.extension.lowercase()
        val format = when (extension) {
            "yml", "yaml" -> "yaml"
            "json" -> "json"
            else -> extension
        }

        val expert = AnonymizationExpertFactory.create()
        currentResult = expert.anonymizeRequest(
            AnonymizationRequest(sourcePath = file.absolutePath, content = originalContent, targetFormat = format)
        )
    }

    @Then("the anonymized content does not contain the string {string}")
    fun `the anonymized content does not contain the string`(forbidden: String) {
        assertNotNull(currentResult)
        assertFalse(forbidden in currentResult.anonymizedContent, "Found '$forbidden' in anonymized content")
    }

    @Then("the anonymized content does not contain the symbol {string}")
    fun `the anonymized content does not contain the symbol`(symbol: String) {
        assertNotNull(currentResult)
        assertFalse(symbol in currentResult.anonymizedContent, "Found '$symbol' in anonymized content")
    }

    @Then("the anonymization detects at least {int} PII category")
    fun `the anonymization detects at least`(minCategories: Int) {
        assertNotNull(currentResult)
        assertTrue(currentResult.detectedPiiCategories.size >= minCategories,
            "Expected >= $minCategories categories, got: ${currentResult.detectedPiiCategories.size}")
    }

    @Then("the anonymization detects the category {string}")
    fun `the anonymization detects the category`(expectedCategory: String) {
        assertNotNull(currentResult)
        assertTrue(expectedCategory in currentResult.detectedPiiCategories,
            "Category '$expectedCategory' not detected. Detected: ${currentResult.detectedPiiCategories}")
    }

    @Then("the confidence score is above {double}")
    fun `the confidence score is above`(threshold: Double) {
        assertNotNull(currentResult)
        assertTrue(currentResult.confidenceScore > threshold,
            "Confidence ${currentResult.confidenceScore} <= $threshold")
    }

    @Then("the confidence score is {double}")
    fun `the confidence score is`(expectedConfidence: Double) {
        assertNotNull(currentResult)
        assertEquals(expectedConfidence, currentResult.confidenceScore, 0.01,
            "Expected confidence: $expectedConfidence, actual: ${currentResult.confidenceScore}")
    }

    @Then("the result contains a non-empty summary")
    fun `the result contains a non empty summary`() {
        assertNotNull(currentResult)
        assertTrue(currentResult.summary.isNotBlank(), "Summary is empty")
    }

    @Then("the anonymized content is identical to the original content")
    fun `the anonymized content is identical to the original content`() {
        assertNotNull(currentResult)
        assertEquals(originalContent.trim(), currentResult.anonymizedContent.trim(),
            "Content was modified but no PII should have been detected")
    }

    @Then("the number of replacements is {int}")
    fun `the number of replacements is`(expectedCount: Int) {
        assertNotNull(currentResult)
        assertEquals(expectedCount, currentResult.replacedCount,
            "Expected replacements: $expectedCount, actual: ${currentResult.replacedCount}")
    }
}
