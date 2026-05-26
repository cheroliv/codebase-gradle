package codebase.koog.session

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitSingle
import reactor.core.publisher.Mono
import java.security.MessageDigest

/**
 * Minimalist migration runner — Flyway convention without Flyway dependency.
 *
 * Convention:
 * - SQL files in classpath: db/migration/V{n}__{description}.sql
 * - Tracking table: schema_version (version, applied_at, checksum)
 * - Idempotent: applies only migrations not yet in schema_version
 * - Checksum: SHA-256 of SQL content (detects tampering, not enforced yet)
 * - Undo files (V{n}__undo.sql) are ignored by the runner
 */
class MigrationRunner(
    private val connectionFactory: ConnectionFactory
) {
    data class Migration(
        val version: String,
        val description: String,
        val sql: String,
        val checksum: String
    )

    suspend fun migrate() {
        withConnection { conn ->
            ensureSchemaVersionTable(conn)
            val applied = getAppliedVersions(conn)
            val pending = scanMigrations()
                .filter { it.version !in applied }
                .sortedBy { it.version.toInt() }

            for (migration in pending) {
                applyMigration(conn, migration)
                recordMigration(conn, migration)
            }
        }
    }

    private suspend fun <R> withConnection(block: suspend (Connection) -> R): R {
        val conn = Mono.from(connectionFactory.create()).awaitSingle()
        try {
            return block(conn)
        } finally {
            Mono.from(conn.close()).subscribe()
        }
    }

    private suspend fun ensureSchemaVersionTable(conn: Connection) {
        Mono.from(
            conn.createStatement(
                "CREATE TABLE IF NOT EXISTS schema_version (" +
                "  version    VARCHAR(50) PRIMARY KEY," +
                "  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()," +
                "  checksum   VARCHAR(64) NOT NULL" +
                ")"
            ).execute()
        ).flatMap { Mono.from(it.rowsUpdated) }
            .defaultIfEmpty(0L)
            .awaitSingle()
    }

    private suspend fun getAppliedVersions(conn: Connection): Set<String> {
        return Mono.from(
            conn.createStatement("SELECT version FROM schema_version").execute()
        ).flatMapMany { result ->
            result.map { row, _ -> row.get("version", String::class.java)!! }
        }.collectList().awaitSingle().toSet()
    }

    private fun scanMigrations(): List<Migration> {
        val classLoader = javaClass.classLoader
        val resources = classLoader.getResources("db/migration/")
        val migrations = mutableListOf<Migration>()

        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            if (url.protocol == "file") {
                val dir = java.io.File(url.toURI())
                dir.listFiles()?.forEach { file ->
                    val name = file.name
                    val match = Regex("^V(\\d+)__(.+)\\.sql$").matchEntire(name)
                    if (match != null && !name.contains("__undo")) {
                        val version = match.groupValues[1]
                        val description = match.groupValues[2]
                        val sql = file.readText()
                        val checksum = sha256(sql)
                        migrations.add(Migration(version, description, sql, checksum))
                    }
                }
            }
        }

        return migrations.distinctBy { it.version }
    }

    private suspend fun applyMigration(conn: Connection, migration: Migration) {
        val statements = migration.sql
            .lines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (stmt in statements) {
            Mono.from(conn.createStatement(stmt).execute())
                .flatMap { Mono.from(it.rowsUpdated) }
                .defaultIfEmpty(0L)
                .awaitSingle()
        }
    }

    private suspend fun recordMigration(conn: Connection, migration: Migration) {
        val s = conn.createStatement(
            "INSERT INTO schema_version (version, checksum) VALUES ($1, $2)"
        )
        s.bind("$1", migration.version)
        s.bind("$2", migration.checksum)
        Mono.from(s.execute())
            .flatMap { Mono.from(it.rowsUpdated) }
            .awaitSingle()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
