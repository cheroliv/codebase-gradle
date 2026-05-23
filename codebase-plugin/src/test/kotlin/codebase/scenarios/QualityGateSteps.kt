package codebase.scenarios

import codebase.quality.*
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QualityGateSteps {

    private lateinit var gate: QualityGate
    private var lastAssessment: QualityAssessment? = null
    private var lastFeedback: String = ""
    private var lastOutput: String = ""
    private var lastDomain: Domain = Domain.CDA

    @Given("a QualityGate is instantiated with default deterministic checkers")
    fun `instantiate quality gate`() {
        gate = QualityGate(
            sentimentAnalyzer = DeterministicSentimentAnalyzer(),
            offTopicDetector = DeterministicOffTopicDetector(),
            piiDetector = DeterministicPiiResidualDetector(),
            config = QualityGateConfig()
        )
    }

    @When("I evaluate CDA output {string}")
    fun `evaluate CDA output`(output: String) {
        lastOutput = output
        lastDomain = Domain.CDA
        lastAssessment = gate.evaluate(output, Domain.CDA)
        lastFeedback = gate.buildFeedback(lastAssessment!!)
    }

    @When("I evaluate FPA output {string}")
    fun `evaluate FPA output`(output: String) {
        lastOutput = output
        lastDomain = Domain.FPA
        lastAssessment = gate.evaluate(output, Domain.FPA)
        lastFeedback = gate.buildFeedback(lastAssessment!!)
    }

    @Then("the quality gate passes")
    fun `quality gate passes`() {
        assertNotNull(lastAssessment)
        assertEquals(QualityVerdict.PASS, lastAssessment!!.overallVerdict)
        assertTrue(lastAssessment!!.passed)
    }

    @Then("all 3 checkers return PASS verdicts")
    fun `all checkers pass`() {
        assertNotNull(lastAssessment)
        assertEquals(3, lastAssessment!!.results.size)
        assertTrue(lastAssessment!!.results.all { it.verdict == QualityVerdict.PASS })
    }

    @Then("the quality gate fails with verdict {string}")
    fun `quality gate fails with verdict`(verdictName: String) {
        assertNotNull(lastAssessment)
        val expected = QualityVerdict.valueOf(verdictName)
        assertEquals(expected, lastAssessment!!.overallVerdict)
        assertTrue(!lastAssessment!!.passed)
    }

    @Then("the failing checks contain {string}")
    fun `failing checks contain`(checkerName: String) {
        assertNotNull(lastAssessment)
        val failing = lastAssessment!!.failingChecks
        assertTrue(failing.any { it.checkerName == checkerName },
            "Expected failing check '$checkerName' but got: ${failing.map { it.checkerName }}")
    }

    @Then("the assessment contains at least {int} failing checks")
    fun `assessment contains at least N failing checks`(count: Int) {
        assertNotNull(lastAssessment)
        val failing = lastAssessment!!.failingChecks
        assertTrue(failing.size >= count,
            "Expected at least $count failing checks but got ${failing.size}")
    }

    @Then("the quality gate fails")
    fun `quality gate fails`() {
        assertNotNull(lastAssessment)
        assertTrue(!lastAssessment!!.passed)
    }

    @Then("the feedback message contains {string}")
    fun `feedback message contains`(substring: String) {
        assertTrue(lastFeedback.contains(substring),
            "Expected feedback to contain '$substring' but got: $lastFeedback")
    }

    @Then("the quality gate verdict is not {string}")
    fun `quality gate verdict is not`(verdictName: String) {
        assertNotNull(lastAssessment)
        val excluded = QualityVerdict.valueOf(verdictName)
        assertTrue(lastAssessment!!.overallVerdict != excluded,
            "Expected verdict NOT $excluded but got ${lastAssessment!!.overallVerdict}")
    }

    @Then("all scores are {double}")
    fun `all scores are`(score: Double) {
        assertNotNull(lastAssessment)
        assertTrue(lastAssessment!!.results.all { it.score == score },
            "Expected all scores to be $score but got: ${lastAssessment!!.results.map { it.score }}")
    }
}
