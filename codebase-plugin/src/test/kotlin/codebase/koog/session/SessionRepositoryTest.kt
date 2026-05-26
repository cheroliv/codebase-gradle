package codebase.koog.session

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import reactor.core.publisher.Mono
import vibecoding.contracts.state.VibecodingState

class SessionRepositoryTest {

    companion object {
        private val container: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg17")
            .apply {
                withDatabaseName("codebase_vibecoding_test")
                withUsername("codebase")
                withPassword("codebase")
                withReuse(false)
            }

        lateinit var connectionFactory: ConnectionFactory
        lateinit var repository: SessionRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            container.start()
            val config = PostgresqlConnectionConfiguration.builder()
                .host(container.host)
                .port(container.getMappedPort(5432))
                .database(container.databaseName)
                .username(container.username)
                .password(container.password)
                .build()
            connectionFactory = PostgresqlConnectionFactory(config)
            repository = SessionRepository(connectionFactory)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            container.stop()
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        runBlocking {
            val conn = Mono.from(connectionFactory.create()).awaitSingle()
            try {
                Mono.from(conn.createStatement("DROP TABLE IF EXISTS vibecoding_steps").execute()).awaitSingle()
                Mono.from(conn.createStatement("DROP TABLE IF EXISTS vibecoding_sessions").execute()).awaitSingle()
                Mono.from(conn.createStatement("DROP TABLE IF EXISTS schema_version").execute()).awaitSingle()
            } finally {
                Mono.from(conn.close()).subscribe()
            }
        }
    }

    private suspend fun Connection.queryForStrings(sql: String): List<String> {
        val result = mutableListOf<String>()
        Mono.from(createStatement(sql).execute())
            .flatMapMany { r -> r.map { row, _ -> row.get(0, String::class.java)!! } }
            .collectList()
            .awaitSingle()
            .also { result.addAll(it) }
        return result
    }

    private suspend fun Connection.queryForLong(sql: String): Long {
        return Mono.from(createStatement(sql).execute())
            .flatMap { result -> Mono.from(result.map { row, _ -> row.get(0, Long::class.java)!! }) }
            .awaitSingle()
    }

    @Test
    fun `initSchema creates tables without error`() = runBlocking {
        repository.initSchema()

        var tables = emptyList<String>()
        val conn = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            tables = conn.queryForStrings(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'vibecoding_%' ORDER BY table_name"
            )
        } finally {
            Mono.from(conn.close()).subscribe()
        }

