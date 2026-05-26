package codebase.koog.session

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import reactor.core.publisher.Mono

class MigrationTest {

    companion object {
        private val container: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg17")
            .apply {
                withDatabaseName("codebase_migration_test")
                withUsername("codebase")
                withPassword("codebase")
                withReuse(false)
            }

        lateinit var connectionFactory: ConnectionFactory
        lateinit var migrationRunner: MigrationRunner

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
            migrationRunner = MigrationRunner(connectionFactory)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            container.stop()
        }
    }

    private suspend fun tableExists(tableName: String): Boolean {
        val conn = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            return Mono.from(
                conn.createStatement(
                    "SELECT EXISTS (" +
                    "SELECT FROM information_schema.tables " +
                    "WHERE table_name = \$1" +
                    ")"
                ).bind("\$1", tableName).execute()
            ).flatMap { result -> Mono.from(result.map { row, _ -> row.get(0, Boolean::class.java)!! }) }
                .awaitSingle()
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    private suspend fun countRows(tableName: String): Long {
        val conn = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            return Mono.from(
                conn.createStatement("SELECT COUNT(*) FROM $tableName").execute()
            ).flatMap { result -> Mono.from(result.map { row, _ -> row.get(0, Long::class.java)!! }) }
                .awaitSingle()
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    private fun dropSchemaVersionTable() {
        runBlocking {
            val conn = Mono.from(connectionFactory.create()).awaitSingle()
            try {
                Mono.from(conn.createStatement("DROP TABLE IF EXISTS schema_version").execute()).awaitSingle()
            } finally {
                Mono.from(conn.close()).subscribe()
            }
        }
    }

    @Test
    fun `should create tables on first migration`() = runBlocking {
        // given: a clean database with no schema_version
        dropSchemaVersionTable()

        // when: migration is applied for the first time
        migrationRunner.migrate()

        // then: schema_version table exists and contains 1 record
        assertTrue(tableExists("schema_version"), "schema_version table should exist after migration")
        assertEquals(2L, countRows("schema_version"), "should have 2 migrations recorded (V1 + V2)")
    }

    @Test
    fun `should be idempotent`() = runBlocking {
        // given: a clean database
        dropSchemaVersionTable()

        // when: migration is applied twice
        migrationRunner.migrate()
        migrationRunner.migrate()

        // then: schema_version still contains only 1 record
        assertEquals(2L, countRows("schema_version"), "idempotent migration should not duplicate records (2 migrations: V1 + V2)")
    }

    @Test
    fun `should not reapply applied migrations`() = runBlocking {
        // given: first migration already applied
        dropSchemaVersionTable()
        migrationRunner.migrate()
        val afterFirst = countRows("schema_version")

        // when: no new migrations available, migrate again
        migrationRunner.migrate()

        // then: no new records added
        assertEquals(afterFirst, countRows("schema_version"), "should not reapply already applied migrations")

        // and: original tables still exist
        assertTrue(tableExists("vibecoding_sessions"), "vibecoding_sessions should exist")
        assertTrue(tableExists("vibecoding_steps"), "vibecoding_steps should exist")
    }
}
