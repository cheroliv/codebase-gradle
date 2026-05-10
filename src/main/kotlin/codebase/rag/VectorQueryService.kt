package codebase.rag

data class RelevantChunk(
    val chunkId: Long,
    val text: String,
    val similarity: Double,
    val fileName: String? = null,
    val packageName: String? = null,
    val className: String? = null,
    val repoName: String? = null
)

class VectorQueryService(
    private val vectorStore: VectorStore,
    private val embeddingPipeline: EmbeddingPipeline
) {
    private val fileNameRegex = Regex("""^\((.+?)\) """)

    fun query(text: String, topK: Int = 10, fileType: String? = null): List<RelevantChunk> {
        val vectorStr = embeddingPipeline.embedQuery(text)
        val results = vectorStore.querySimilar(vectorStr, topK, fileType)
        return results.map { r ->
            val match = fileNameRegex.find(r.text)
            RelevantChunk(
                chunkId = r.chunkId,
                text = r.text,
                similarity = r.similarity,
                fileName = match?.groupValues?.get(1)
            )
        }
    }
}
