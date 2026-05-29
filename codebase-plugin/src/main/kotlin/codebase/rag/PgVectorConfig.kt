package codebase.rag

data class PgVectorConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String
) {
    companion object {
        private fun jdbcUrlWithTimeout(baseUrl: String): String {
            // Ajoute connectTimeout + socketTimeout pour éviter blocage
            // quand pgvector est hors-ligne (sinon TCP timeout OS ≈ 2 min)
            if (baseUrl.contains("?")) return "$baseUrl&connectTimeout=5&socketTimeout=5"
            return "$baseUrl?connectTimeout=5&socketTimeout=5"
        }

        fun fromEnv(): PgVectorConfig = PgVectorConfig(
            jdbcUrl = jdbcUrlWithTimeout(
                System.getenv("PGVECTOR_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/codebase_rag"
            ),
            user = System.getenv("PGVECTOR_USER") ?: "codebase",
            password = System.getenv("PGVECTOR_PASSWORD") ?: "codebase"
        )
    }

    fun toVectorStore(): VectorStore = VectorStore(jdbcUrl, user, password)
}
