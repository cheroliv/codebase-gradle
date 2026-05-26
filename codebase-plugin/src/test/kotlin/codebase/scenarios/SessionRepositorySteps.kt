package codebase.scenarios

import codebase.koog.session.SessionRepository
import codebase.koog.tracking.Dashboard
import vibecoding.contracts.state.VibecodingState
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

/**
 * Step Definitions Cucumber pour SessionRepository + Dashboard + Jardin Secret.
 *
 * PicoContainer injecte SessionRepositoryWorld (une instance par scénario).
 * Le container Testcontainers est partagé (lazy-start) via un companion object.
 * Toutes les étapes sont préfixées "vibecoding" pour éviter les conflits
 * (Cucumber interdit les doublons dans un même runner cucumberTest).
 */
class SessionRepositorySteps(private val world: SessionRepositoryWorld) {

    @Before("@epic_v_session_repo")
    fun cleanupDatabase() = runBlocking {
        // Ensure schema exists before cleanup
        world.repository.initSchema()
        // TRUNCATE cleans tables without dropping them
        val conn = Mono.from(world.connectionFactory.create()).awaitSingle()
        try {
            Mono.from(conn.createStatement("TRUNCATE TABLE vibecoding_steps, vibecoding_sessions CASCADE").execute())
                .flatMap { Mono.from(it.rowsUpdated) }.defaultIfEmpty(0L)
                .awaitSingle()
        } finally {
            Mono.from(conn.close()).subscribe()
        }
        world.reset()
    }

    private fun connectionFactory(): ConnectionFactory = world.connectionFactory

