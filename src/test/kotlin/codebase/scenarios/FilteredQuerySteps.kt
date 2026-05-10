package codebase.scenarios

import codebase.rag.EmbeddingPipeline
import codebase.rag.VectorQueryService
import codebase.rag.VectorStore
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory

class FilteredQuerySteps {

    private val log = LoggerFactory.getLogger(FilteredQuerySteps::class.java)
    private val ctx = PgVectorTestContext

    @When("I query via VectorQueryService filtered by file type {string} with phrase {string} and topK {int}")
    fun `query filtered by file type`(fileType: String, query: String, topK: Int) {
        val store = VectorStore(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword())
        val pipeline = EmbeddingPipeline(store)
        val service = VectorQueryService(store, pipeline)

        val results = service.query(query, topK, fileType)
        ctx.topResults = results.map { r ->
            PgVectorTestContext.TopResult(
                chunkId = r.chunkId,
                text = r.text,
                similarity = r.similarity
            )
        }
        log.info("Filtered query ($fileType): ${results.size} result(s)")
        results.forEachIndexed { i, r ->
            log.info("  #{} similarity={} text={}", i + 1, "%.4f".format(r.similarity), r.text.take(100))
        }
    }

    @Then("the top result is from a YAML configuration file")
    fun `top result is from YAML config file`() {
        val top = ctx.topResults.first()
        assert(".yml" in top.text || ".yaml" in top.text) {
            "Top result is not from a YAML file. Top text: ${top.text.take(120)}"
        }
        log.info("Top result verified as YAML configuration file")
    }
}
