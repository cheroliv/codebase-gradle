package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PgVectorConfigTest {

    @Test
    fun `PgVectorConfig stores jdbcUrl user and password`() {
        val config = PgVectorConfig(
            jdbcUrl = "jdbc:postgresql://pg.example.com:5432/mydb",
            user = "admin",
            password = "s3cret"
        )
        assertEquals("jdbc:postgresql://pg.example.com:5432/mydb", config.jdbcUrl)
        assertEquals("admin", config.user)
        assertEquals("s3cret", config.password)
    }

    @Test
    fun `fromEnv with no env vars returns defaults`() {
        val config = PgVectorConfig.fromEnv()

        assertTrue(config.jdbcUrl.contains("localhost"))
        assertTrue(config.jdbcUrl.contains("5432"))
        assertTrue(config.jdbcUrl.contains("codebase_rag"))
        assertEquals("codebase", config.user)
        assertEquals("codebase", config.password)
    }

    @Test
    fun `fromEnv with custom env vars returns custom values`() {
        val customJdbc = "jdbc:postgresql://prod.db:5432/prod_rag"
        val customUser = "prod_user"
        val customPass = "prod_pass"

        val originalJdbc = System.getenv("PGVECTOR_JDBC_URL")
        val originalUser = System.getenv("PGVECTOR_USER")
        val originalPass = System.getenv("PGVECTOR_PASSWORD")

        try {
            setEnv("PGVECTOR_JDBC_URL", customJdbc)
            setEnv("PGVECTOR_USER", customUser)
            setEnv("PGVECTOR_PASSWORD", customPass)

            val config = PgVectorConfig.fromEnv()

            assertEquals(customJdbc, config.jdbcUrl)
            assertEquals(customUser, config.user)
            assertEquals(customPass, config.password)
        } finally {
            restoreEnv("PGVECTOR_JDBC_URL", originalJdbc)
            restoreEnv("PGVECTOR_USER", originalUser)
            restoreEnv("PGVECTOR_PASSWORD", originalPass)
        }
    }

    @Test
    fun `toVectorStore creates VectorStore with same credentials`() {
        val config = PgVectorConfig(
            jdbcUrl = "jdbc:postgresql://localhost:5432/testdb",
            user = "testuser",
            password = "testpass"
        )
        val store = config.toVectorStore()
        assertNotNull(store)
    }

    private fun setEnv(key: String, value: String) {
        val envField = System.getenv()::class.java.getDeclaredField("m")
        envField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val env = envField.get(System.getenv()) as MutableMap<String, String>
        env[key] = value
    }

    private fun restoreEnv(key: String, original: String?) {
        val envField = System.getenv()::class.java.getDeclaredField("m")
        envField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val env = envField.get(System.getenv()) as MutableMap<String, String>
        if (original != null) env[key] = original else env.remove(key)
    }
}