    private suspend fun execSql(sql: String) {
        val conn = Mono.from(connectionFactory().create()).awaitSingle()
        try {
            Mono.from(conn.createStatement(sql).execute())
                .flatMap { Mono.from(it.rowsUpdated) }.defaultIfEmpty(0L)
                .awaitSingle()
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    // ── Given ──

    @Given("the vibecoding session repository is initialized")
    fun `vibecoding repo initialized`() = runBlocking {
        world.reset()
        world.repository.initSchema()
    }

    // ── When ──

    @When("I create a vibecoding session with intention {string}")
    fun `create vibecoding session default`(intention: String) = runBlocking {
        val state = VibecodingState(intention = intention, workspaceRoot = "/tmp/test")
        world.lastCreatedSessionId = world.repository.createSession(state)
        world.createdSessionIds.add(world.lastCreatedSessionId!!)
        assertNotNull(world.lastCreatedSessionId)
    }

    @When("I create a vibecoding session with intention {string} and confidentiality {string}")
    fun `create vibecoding session with confidentiality`(intention: String, level: String) = runBlocking {
        val state = VibecodingState(intention = intention, workspaceRoot = "/tmp/test")
        world.lastCreatedSessionId = world.repository.createSession(state, confidentialityLevel = level)
        world.createdSessionIds.add(world.lastCreatedSessionId!!)
        assertNotNull(world.lastCreatedSessionId)
    }

    @When("I create a vibecoding session with intention {string} and confidentiality {string} with createdAt 40 days ago")
    fun `create vibecoding session old`(intention: String, level: String) = runBlocking {
        val state = VibecodingState(intention = intention, workspaceRoot = "/tmp/test")
        val id = world.repository.createSession(state, confidentialityLevel = level)
        world.createdSessionIds.add(id)

        // Override created_at to simulate an old session
        val fortyDaysAgo = Instant.now().minus(40, ChronoUnit.DAYS)
        execSql("UPDATE vibecoding_sessions SET created_at = '${fortyDaysAgo}', updated_at = '${fortyDaysAgo}' WHERE id = '$id'")
        world.lastCreatedSessionId = id
    }

    @When("I update the vibecoding session with error {string} and finished {word} and iteration {int}")
    fun `update vibecoding session`(error: String, finishedFlag: String, iterationCount: Int) = runBlocking {
        val id = world.lastCreatedSessionId ?: throw AssertionError("No session created yet")
        val state = VibecodingState(
            intention = "",
            workspaceRoot = "",
            error = error,
            finished = finishedFlag.toBooleanStrictOrNull() ?: false,
            iteration = iterationCount
        )
        world.repository.updateSession(id, state)
    }

    @When("I add a vibecoding step {string} with tool {string} and duration {int}ms")
    fun `add vibecoding step with tool`(stepType: String, toolName: String, durationMs: Int) = runBlocking {
        val id = world.lastCreatedSessionId ?: throw AssertionError("No session created yet")
        world.repository.addStep(
            sessionId = id,
            stepType = stepType,
            toolName = toolName,
            stepData = """{"tool":"$toolName"}""",
            durationMs = durationMs.toLong(),
            error = null
        )
        world.stepCount++
    }

    @When("I add a vibecoding step {string} with error {string} and duration {int}ms")
    fun `add vibecoding step with error`(stepType: String, error: String, durationMs: Int) = runBlocking {
        val id = world.lastCreatedSessionId ?: throw AssertionError("No session created yet")
        world.repository.addStep(
            sessionId = id,
            stepType = stepType,
            toolName = null,
            stepData = """{"step":"$stepType"}""",
            durationMs = durationMs.toLong(),
            error = error
        )
        world.stepCount++
    }

    @When("I delete the vibecoding session")
    fun `delete vibecoding session`() = runBlocking {
        val id = world.lastCreatedSessionId ?: throw AssertionError("No session created yet")
        world.deletionResult = world.repository.deleteSession(id)
    }

    @When("I set the vibecoding session cost to {double} for all {string} sessions")
    fun `set cost for confidentiality level`(cost: Double, level: String) = runBlocking {
        execSql("UPDATE vibecoding_sessions SET cost = $cost WHERE confidentiality_level = '$level'")
    }

    @When("the vibecoding repository is empty")
    fun `repository is empty`() = runBlocking {
        world.reset()
        world.repository.initSchema()
    }

    // ── Then ──

    @Then("the vibecoding session is created successfully")
    fun `session created successfully`() = runBlocking {
        val id = world.lastCreatedSessionId
        assertNotNull(id, "Session ID should not be null")
        assertTrue(id!!.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")),
            "ID should be UUID format, got: $id")
        val record = world.repository.getSession(id)
        assertNotNull(record, "Should retrieve created session")
        world.lastGetResult = record
    }

    @Then("the vibecoding session confidentiality level is {string}")
    fun `session confidentiality level is`(expected: String) = runBlocking {
        val record = world.lastCreatedSessionId?.let { world.repository.getSession(it) }
            ?: world.lastGetResult
        assertNotNull(record, "Session should exist")
        assertEquals(expected, record!!.confidentialityLevel)
    }

    @Then("the vibecoding session intention is {string}")
    fun `session intention is`(expected: String) = runBlocking {
        val record = world.lastCreatedSessionId?.let { world.repository.getSession(it) }
            ?: world.lastGetResult
        assertNotNull(record, "Session should exist")
        assertEquals(expected, record!!.intention)
    }

    @Then("I can list vibecoding sessions by confidentiality {string} and find exactly {int}")
    fun `list sessions by confidentiality`(level: String, expectedCount: Int) = runBlocking {
        val sessions = world.repository.listSessionsByConfidentiality(level, limit = 100)
        assertEquals(expectedCount, sessions.size,
            "Expected $expectedCount sessions with confidentiality '$level', got ${sessions.size}: ${sessions.map { "${it.id}:${it.intention}" }}")
    }

    @Then("the vibecoding session error is {string}")
    fun `session error is`(expected: String) = runBlocking {
        val record = world.repository.getSession(world.lastCreatedSessionId!!)
        assertNotNull(record)
        assertEquals(expected, record!!.error)
    }

    @Then("the vibecoding session is marked as finished")
    fun `session is finished`() = runBlocking {
        val record = world.repository.getSession(world.lastCreatedSessionId!!)
        assertNotNull(record)
        assertTrue(record!!.finished, "Session should be marked as finished")
    }

    @Then("the vibecoding session iteration count is {int}")
    fun `session iteration count is`(expected: Int) = runBlocking {
        val record = world.repository.getSession(world.lastCreatedSessionId!!)
        assertNotNull(record)
        assertEquals(expected, record!!.iterationCount)
    }

    @Then("exactly {int} vibecoding steps are linked to the session")
    fun `steps linked count`(expectedCount: Int) = runBlocking {
        val conn = Mono.from(connectionFactory().create()).awaitSingle()
        try {
            val count = Mono.from(
                conn.createStatement(
                    "SELECT count(*) FROM vibecoding_steps WHERE session_id = \$1"
                ).bind(0, world.lastCreatedSessionId).execute()
            ).flatMap { result ->
                Mono.from(result.map { row, _ -> row.get(0, Long::class.java)!! })
            }.awaitSingle()
            assertEquals(expectedCount.toLong(), count,
                "Expected $expectedCount steps, got $count")
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    @Then("the first vibecoding step has type {string} and no error")
    fun `first step type and no error`(expectedType: String) = runBlocking {
        val conn = Mono.from(connectionFactory().create()).awaitSingle()
        try {
            Mono.from(
                conn.createStatement(
                    "SELECT step_type, error FROM vibecoding_steps WHERE session_id = \$1 ORDER BY id LIMIT 1"
                ).bind(0, world.lastCreatedSessionId).execute()
            ).flatMap { result ->
                Mono.from(result.map { row, _ ->
                    Pair(row.get("step_type", String::class.java), row.get("error", String::class.java))
                })
            }.awaitSingle().let { (type, err) ->
                assertEquals(expectedType, type, "Step type mismatch")
                assertNull(err, "Step should have no error, got: $err")
            }
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    @Then("the second vibecoding step has type {string} and error {string}")
    fun `second step type and error`(expectedType: String, expectedError: String) = runBlocking {
        val conn = Mono.from(connectionFactory().create()).awaitSingle()
        try {
            Mono.from(
                conn.createStatement(
                    "SELECT step_type, error FROM vibecoding_steps WHERE session_id = \$1 ORDER BY id LIMIT 1 OFFSET 1"
                ).bind(0, world.lastCreatedSessionId).execute()
            ).flatMap { result ->
                Mono.from(result.map { row, _ ->
                    Pair(row.get("step_type", String::class.java), row.get("error", String::class.java))
                })
            }.awaitSingle().let { (type, err) ->
                assertEquals(expectedType, type, "Step type mismatch")
                assertEquals(expectedError, err, "Step error mismatch")
            }
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    @Then("the vibecoding session no longer exists")
    fun `session no longer exists`() = runBlocking {
        val record = world.repository.getSession(world.lastCreatedSessionId!!)
        assertNull(record, "Session should be deleted")
    }

    @Then("no vibecoding steps remain for the deleted session")
    fun `no steps remain after cascade`() = runBlocking {
        val conn = Mono.from(connectionFactory().create()).awaitSingle()
        try {
            val count = Mono.from(
                conn.createStatement(
                    "SELECT count(*) FROM vibecoding_steps WHERE session_id = \$1"
                ).bind(0, world.lastCreatedSessionId).execute()
            ).flatMap { result ->
                Mono.from(result.map { row, _ -> row.get(0, Long::class.java)!! })
            }.awaitSingle()
            assertEquals(0L, count, "Steps should be cascade-deleted")
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    // ── Dashboard assertions ──

    private suspend fun dashboard(): Dashboard = Dashboard(world.repository)

    @Then("the vibecoding dashboard total sessions is {int}")
    fun `dashboard total sessions`(expected: Int) = runBlocking {
        assertEquals(expected, dashboard().totalSessions())
    }

    @Then("the vibecoding dashboard cost for confidentiality {string} is {double}")
    fun `dashboard cost for confidentiality`(level: String, expected: Double) = runBlocking {
        val costs = dashboard().costByConfidentialityLevel()
        val actual = costs[level] ?: 0.0
        assertEquals(expected, actual, 0.01,
            "Cost for '$level' should be $expected, got $actual. All costs: $costs")
    }

    @Then("the vibecoding dashboard total cost is {double}")
    fun `dashboard total cost`(expected: Double) = runBlocking {
        assertEquals(expected, dashboard().totalCost(), 0.01)
    }

    @Then("the vibecoding dashboard average cost per session is {double}")
    fun `dashboard average cost`(expected: Double) = runBlocking {
        assertEquals(expected, dashboard().averageCostPerSession(), 0.01)
    }

    @Then("the vibecoding dashboard most expensive session is null")
    fun `dashboard most expensive is null`() = runBlocking {
        assertNull(dashboard().summary().mostExpensiveSession)
    }

    @Then("the vibecoding dashboard last session is null")
    fun `dashboard last session is null`() = runBlocking {
        assertNull(dashboard().summary().lastSession)
    }

    @Then("the vibecoding dashboard summary has totalSessions {int}")
    fun `dashboard summary totalSessions`(expected: Int) = runBlocking {
        world.dashboardSummary = dashboard().summary()
        assertEquals(expected, world.dashboardSummary!!.totalSessions)
    }

    @Then("the vibecoding dashboard summary has sessionsLast7Days at least {int}")
    fun `dashboard summary sessionsLast7Days`(minExpected: Int) = runBlocking {
        val summary = world.dashboardSummary ?: dashboard().summary()
        assertTrue(summary.sessionsLast7Days >= minExpected,
            "sessionsLast7Days should be at least $minExpected, got ${summary.sessionsLast7Days}")
    }

    @Then("the vibecoding dashboard summary has sessionsLast30Days at least {int}")
    fun `dashboard summary sessionsLast30Days`(minExpected: Int) = runBlocking {
        val summary = world.dashboardSummary ?: dashboard().summary()
        assertTrue(summary.sessionsLast30Days >= minExpected,
            "sessionsLast30Days should be at least $minExpected, got ${summary.sessionsLast30Days}")
    }

    @Then("the vibecoding dashboard summary has a non-null lastSession")
    fun `dashboard summary lastSession non null`() = runBlocking {
        val summary = world.dashboardSummary ?: dashboard().summary()
        assertNotNull(summary.lastSession, "lastSession should not be null")
    }
}
