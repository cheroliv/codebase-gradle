package codebase.scenarios

import codebase.koog.session.SessionRecord
import codebase.koog.session.SessionRepository
import vibecoding.contracts.state.VibecodingState
import java.time.Instant

/**
 * World Object injecté par PicoContainer dans toutes les Steps Cucumber
 * de type SessionRepository + Dashboard.
 *
 * Pattern aligné sur VibecodingWorld — PicoContainer crée une nouvelle instance par scénario.
 * Le container Testcontainers est lazy (démarré au besoin) et partagé entre scénarios
 * via un companion object (évite de recréer le container à chaque scénario).
 */
class SessionRepositoryWorld {

    companion object {
        private var sharedContainer: org.testcontainers.containers.PostgreSQLContainer<Nothing>? = null
        private var sharedConnectionFactory: io.r2dbc.spi.ConnectionFactory? = null
        private var sharedRepository: SessionRepository? = null

        @Synchronized
        fun ensureStarted() {
            if (sharedContainer == null || !sharedContainer!!.isRunning) {
                val container = org.testcontainers.containers.PostgreSQLContainer<Nothing>("pgvector/pgvector:pg17").apply {
                    withDatabaseName("codebase_vibecoding_cucumber")
                    withUsername("codebase")
                    withPassword("codebase")
                    withReuse(false)
                }
                container.start()
                sharedContainer = container

                val config = io.r2dbc.postgresql.PostgresqlConnectionConfiguration.builder()
                    .host(container.host)
                    .port(container.getMappedPort(5432))
                    .database(container.databaseName)
                    .username(container.username)
                    .password(container.password)
                    .build()
                sharedConnectionFactory = io.r2dbc.postgresql.PostgresqlConnectionFactory(config)
                sharedRepository = SessionRepository(sharedConnectionFactory!!)
            }
        }
    }

    val connectionFactory: io.r2dbc.spi.ConnectionFactory
        get() {
            ensureStarted()
            return sharedConnectionFactory!!
        }

    val repository: SessionRepository
        get() {
            ensureStarted()
            return sharedRepository!!
        }

    var lastCreatedSessionId: String? = null
    var createdSessionIds: MutableList<String> = mutableListOf()
    var lastGetResult: SessionRecord? = null
    var listedSessions: List<SessionRecord> = emptyList()
    var dashboardSummary: codebase.koog.tracking.DashboardSummary? = null
    var confidentialityCosts: Map<String, Double> = emptyMap()
    var stepCount: Int = 0
    var deletionResult: Boolean = false
    var lastError: String? = null

    fun reset() {
        lastCreatedSessionId = null
        createdSessionIds.clear()
        lastGetResult = null
        listedSessions = emptyList()
        dashboardSummary = null
        confidentialityCosts = emptyMap()
        stepCount = 0
        deletionResult = false
        lastError = null
    }
}
