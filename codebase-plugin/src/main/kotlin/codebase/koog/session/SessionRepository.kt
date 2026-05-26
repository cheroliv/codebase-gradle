package codebase.koog.session

import codebase.koog.tracking.TokenTracker
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitSingle
import reactor.core.publisher.Mono
import vibecoding.contracts.state.VibecodingState
import java.time.Instant
import java.util.UUID

interface SessionRepository {

    suspend fun initSchema(): Unit = error("Not supported")

    suspend fun createSession(
        state: VibecodingState,
        confidentialityLevel: String = "INTERNAL"
    ): String = error("Not supported")

    suspend fun updateSession(
        id: String,
        state: VibecodingState,
        model: String = "unknown",
        tokenTracker: codebase.koog.tracking.TokenTracker = codebase.koog.tracking.TokenTracker()
    ): Boolean = error("Not supported")

    suspend fun addStep(
        sessionId: String,
        stepType: String,
        toolName: String?,
        stepData: String,
        durationMs: Long,
        error: String?
    ): Long = error("Not supported")

    suspend fun getSession(id: String): SessionRecord? = error("Not supported")

    suspend fun listSessions(limit: Int = 20): List<SessionRecord> = error("Not supported")

    suspend fun listSessionsByConfidentiality(
        level: String,
        limit: Int = 20
    ): List<SessionRecord> = error("Not supported")

    suspend fun deleteSession(id: String): Boolean = error("Not supported")

    suspend fun costByConfidentialityLevel(): Map<String, Double> = error("Not supported")

    suspend fun allSessions(): List<SessionRecord> = listSessions(Int.MAX_VALUE)

    suspend fun sessionsSince(since: Instant): List<SessionRecord> =
        allSessions().filter { (it.endTime ?: it.startTime) >= since }

    suspend fun countSessions(): Int = allSessions().size

    suspend fun countSessionsSince(since: Instant): Int = sessionsSince(since).size

    companion object {
        operator fun invoke(connectionFactory: ConnectionFactory): SessionRepository =
            R2dbcSessionRepository(connectionFactory)
    }
}

