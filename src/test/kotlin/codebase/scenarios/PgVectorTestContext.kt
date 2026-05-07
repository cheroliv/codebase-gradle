package codebase.scenarios

import dev.langchain4j.data.segment.TextSegment
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

    fun splitIntoSentenceLevelChunks(text: String): List<String> {
        val segments = splitIntoSegments(text)
        val results = mutableListOf<String>()
        val current = StringBuilder()
        var currentTokens = 0
        val maxTokens = 512
        val overlapTokens = 50

        for (segment in segments) {
            val segTokens = estimateTokenCount(segment)
            if (currentTokens + segTokens > maxTokens && current.isNotEmpty()) {
                results.add(current.toString().trim())
                val overlap = buildOverlap(current.toString(), overlapTokens)
                current.clear().append(overlap)
                currentTokens = estimateTokenCount(overlap)
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(segment)
            currentTokens += segTokens + 2
        }
        if (current.isNotBlank()) results.add(current.toString().trim())
        if (results.isEmpty()) results.add(text)
        return results
    }

    private fun splitIntoSegments(text: String): List<String> {
        val lines = text.split("\n")
        val segments = mutableListOf<String>()
        val buf = StringBuilder()
        for (line in lines) {
            if (line.isBlank()) {
                if (buf.isNotBlank()) {
                    segments.add(buf.toString().trimEnd())
                    buf.clear()
                }
            } else {
                if (buf.isNotEmpty()) buf.append("\n")
                buf.append(line)
            }
        }
        if (buf.isNotBlank()) segments.add(buf.toString().trimEnd())
        return segments
    }

    private fun buildOverlap(text: String, overlapTokens: Int): String {
        val words = text.split(Regex("\\s+"))
        val overlapWords = (overlapTokens * 0.75).toInt().coerceAtMost(words.size)
        return words.takeLast(overlapWords.coerceAtLeast(1)).joinToString(" ") + "\n\n"
    }

    fun estimateTokenCount(text: String): Int =
        (text.trim().length / 3.5).toInt().coerceAtLeast(1)
}
