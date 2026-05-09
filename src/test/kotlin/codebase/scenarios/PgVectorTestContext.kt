package codebase.scenarios

import codebase.rag.ChunkTokenizer
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel
import org.testcontainers.containers.PostgreSQLContainer

object PgVectorTestContext {

    const val DATASETS_DIR = "src/test/resources/datasets"

    private var sharedContainer: PostgreSQLContainer<Nothing>? = null

    fun jdbcUrl() = sharedContainer?.jdbcUrl
        ?: throw IllegalStateException("Container not started")

    fun jdbcUser() = sharedContainer?.username ?: "codebase"

    fun jdbcPassword() = sharedContainer?.password ?: "codebase"

    fun startContainer() {
        if (sharedContainer != null && sharedContainer!!.isRunning) return
        sharedContainer = PostgreSQLContainer<Nothing>("pgvector/pgvector:pg17").apply {
            withDatabaseName("codebase_rag")
            withUsername("codebase")
            withPassword("codebase")
            withStartupTimeout(java.time.Duration.ofMinutes(2))
            withReuse(false)
        }.also { it.start() }
    }

    val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }

    val fileChunks = mutableMapOf<String, List<String>>()

    var topResults = listOf<TopResult>()

    data class TopResult(val chunkId: Long, val text: String, val similarity: Double)

    fun splitIntoSentenceLevelChunks(text: String): List<String> =
        ChunkTokenizer.splitIntoSentenceLevelChunks(text)

    fun estimateTokenCount(text: String): Int =
        ChunkTokenizer.estimateTokenCount(text)
}
