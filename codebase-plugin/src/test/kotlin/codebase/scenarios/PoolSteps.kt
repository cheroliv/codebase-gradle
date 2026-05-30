package codebase.scenarios

import contracts.llmpool.RotationStrategy
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step Definitions Cucumber pour le pool Ollama GPT-OSS-120B.
 *
 * Pattern PicoContainer standardisé — aligné sur VibecodingSteps / AugmentedPlanningSteps.
 * Toutes les étapes sont préfixées "pool" pour éviter les conflits de noms
 * avec les autres step classes.
 */
class PoolSteps(private val world: PoolWorld) {

    // ── Given ──

    @Given("the Ollama pool is initialized with default instances")
    fun `pool initialized with default instances`() {
        world.setupPool(count = 3, limit = 10, threshold = 50)
        assertNotNull(world.pool, "Pool should be initialized")
    }

    @Given("the Ollama pool is initialized with full {int}-port configuration")
    fun `pool initialized with full port configuration`(portCount: Int) {
        world.setupFullPool()
        assertNotNull(world.pool, "Full pool should be initialized")
    }

    @Given("a pool with {int} instances and threshold {int}%")
    fun `pool with N instances and threshold`(count: Int, threshold: Int) {
        world.setupPool(count = count, limit = 10, threshold = threshold)
        assertNotNull(world.pool, "Pool should be initialized")
    }

    @Given("a pool with {int} instance at {int}% quota")
    fun `pool with single instance at quota`(count: Int, percent: Int) {
        // Crée 1 instance avec un seuil bas qui sera immédiatement dépassé
        // threshold=1% de limit=10 = seuil 0 → dès le 1er appel le quota est "dépassé"
        world.setupPool(count = count, limit = 10, threshold = 1)
        // Un appel pour saturer
        world.callPool(1)
    }

    // ── When ──

    @When("{int} consecutive LLM calls are made via the pool")
    fun `consecutive LLM calls made`(times: Int) {
        world.callPool(times)
    }

    @When("{int} consecutive LLM calls are made")
    fun `consecutive LLM calls made simple`(times: Int) {
        world.callPool(times)
    }

    @When("a new LLM call is made")
    fun `single LLM call made`() {
        world.callPool(1)
    }

    @When("the pool usage is reset")
    fun `pool usage reset`() {
        world.resetPool()
    }

    // ── Then ──

    @Then("each pool call cycles through all {int} ports in round-robin order")
    fun `each call cycles through all ports round robin`(portCount: Int) {
        val results = world.callResults
        assertTrue(results.size >= 30, "Need 30 calls to verify full cycle, got ${results.size}")

        // Vérifie que les 29 premiers appels utilisent des instances différentes (ports 11437-11465)
        val first29Ports = results.take(29).map { it.removePrefix("ollama-").toInt() }
        for (port in 11437..11465) {
            assertTrue(port in first29Ports,
                "Port $port should appear in the first 29 calls, got ports: $first29Ports")
        }

        // Vérifie l'ordre ROUND_ROBIN : port 11437, 11438, ..., 11465
        val expectedPorts = (11437..11465).toList()
        assertEquals(expectedPorts, first29Ports,
            "First 29 calls should cycle through ports 11437-11465 in order")
    }

    @Then("the {int}th call wraps back to port {int}")
    fun `call wraps back to port`(callNumber: Int, port: Int) {
        val idx = callNumber - 1  // 0-indexed
        assertTrue(idx < world.callResults.size,
            "Call number $callNumber exceeds results size ${world.callResults.size}")
        val resultPort = world.callResults[idx]
            .removePrefix("ollama-")
            .toIntOrNull()
        assertEquals(port, resultPort,
            "Call #$callNumber should wrap to port $port, got port $resultPort")
    }

    @Then("instance {string} has quota exceeded")
    fun `instance has quota exceeded`(instanceId: String) {
        val pool = world.pool ?: error("Pool not initialized")
        val instance = world.poolInstances.first { it.id == instanceId }
        assertTrue(pool.isQuotaExceeded(instance),
            "Instance '$instanceId' should have quota exceeded")
    }

    @Then("instance {string} was used at least once")
    fun `instance was used at least once`(instanceId: String) {
        assertTrue(world.callResults.any { it == instanceId },
            "Instance '$instanceId' should have been used at least once, got calls: ${world.callResults}")
    }

    @Then("the pool returns the instance despite quota exceeded")
    fun `pool returns instance despite quota exceeded`() {
        val pool = world.pool ?: error("Pool not initialized")
        assertEquals(1, world.callResults.size,
            "Should have made exactly 1 call despite quota exceeded")
        val returnedInstance = world.poolInstances.first { it.id == world.callResults[0] }
        assertTrue(pool.isQuotaExceeded(returnedInstance),
            "Returned instance should have quota exceeded (best-effort fallback)")
    }

    @Then("no exception is thrown")
    fun `no exception thrown`() {
        // Si on arrive ici sans exception, c'est que nextInstance() n'a pas throw
        assertTrue(true, "No exception was thrown — pool handled saturated state gracefully")
    }

    @Then("all instances have quota NOT exceeded")
    fun `all instances quota not exceeded`() {
        val pool = world.pool ?: error("Pool not initialized")
        for (instance in world.poolInstances) {
            assertFalse(pool.isQuotaExceeded(instance),
                "Instance '${instance.id}' should NOT have quota exceeded after reset")
        }
    }

    @Then("rotation starts again from the first instance")
    fun `rotation starts from first instance`() {
        // Après reset, le prochain appel doit retourner la première instance
        world.callPool(1)
        assertEquals(world.poolInstances.first().id, world.callResults[0],
            "After reset, first call should return first instance '${world.poolInstances.first().id}'")
    }

    @Then("the pool has exactly {int} instances")
    fun `pool has exactly N instances`(expected: Int) {
        val pool = world.pool ?: error("Pool not initialized")
        assertEquals(expected, pool.size(), "Pool should have $expected instances")
    }

    @Then("the pool covers all ports from {int} to {int}")
    fun `pool covers port range`(from: Int, to: Int) {
        val ports = world.poolInstances
            .map { it.baseUrl.removePrefix("http://localhost:").toInt() }
            .toSet()
        for (port in from..to) {
            assertTrue(port in ports, "Port $port missing from pool")
        }
    }

    @Then("the pool uses exactly {int} distinct authorized models")
    fun `pool uses N distinct models`(expected: Int) {
        val models = world.poolInstances.map { it.model }.toSet()
        assertEquals(expected, models.size,
            "Pool should have $expected distinct models, got ${models.size}: $models")
        for (authorized in PoolWorld.AUTHORIZED_MODELS) {
            assertTrue(authorized in models,
                "Authorized model '$authorized' missing from pool")
        }
    }
}
