package codebase.rag

data class PgVectorConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String
) {
    companion object {
        fun fromEnv(): PgVectorConfig = PgVectorConfig(
            jdbcUrl = System.getenv("PGVECTOR_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/codebase_rag",
            user = System.getenv("PGVECTOR_USER") ?: "codebase",
            password = System.getenv("PGVECTOR_PASSWORD") ?: "codebase"
        )
    }

    fun toVectorStore(): VectorStore = VectorStore(jdbcUrl, user, password)
}
