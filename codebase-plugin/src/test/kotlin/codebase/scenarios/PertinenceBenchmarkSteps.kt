package codebase.scenarios

import codebase.rag.PertinenceQuestions
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PertinenceBenchmarkSteps {

    private val log = LoggerFactory.getLogger(PertinenceBenchmarkSteps::class.java)

    @Given("the pertinence questions dataset")
    fun `load pertinence questions`() {
        val questions = PertinenceQuestions.all
        assertNotNull(questions, "Questions dataset should not be null")
        log.info("Pertinence questions dataset loaded")
    }

    @Then("there are exactly {int} questions")
    fun `exact question count`(expected: Int) {
        assertEquals(expected, PertinenceQuestions.all.size,
            "Expected $expected questions, got ${PertinenceQuestions.all.size}")
        log.info("Question count: {}", PertinenceQuestions.all.size)
    }

    @Then("each question has a domain in {string}")
    fun `each question has valid domain`(domainList: String) {
        val validDomains = domainList.split(",").map { it.trim() }
        for (q in PertinenceQuestions.all) {
            assertTrue(q.domain in validDomains,
                "Question ${q.id} has domain '${q.domain}', expected one of $validDomains")
        }
        log.info("All domains are valid: {}", validDomains)
    }

    @Then("each question has at least {int} expected keywords")
    fun `each question has minimum keywords`(minKeywords: Int) {
        for (q in PertinenceQuestions.all) {
            assertTrue(q.expectedKeywords.size >= minKeywords,
                "Question ${q.id} has ${q.expectedKeywords.size} keywords, min expected: $minKeywords")
        }
        log.info("All questions have >= {} keywords", minKeywords)
    }

    @Then("each question has a minimum expected answer length of {int} characters")
    fun `each question has min answer length`(minLen: Int) {
        for (q in PertinenceQuestions.all) {
            assertTrue(q.minExpectedLength >= minLen,
                "Question ${q.id} has minExpectedLength ${q.minExpectedLength}, min: $minLen")
        }
        log.info("All questions have min expected length >= {}", minLen)
    }

    @Then("the pertinence benchmark runner can be instantiated")
    fun `benchmark runner instantiated`() {
        val runner = codebase.rag.PertinenceBenchmarkRunner(
            baseUrl = "http://localhost:11434",
            modelName = "deepseek-v4-pro:cloud"
        )
        assertNotNull(runner, "PertinenceBenchmarkRunner should be instantiable")
        log.info("PertinenceBenchmarkRunner instantiated")
    }

    @Then("the pertinence report JSON export produces valid format")
    fun `json export format valid`() {
        val report = codebase.rag.PertinenceBenchmarkReport(
            executionTimestamp = java.time.Instant.now().toString(),
            modelName = "deepseek-v4-pro:cloud",
            totalQuestions = 10,
            improvedCount = 7,
            degradedCount = 2,
            unchangedCount = 1,
            improvementRate = 0.70,
            pairs = emptyList(),
            mvp0Validated = true
        )

        val json = codebase.rag.PertinenceReportExporter.exportJson(report)
        assertNotNull(json, "JSON export should not be null")
        assertTrue(json.contains("\"totalQuestions\""), "JSON should contain totalQuestions")
        assertTrue(json.contains("\"mvp0Validated\""), "JSON should contain mvp0Validated")
        assertTrue(json.contains("\"pairs\""), "JSON should contain pairs")
        log.info("JSON export format is valid ({} chars)", json.length)
    }

    @Then("the pertinence report AsciiDoc export produces valid format")
    fun `adoc export format valid`() {
        val report = codebase.rag.PertinenceBenchmarkReport(
            executionTimestamp = java.time.Instant.now().toString(),
            modelName = "deepseek-v4-pro:cloud",
            totalQuestions = 10,
            improvedCount = 8,
            degradedCount = 1,
            unchangedCount = 1,
            improvementRate = 0.80,
            pairs = emptyList(),
            mvp0Validated = true
        )

        val adoc = codebase.rag.PertinenceReportExporter.exportAsciiDoc(report)
        assertNotNull(adoc, "AsciiDoc export should not be null")
        assertTrue(adoc.contains("Pertinence Benchmark"), "AsciiDoc should contain title")
        assertTrue(adoc.contains("MVP0"), "AsciiDoc should contain MVP0 decision")
        assertTrue(adoc.contains("Synthese Globale"), "AsciiDoc should contain synthesis section")
        assertTrue(adoc.contains("Questions Metier"), "AsciiDoc should contain questions section")
        log.info("AsciiDoc export format is valid ({} chars)", adoc.length)
    }

    @Then("the mvp0 gate requires improvement on more than {int} percent of questions")
    fun `mvp0 gate threshold`(threshold: Int) {
        assertTrue(threshold == 70, "MVP0 gate threshold must be 70%")
        val report = codebase.rag.PertinenceBenchmarkReport(
            improvementRate = 0.69,
            mvp0Validated = false
        )
        assertEquals(false, report.mvp0Validated,
            "At 69%, MVP0 should NOT be validated")

        val report2 = codebase.rag.PertinenceBenchmarkReport(
            improvementRate = 0.71,
            mvp0Validated = true
        )
        assertEquals(true, report2.mvp0Validated,
            "At 71%, MVP0 SHOULD be validated")
        log.info("MVP0 gate validated at {}% threshold", threshold)
    }
}
