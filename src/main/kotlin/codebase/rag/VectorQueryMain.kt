package codebase.rag

import org.slf4j.LoggerFactory

object VectorQueryMain {
    private val log = LoggerFactory.getLogger(VectorQueryMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            log.error("Usage: java ... VectorQueryMain <query> [topK] [fileType(.kt,.yml,.json,.adoc)]")
            return
        }

        val query = args[0]
        val topK = if (args.size >= 2) args[1].toIntOrNull() ?: 10 else 10
        val fileType = if (args.size >= 3) args[2].takeIf { it.isNotBlank() } else null
        val jdbcUrl = System.getenv("PGVECTOR_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/codebase_rag"
        val user = System.getenv("PGVECTOR_USER") ?: "codebase"
        val password = System.getenv("PGVECTOR_PASSWORD") ?: "codebase"

        val store = VectorStore(jdbcUrl, user, password)
        val pipeline = EmbeddingPipeline(store)
        val service = VectorQueryService(store, pipeline)

        val results = service.query(query, topK, fileType)

        val typeInfo = if (fileType != null) " (filter=.$fileType)" else ""
        log.info("Query: \"{}\"{} → {} result(s) (topK={})", query, typeInfo, results.size, topK)
        results.forEachIndexed { i, r ->
            log.info("  #{} similarity={} text={}", i + 1, "%.4f".format(r.similarity), r.text.take(120))
        }
    }
}
