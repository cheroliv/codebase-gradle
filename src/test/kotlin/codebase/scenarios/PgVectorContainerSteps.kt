package codebase.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

class PgVectorContainerSteps {

    companion object {
        private val log = LoggerFactory.getLogger(PgVectorContainerSteps::class.java)
    }

    private var container: PostgreSQLContainer<Nothing>? = null

    @When("I start a pgvector container")
    fun `start a pgvector container`() {
        log.info("Starting pgvector container...")

        container = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg17").apply {
            withDatabaseName("codebase_rag")
            withUsername("codebase")
            withPassword("codebase")
            withStartupTimeout(java.time.Duration.ofMinutes(2))
            withReuse(false)
        }.also { c ->
            try {
                c.start()
                log.info("pgvector container started: ${c.containerId}")
                log.info("  JDBC URL: ${c.jdbcUrl}")
                log.info("  Port: ${c.firstMappedPort}")
            } catch (e: Exception) {
                log.error("Failed to start pgvector container: ${e.message}", e)
                throw e
            }
        }
    }

    @When("I stop the container")
    fun `stop the container`() {
        container?.let { c ->
            log.info("Stopping pgvector container: ${c.containerId}")
            c.stop()
            log.info("pgvector container stopped")
        }
    }

    @Then("the container is running")
    fun `the container is running`() {
        val c = container ?: throw AssertionError("Container not initialized")
        assert(c.isRunning) { "Container should be running" }
        log.info("Container is running: ${c.containerId}")
    }

    @Then("the container is not running")
    fun `the container is not running`() {
        val c = container ?: throw AssertionError("Container not initialized")
        assert(!c.isRunning) { "Container should not be running" }
        log.info("Container is stopped")
    }

    @Then("the vector extension is available")
    fun `the vector extension is available`() {
        val c = container ?: throw AssertionError("Container not initialized")
        DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM pg_available_extensions WHERE name = 'vector'")
                rs.next()
                val count = rs.getInt(1)
                assert(count > 0) { "pgvector extension should be available, got count=$count" }
                log.info("pgvector extension is available")
            }
        }
    }

    @Then("I can create a table with a vector\\({int}) column")
    fun `i can create a table with a vector column`(dimensions: Int) {
        val c = container ?: throw AssertionError("Container not initialized")
        DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector")
                stmt.execute("DROP TABLE IF EXISTS test_embeddings")
                stmt.execute("CREATE TABLE test_embeddings (id SERIAL PRIMARY KEY, embedding vector($dimensions))")
                log.info("Table test_embeddings created with vector($dimensions)")
            }
        }
    }
}
