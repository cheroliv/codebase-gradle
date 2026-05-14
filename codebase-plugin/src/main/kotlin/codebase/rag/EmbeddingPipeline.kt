package codebase.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel

class EmbeddingPipeline(private val vectorStore: VectorStore) {

    val model: AllMiniLmL6V2EmbeddingModel by lazy { AllMiniLmL6V2EmbeddingModel() }

    fun dimension() = model.dimension()

    fun embedAndStore(chunkId: Long, text: String) {
        val embedding = model.embed(TextSegment.from(text)).content()
        vectorStore.updateEmbedding(chunkId, embedding.vector().joinToString(",", "[", "]"))
    }

    fun embedAll(chunks: List<Pair<Long, String>>) {
        for ((id, text) in chunks) {
            embedAndStore(id, text)
        }
    }

    fun embedQuery(text: String): String {
        val embedding = model.embed(TextSegment.from(text)).content()
        return embedding.vector().joinToString(",", "[", "]")
    }
}