        assertTrue(tables.contains("vibecoding_sessions"), "Expected vibecoding_sessions table")
        assertTrue(tables.contains("vibecoding_steps"), "Expected vibecoding_steps table")
    }

    @Test
    fun `initSchema is idempotent`() = runBlocking {
        repository.initSchema()
        repository.initSchema()

        var count = 0L
        val conn = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            count = conn.queryForLong("SELECT count(*) FROM vibecoding_sessions")
        } finally {
            Mono.from(conn.close()).subscribe()
        }

        assertEquals(0L, count, "Schema should be idempotent (no rows created)")
    }

    @Test
    fun `createSession inserts and returns UUID id`() = runBlocking {
        repository.initSchema()

        val state = VibecodingState(
            intention = "Add dark mode toggle",
            workspaceRoot = "/tmp/test",
            dryRun = true,
            maxActions = 5
        )

        val id = repository.createSession(state)

        assertNotNull(id)
        assertTrue(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")),
            "Expected UUID format, got: $id")

        val record = repository.getSession(id)
        assertNotNull(record, "Should retrieve created session")
        assertEquals("Add dark mode toggle", record!!.intention)
        assertEquals("/tmp/test", record.workspaceRoot)
        assertTrue(record.dryRun)
        assertEquals(5, record.maxActions)
        assertEquals("INTERNAL", record.confidentialityLevel)
        assertEquals(0L, record.promptTokens)
        assertEquals(0L, record.completionTokens)
        assertEquals(0.0, record.cost)
        assertNull(record.error)
        assertFalse(record.finished)
        assertEquals(0, record.iterationCount)
    }

    @Test
    fun `createSession with explicit confidentialityLevel`() = runBlocking {
        repository.initSchema()

        val state = VibecodingState(
            intention = "Secret refactoring plan",
            workspaceRoot = "/tmp/secret"
        )

        val id = repository.createSession(state, confidentialityLevel = "CONFIDENTIAL")

        val record = repository.getSession(id)
        assertNotNull(record)
        assertEquals("CONFIDENTIAL", record!!.confidentialityLevel)
    }

    @Test
    fun `createSession with all confidentiality levels`() = runBlocking {
        repository.initSchema()

        val levels = listOf("PUBLIC", "INTERNAL", "CONFIDENTIAL", "SECRET")
        val baseState = VibecodingState(intention = "test", workspaceRoot = "/tmp")

        for (level in levels) {
            val id = repository.createSession(baseState, confidentialityLevel = level)
            val record = repository.getSession(id)
            assertNotNull(record)
            assertEquals(level, record!!.confidentialityLevel,
                "Confidentiality level should be $level")
        }
    }

    @Test
    fun `updateSession updates error and finished from state`() = runBlocking {
        repository.initSchema()

        val initialState = VibecodingState(
            intention = "Fix typo in README",
            workspaceRoot = "/tmp/test"
        )
        val id = repository.createSession(initialState)

        val updatedState = initialState.copy(
            error = "Something went wrong",
            finished = true,
            iteration = 3
        )

        val result = repository.updateSession(id, updatedState)
        assertTrue(result, "updateSession should return true")

        val record = repository.getSession(id)
        assertNotNull(record)
        assertEquals("Something went wrong", record!!.error)
        assertTrue(record.finished)
        assertEquals(3, record.iterationCount)
    }

    @Test
    fun `addStep inserts step records linked to session`() = runBlocking {
        repository.initSchema()

        val state = VibecodingState(
            intention = "Refactor DAG",
            workspaceRoot = "/tmp/test"
        )
        val sessionId = repository.createSession(state)

        val step1Id = repository.addStep(
            sessionId = sessionId,
            stepType = "exec_gradle",
            toolName = "exec_shell",
            stepData = """{"task":"build"}""",
            durationMs = 1500,
            error = null
        )
        assertTrue(step1Id > 0, "Step id should be positive, got $step1Id")

        val step2Id = repository.addStep(
            sessionId = sessionId,
            stepType = "llm_call",
            toolName = null,
            stepData = """{"prompt":"Fix code"}""",
            durationMs = 3200,
            error = "Rate limit exceeded"
        )
        assertTrue(step2Id > 0, "Step id should be positive, got $step2Id")
        assertTrue(step2Id > step1Id, "Step ids should be sequential")

        val steps = mutableListOf<Pair<String, String?>>()
        val conn = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            Mono.from(
                conn.createStatement(
                    "SELECT step_type, error FROM vibecoding_steps WHERE session_id = \$1 ORDER BY id"
                ).bind(0, sessionId).execute()
            ).flatMapMany { result ->
                result.map { row, _ ->
                    row.get("step_type", String::class.java)!! to
                    row.get("error", String::class.java)
                }
            }.collectList().awaitSingle().also { steps.addAll(it) }
        } finally {
            Mono.from(conn.close()).subscribe()
        }

        assertEquals(2, steps.size)
        assertEquals("exec_gradle", steps[0].first)
        assertNull(steps[0].second)
        assertEquals("llm_call", steps[1].first)
        assertEquals("Rate limit exceeded", steps[1].second)
    }

    @Test
    fun `getSession retrieves full record`() = runBlocking {
        repository.initSchema()

        val state = VibecodingState(
            intention = "Full pipeline test",
            workspaceRoot = "/home/user/project",
            dryRun = false,
            maxActions = 20,
            classification = "multi-step"
        )
        val id = repository.createSession(state, confidentialityLevel = "PUBLIC")

        val record = repository.getSession(id)
        assertNotNull(record)
        val r = record!!

        assertEquals(id, r.id)
        assertNull(r.parentSessionId)
        assertEquals("/home/user/project", r.workspaceRoot)
        assertEquals("Full pipeline test", r.intention)
        assertFalse(r.dryRun)
        assertEquals(20, r.maxActions)
        assertEquals("multi-step", r.classification)
        assertEquals(0L, r.promptTokens)
        assertEquals(0L, r.completionTokens)
        assertEquals(0.0, r.cost)
        assertNull(r.error)
        assertFalse(r.finished)
        assertEquals(0, r.iterationCount)
        assertEquals("PUBLIC", r.confidentialityLevel)
        assertNotNull(r.createdAt)
        assertNotNull(r.updatedAt)
    }

    @Test
    fun `getSession returns null for non-existent id`() = runBlocking {
        repository.initSchema()

        val record = repository.getSession("00000000-0000-0000-0000-000000000000")
        assertNull(record)
    }

    @Test
    fun `listSessions returns sessions ordered by created_at DESC`() = runBlocking {
        repository.initSchema()

        val baseState = VibecodingState(intention = "session", workspaceRoot = "/tmp")

        for (i in 1..3) {
            repository.createSession(baseState.copy(intention = "Session $i"))
            Thread.sleep(50)
        }

        val sessions = repository.listSessions(limit = 10)
        assertEquals(3, sessions.size)
        assertEquals("Session 3", sessions[0].intention)
        assertEquals("Session 2", sessions[1].intention)
        assertEquals("Session 1", sessions[2].intention)
    }

    @Test
    fun `listSessions respects limit`() = runBlocking {
        repository.initSchema()

        val baseState = VibecodingState(intention = "test", workspaceRoot = "/tmp")
        for (i in 1..10) {
            repository.createSession(baseState.copy(intention = "Session $i"))
            Thread.sleep(10)
        }

        val sessions = repository.listSessions(limit = 5)
        assertEquals(5, sessions.size)
    }

    @Test
    fun `listSessionsByConfidentiality filters correctly`() = runBlocking {
        repository.initSchema()

        val baseState = VibecodingState(intention = "test", workspaceRoot = "/tmp")
        repository.createSession(baseState.copy(intention = "Public wiki"), confidentialityLevel = "PUBLIC")
        Thread.sleep(10)
        repository.createSession(baseState.copy(intention = "Internal doc"), confidentialityLevel = "INTERNAL")
        Thread.sleep(10)
        repository.createSession(baseState.copy(intention = "Confidential keys"), confidentialityLevel = "CONFIDENTIAL")
        Thread.sleep(10)
        repository.createSession(baseState.copy(intention = "Public blog"), confidentialityLevel = "PUBLIC")

        val publicSessions = repository.listSessionsByConfidentiality("PUBLIC", limit = 10)
        assertEquals(2, publicSessions.size)
        assertTrue(publicSessions.all { it.confidentialityLevel == "PUBLIC" })

        val internalSessions = repository.listSessionsByConfidentiality("INTERNAL", limit = 10)
        assertEquals(1, internalSessions.size)
        assertEquals("INTERNAL", internalSessions[0].confidentialityLevel)

        val secretSessions = repository.listSessionsByConfidentiality("SECRET", limit = 10)
        assertEquals(0, secretSessions.size)
    }

    @Test
    fun `deleteSession cascades to steps`() = runBlocking {
        repository.initSchema()

        val state = VibecodingState(intention = "To be deleted", workspaceRoot = "/tmp")
        val sessionId = repository.createSession(state)

        repository.addStep(sessionId, "exec_gradle", "exec_shell", """{"task":"build"}""", 500, null)
        repository.addStep(sessionId, "llm_call", null, """{"prompt":"test"}""", 1000, null)

        var stepCount = 0L
        val conn1 = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            stepCount = conn1.queryForLong(
                "SELECT count(*) FROM vibecoding_steps WHERE session_id = '$sessionId'"
            )
        } finally {
            Mono.from(conn1.close()).subscribe()
        }
        assertEquals(2L, stepCount, "Steps should exist before delete")

        val deleted = repository.deleteSession(sessionId)
        assertTrue(deleted, "deleteSession should return true")

        val record = repository.getSession(sessionId)
        assertNull(record, "Session should be deleted")

        var stepCountAfter = 0L
        val conn2 = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            stepCountAfter = conn2.queryForLong(
                "SELECT count(*) FROM vibecoding_steps WHERE session_id = '$sessionId'"
            )
        } finally {
            Mono.from(conn2.close()).subscribe()
        }
        assertEquals(0L, stepCountAfter, "Steps should be cascade-deleted")
    }

    @Test
    fun `deleteSession returns false for non-existent id`() = runBlocking {
        repository.initSchema()

        val deleted = repository.deleteSession("00000000-0000-0000-0000-000000000000")
        assertFalse(deleted)
    }

    @Test
    fun `costByConfidentialityLevel aggregates correctly`() = runBlocking {
        repository.initSchema()

        val state = VibecodingState(intention = "cost test", workspaceRoot = "/tmp")
        repository.createSession(state.copy(intention = "Public A"), confidentialityLevel = "PUBLIC")
        repository.createSession(state.copy(intention = "Public B"), confidentialityLevel = "PUBLIC")
        repository.createSession(state.copy(intention = "Internal A"), confidentialityLevel = "INTERNAL")
        repository.createSession(state.copy(intention = "Conf A"), confidentialityLevel = "CONFIDENTIAL")

        val conn = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            Mono.from(
                conn.createStatement(
                    "UPDATE vibecoding_sessions SET cost = 1.5 WHERE confidentiality_level = 'PUBLIC'"
                ).execute()
            ).awaitSingle()
            Mono.from(
                conn.createStatement(
                    "UPDATE vibecoding_sessions SET cost = 2.0 WHERE confidentiality_level = 'INTERNAL'"
                ).execute()
            ).awaitSingle()
            Mono.from(
                conn.createStatement(
                    "UPDATE vibecoding_sessions SET cost = 5.0 WHERE confidentiality_level = 'CONFIDENTIAL'"
                ).execute()
            ).awaitSingle()
        } finally {
            Mono.from(conn.close()).subscribe()
        }

        val costs = repository.costByConfidentialityLevel()

        assertEquals(3.0, costs["PUBLIC"] ?: 0.0, 0.01, "2 PUBLIC * 1.5 = 3.0")
        assertEquals(2.0, costs["INTERNAL"] ?: 0.0, 0.01)
        assertEquals(5.0, costs["CONFIDENTIAL"] ?: 0.0, 0.01)
        assertEquals(null, costs["SECRET"])
    }
}
