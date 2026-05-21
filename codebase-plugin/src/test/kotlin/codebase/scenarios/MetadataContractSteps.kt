package codebase.scenarios

import codebase.rag.Metadata
import codebase.rag.MetadataValidator
import codebase.rag.PlanMetadata
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.io.File
import kotlin.test.*

class MetadataContractSteps {

    private lateinit var tempDir: File
    private var validationResult: MetadataValidator.ValidationResult? = null
    private var lastCompatibilityResult: Boolean? = null

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "epic-k-test-${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @Given("a temporary directory for test artefacts")
    fun `a temporary directory for test artefacts`() {
        assertTrue(tempDir.isDirectory, "Temp dir should exist")
    }

    // ── Given a file with content ──

    @Given("a file {string} with content:")
    fun `a file with content`(fileName: String, content: String) {
        val file = File(tempDir, fileName)
        file.writeText(content.trimIndent())
        assertTrue(file.isFile, "File $fileName should exist")
    }

    @Given("no file {string} exists")
    fun `no file exists`(fileName: String) {
        val file = File(tempDir, fileName)
        if (file.exists()) file.delete()
        assertFalse(file.isFile, "File $fileName should not exist")
    }

    // ── When I validate ──

    @When("I validate {string} expecting type {string} and version {string}")
    fun `validate with type and version`(fileName: String, expectedType: String, expectedVersion: String) {
        val file = File(tempDir, fileName)
        validationResult = MetadataValidator.validate(file, expectedVersion = expectedVersion, expectedType = expectedType)
        assertNotNull(validationResult, "Validation result should not be null")
    }

    @When("I validate {string} expecting type {string}")
    fun `validate with type only`(fileName: String, expectedType: String) {
        val file = File(tempDir, fileName)
        validationResult = MetadataValidator.validate(file, expectedType = expectedType)
        assertNotNull(validationResult, "Validation result should not be null")
    }

    @When("I validate {string} expecting version {string}")
    fun `validate with version only`(fileName: String, expectedVersion: String) {
        val file = File(tempDir, fileName)
        validationResult = MetadataValidator.validate(file, expectedVersion = expectedVersion)
        assertNotNull(validationResult, "Validation result should not be null")
    }

    @When("I validate {string}")
    fun `validate without expectations`(fileName: String) {
        val file = File(tempDir, fileName)
        validationResult = MetadataValidator.validate(file)
        assertNotNull(validationResult, "Validation result should not be null")
    }

    // ── Then validation result ──

    @Then("the validation result is VALID")
    fun `validation result is valid`() {
        assertIs<MetadataValidator.ValidationResult.Valid>(validationResult)
    }

    @Then("the validation result is INVALID")
    fun `validation result is invalid`() {
        assertIs<MetadataValidator.ValidationResult.Invalid>(validationResult)
    }

    @And("the reason contains {string}")
    fun `reason contains`(expected: String) {
        val invalid = validationResult as? MetadataValidator.ValidationResult.Invalid
            ?: throw AssertionError("Expected INVALID but got $validationResult")
        assertTrue(invalid.reason.contains(expected), "Reason '${invalid.reason}' should contain '$expected'")
    }

    // ── Then parsed metadata fields ──

    @And("the parsed metadata type is {string}")
    fun `parsed type is`(expectedType: String) {
        val valid = validationResult as? MetadataValidator.ValidationResult.Valid
            ?: throw AssertionError("Expected VALID but got $validationResult")
        assertEquals(expectedType, valid.metadata.type)
    }

    @And("the parsed metadata source is {string}")
    fun `parsed source is`(expectedSource: String) {
        val valid = validationResult as? MetadataValidator.ValidationResult.Valid
            ?: throw AssertionError("Expected VALID but got $validationResult")
        assertEquals(expectedSource, valid.metadata.source)
    }

    @And("the parsed metadata classification is {string}")
    fun `parsed classification is`(expectedClass: String) {
        val valid = validationResult as? MetadataValidator.ValidationResult.Valid
            ?: throw AssertionError("Expected VALID but got $validationResult")
        val plan = valid.metadata as? PlanMetadata
            ?: throw AssertionError("Expected PlanMetadata but got ${valid.metadata::class.simpleName}")
        assertEquals(expectedClass, plan.classification)
    }

    @And("the parsed metadata epics count is {int}")
    fun `parsed epics count`(expectedEpics: Int) {
        val valid = validationResult as? MetadataValidator.ValidationResult.Valid
            ?: throw AssertionError("Expected VALID but got $validationResult")
        val plan = valid.metadata as? PlanMetadata
            ?: throw AssertionError("Expected PlanMetadata but got ${valid.metadata::class.simpleName}")
        assertEquals(expectedEpics, plan.epics)
    }

    // ── Then isVersionCompatible ──

    @Then("isVersionCompatible between {string} and {string} is TRUE")
    fun `isVersionCompatible is true`(provided: String, expected: String) {
        val result = MetadataValidator.isVersionCompatible(provided, expected)
        lastCompatibilityResult = result
        assertTrue(result, "isVersionCompatible($provided, $expected) should be TRUE")
    }

    @Then("isVersionCompatible between {string} and {string} is FALSE")
    fun `isVersionCompatible is false`(provided: String, expected: String) {
        val result = MetadataValidator.isVersionCompatible(provided, expected)
        lastCompatibilityResult = result
        assertFalse(result, "isVersionCompatible($provided, $expected) should be FALSE")
    }
}
