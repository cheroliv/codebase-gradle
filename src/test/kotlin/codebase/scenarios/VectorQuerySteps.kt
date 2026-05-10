package codebase.scenarios

import codebase.rag.EmbeddingPipeline
import codebase.rag.VectorQueryService
import codebase.rag.VectorStore
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory

class VectorQuerySteps {

    private val log = LoggerFactory.getLogger(VectorQuerySteps::class.java)
    private val ctx = PgVectorTestContext
    private var queryServiceResults = listOf<codebase.rag.RelevantChunk>()

    @When("I query via VectorQueryService with the phrase {string} and topK {int}")
    fun `query via VectorQueryService`(query: String, topK: Int) {
        val store = VectorStore(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword())
        val pipeline = EmbeddingPipeline(store)
        val service = VectorQueryService(store, pipeline)

        queryServiceResults = service.query(query, topK)
        log.info("VectorQueryService returned ${queryServiceResults.size} results for \"$query\"")
        queryServiceResults.forEachIndexed { i, r ->
            log.info("  #{} similarity={} text={}", i + 1, "%.4f".format(r.similarity), r.text.take(100))
        }

        ctx.topResults = queryServiceResults.map { r ->
            PgVectorTestContext.TopResult(
                chunkId = r.chunkId,
                text = r.text,
                similarity = r.similarity
            )
        }
    }

    @Then("exactly {int} results are returned")
    fun `exactly N results returned`(expected: Int) {
        assert(queryServiceResults.size == expected) {
            "Expected $expected results, got ${queryServiceResults.size}"
        }
        log.info("Confirmed: exactly $expected results returned")
    }
}
