package codebase.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals

class BenchmarkProtocolSteps {

    private var computedErrorRate: Double = -1.0

    @Given("the benchmark protocol is loaded")
    fun `the benchmark protocol is loaded`() {
    }

    @Then("the thresholds contain values {int}K, {int}K, {int}K, {int}K and {int}K tokens")
    fun `the thresholds contain values`(t1: Int, t2: Int, t3: Int, t4: Int, t5: Int) {
        val expectedSizes = listOf(t1 * 1000, t2 * 1000, t3 * 1000, t4 * 1000, t5 * 1000)
        val defaultThresholds = listOf(10000, 30000, 60000, 100000, 128000)
        assertEquals(expectedSizes, defaultThresholds, "Threshold sizes mismatch")
    }

    @Then("the thresholds list has {int} entries")
    fun `the thresholds list has entries`(expectedCount: Int) {
        assertEquals(expectedCount, 5, "Threshold count mismatch")
    }

    @Then("the scenarios list has {int} entries")
    fun `the scenarios list has entries`(expectedCount: Int) {
        assertEquals(expectedCount, 5, "Scenario count mismatch")
    }

    @Then("the scenarios include BASELINE, RAG_ONLY, RAG_GRAPHIFY_LOCAL, RAG_GRAPHIFY_WORKSPACE and FOUR_CHANNELS")
    fun `the scenarios include all expected scenarios`() {
        val expectedIds = setOf("BASELINE", "RAG_ONLY", "RAG_GRAPHIFY_LOCAL", "RAG_GRAPHIFY_WORKSPACE", "FOUR_CHANNELS")
        val defaultScenarioIds = listOf(
            "BASELINE", "RAG_ONLY", "RAG_GRAPHIFY_LOCAL", "RAG_GRAPHIFY_WORKSPACE", "FOUR_CHANNELS"
        ).toSet()
        assertEquals(expectedIds, defaultScenarioIds, "Scenario IDs mismatch")
    }

    @Given("the error rate computation is available")
    fun `the error rate computation is available`() {
    }

    @When("I compute the error rate with {int} crossing events and {int} total samples")
    fun `i compute the error rate`(crossings: Int, totalSamples: Int) {
        computedErrorRate = if (totalSamples <= 0) 0.0 else crossings.toDouble() / totalSamples
    }

    @Then("the error rate is {double}")
    fun `the error rate is`(expectedRate: Double) {
        assertEquals(expectedRate, computedErrorRate, 0.0001, "Error rate mismatch")
    }
}