private class R2dbcSessionRepository(
    private val connectionFactory: ConnectionFactory
) : SessionRepository {

    private suspend fun <R> withConnection(block: suspend (conn: Connection) -> R): R {
        val conn = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            return block(conn)
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    override suspend fun initSchema() {
        MigrationRunner(connectionFactory).migrate()
    }

    override suspend fun createSession(
        state: VibecodingState,
        confidentialityLevel: String
    ): String {
        val id = UUID.randomUUID().toString()

        withConnection { conn ->
            val s = conn.createStatement(
                "INSERT INTO vibecoding_sessions" +
                " (id, parent_session_id, workspace_root, intention, dry_run, max_actions," +
                "  classification, plan_json, error, finished, iteration_count, confidentiality_level)" +
                " VALUES" +
                " ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9, $10, $11, $12)"
            )
            s.bind("$1", id)
            s.bindNull("$2", String::class.java)
            s.bind("$3", state.workspaceRoot)
            s.bind("$4", state.intention)
            s.bind("$5", state.dryRun)
            s.bind("$6", state.maxActions)
            s.bind("$7", state.classification.ifEmpty { "" })
            if (state.planJson.isEmpty()) s.bindNull("$8", String::class.java)
            else s.bind("$8", state.planJson)
            if (state.error != null) s.bind("$9", state.error) else s.bindNull("$9", String::class.java)
            s.bind("$10", state.finished)
            s.bind("$11", state.iteration)
            s.bind("$12", confidentialityLevel)

            Mono.from(s.execute())
                .flatMap { Mono.from(it.rowsUpdated) }.defaultIfEmpty(0L)
                .awaitSingle()
        }

        return id
    }

    override suspend fun updateSession(
        id: String,
        state: VibecodingState,
        model: String,
        tokenTracker: TokenTracker
    ): Boolean {
        val cost = tokenTracker.estimatedCost(model)
        return withConnection { conn ->
            val s = conn.createStatement(
                "UPDATE vibecoding_sessions" +
                " SET error = $2, finished = $3, iteration_count = $4," +
                "     classification = $5, prompt_tokens = $6, completion_tokens = $7," +
                "     cost = $8, model = $9, updated_at = NOW()" +
                " WHERE id = $1"
            )
            s.bind("$1", id)
            if (state.error != null) s.bind("$2", state.error) else s.bindNull("$2", String::class.java)
            s.bind("$3", state.finished)
            s.bind("$4", state.iteration)
            s.bind("$5", state.classification.ifEmpty { "" })
            s.bind("$6", tokenTracker.promptTokens)
            s.bind("$7", tokenTracker.completionTokens)
            s.bind("$8", cost)
            s.bind("$9", model)

            val updated = Mono.from(s.execute())
                .flatMap { Mono.from(it.rowsUpdated) }.defaultIfEmpty(0L)
                .awaitSingle()
            updated > 0L
        }
    }

    override suspend fun addStep(
        sessionId: String,
        stepType: String,
        toolName: String?,
        stepData: String,
        durationMs: Long,
        error: String?
    ): Long {
        return withConnection { conn ->
            val s = conn.createStatement(
                "INSERT INTO vibecoding_steps" +
                " (session_id, step_type, tool_name, step_data, duration_ms, error)" +
                " VALUES ($1, $2, $3, $4::jsonb, $5, $6)" +
                " RETURNING id"
            )
            s.bind("$1", sessionId)
            s.bind("$2", stepType)
            if (toolName != null) s.bind("$3", toolName) else s.bindNull("$3", String::class.java)
            s.bind("$4", stepData)
            s.bind("$5", durationMs)
            if (error != null) s.bind("$6", error) else s.bindNull("$6", String::class.java)

            Mono.from(s.execute())
                .flatMap { result ->
                    Mono.from(result.map { row, _ -> row.get("id", Long::class.java)!! })
                }
                .awaitSingle()
        }
    }

    override suspend fun getSession(id: String): SessionRecord? {
        return withConnection { conn ->
            val list: List<SessionRecord> = Mono.from(
                conn.createStatement(
                    "SELECT id, parent_session_id, workspace_root, intention, dry_run," +
                    " max_actions, classification, plan_json, prompt_tokens, completion_tokens," +
                    " cost, error, finished, iteration_count, confidentiality_level," +
                    " created_at, updated_at" +
                    " FROM vibecoding_sessions WHERE id = $1"
                )
                    .bind("$1", id)
                    .execute()
            ).flatMapMany { result ->
                result.map { row, _ -> mapToSessionRecord(row) }
            }.collectList().awaitSingle()
            list.firstOrNull()
        }
    }

    override suspend fun listSessions(limit: Int): List<SessionRecord> {
        return withConnection { conn ->
            Mono.from(
                conn.createStatement(
                    "SELECT id, parent_session_id, workspace_root, intention, dry_run," +
                    " max_actions, classification, plan_json, prompt_tokens, completion_tokens," +
                    " cost, error, finished, iteration_count, confidentiality_level," +
                    " created_at, updated_at" +
                    " FROM vibecoding_sessions ORDER BY created_at DESC LIMIT $1"
                )
                    .bind("$1", limit)
                    .execute()
            ).flatMapMany { result ->
                result.map { row, _ -> mapToSessionRecord(row) }
            }.collectList().awaitSingle()
        }
    }

    override suspend fun listSessionsByConfidentiality(
        level: String,
        limit: Int
    ): List<SessionRecord> {
        return withConnection { conn ->
            Mono.from(
                conn.createStatement(
                    "SELECT id, parent_session_id, workspace_root, intention, dry_run," +
                    " max_actions, classification, plan_json, prompt_tokens, completion_tokens," +
                    " cost, error, finished, iteration_count, confidentiality_level," +
                    " created_at, updated_at" +
                    " FROM vibecoding_sessions WHERE confidentiality_level = $1" +
                    " ORDER BY created_at DESC LIMIT $2"
                )
                    .bind("$1", level)
                    .bind("$2", limit)
                    .execute()
            ).flatMapMany { result ->
                result.map { row, _ -> mapToSessionRecord(row) }
            }.collectList().awaitSingle()
        }
    }

    override suspend fun deleteSession(id: String): Boolean {
        return withConnection { conn ->
            val deleted = Mono.from(
                conn.createStatement("DELETE FROM vibecoding_sessions WHERE id = $1")
                    .bind("$1", id)
                    .execute()
            ).flatMap { Mono.from(it.rowsUpdated) }.defaultIfEmpty(0L)
                .awaitSingle()
            deleted > 0L
        }
    }

    override suspend fun costByConfidentialityLevel(): Map<String, Double> {
        return withConnection { conn ->
            val pairs = Mono.from(
                conn.createStatement(
                    "SELECT confidentiality_level, COALESCE(SUM(cost), 0.0) AS total_cost" +
                    " FROM vibecoding_sessions" +
                    " GROUP BY confidentiality_level"
                ).execute()
            ).flatMapMany { result ->
                result.map { row, _ ->
                    row.get("confidentiality_level", String::class.java)!! to
                    (row.get("total_cost", Double::class.java) ?: 0.0)
                }
            }.collectList().awaitSingle()
            pairs.toMap()
        }
    }

    override suspend fun allSessions(): List<SessionRecord> = listSessions(Int.MAX_VALUE)

    override suspend fun countSessions(): Int = listSessions(Int.MAX_VALUE).size

    private fun mapToSessionRecord(row: io.r2dbc.spi.Row): SessionRecord {
        return SessionRecord(
            id = row.get("id", String::class.java)!!,
            parentSessionId = row.get("parent_session_id", String::class.java),
            workspaceRoot = row.get("workspace_root", String::class.java)!!,
            intention = row.get("intention", String::class.java)!!,
            dryRun = row.get("dry_run", Boolean::class.java) ?: false,
            maxActions = row.get("max_actions", Int::class.java) ?: 10,
            classification = row.get("classification", String::class.java) ?: "",
            planJson = row.get("plan_json", String::class.java),
            promptTokens = row.get("prompt_tokens", Long::class.java) ?: 0L,
            completionTokens = row.get("completion_tokens", Long::class.java) ?: 0L,
            cost = row.get("cost", Double::class.java) ?: 0.0,
            error = row.get("error", String::class.java),
            finished = row.get("finished", Boolean::class.java) ?: false,
            iterationCount = row.get("iteration_count", Int::class.java) ?: 0,
            confidentialityLevel = row.get("confidentiality_level", String::class.java) ?: "INTERNAL",
            createdAt = row.get("created_at", Instant::class.java)!!,
            updatedAt = row.get("updated_at", Instant::class.java)!!
        )
    }
}
