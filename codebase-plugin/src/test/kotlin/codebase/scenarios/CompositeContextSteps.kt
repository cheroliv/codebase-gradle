package codebase.scenarios

import codebase.rag.CompositeContext
import codebase.rag.CompositeContextBuilder
import codebase.rag.CompositeContextConfig
import codebase.rag.EmbeddingPipeline
import codebase.rag.VectorStore
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompositeContextSteps {

    private val log = LoggerFactory.getLogger(CompositeContextSteps::class.java)
    private val ctx = PgVectorTestContext
    private var composite: CompositeContext? = null

    @When("I build a CompositeContext with the question {string} and budget {int}/{int}/{int}/{int}")
    fun `build CompositeContext`(question: String, eagerPct: Int, ragPct: Int, graphifyPct: Int, overheadPct: Int) {
        val store = VectorStore(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword())
        val pipeline = EmbeddingPipeline(store)
        val config = CompositeContextConfig(
            totalTokenBudget = 8000,
            budgetEagerLazy = eagerPct / 100.0,
            budgetRag = ragPct / 100.0,
            budgetGraphify = graphifyPct / 100.0,
            budgetOverhead = overheadPct / 100.0
        )
        val workspaceRoot = File(".").absoluteFile

        val builder = CompositeContextBuilder(workspaceRoot, store, pipeline, config)
        composite = builder.build(question)
        CompositeContextHolder.set(composite!!)

        log.info("CompositeContext built: EAGER={} chars, RAG={} chars, Graphify={} chars",
            composite!!.eagerSection.length, composite!!.ragSection.length, composite!!.graphifySection.length)
    }

    @Then("the composite context has EAGER, RAG, and Graphify sections")
    fun `composite has all sections`() {
        val c = composite
        assertNotNull(c, "CompositeContext was not built")
        assertTrue(c.eagerSection.isNotEmpty(), "EAGER section should not be empty")
        assertTrue(c.ragSection.isNotEmpty(), "RAG section should not be empty")
        assertTrue(c.graphifySection.isNotEmpty(), "Graphify section should not be empty")
        log.info("All 3 sections populated")
    }

    @Then("the token budget totals {int} with split {int} EAGER, {int} RAG, {int} Graphify, {int} overhead")
    fun `token budget split`(total: Int, eager: Int, rag: Int, graphify: Int, overhead: Int) {
        val c = composite
        assertNotNull(c, "CompositeContext was not built")
        assertEquals(total, c.config.totalTokenBudget, "Total token budget mismatch")
        assertEquals(eager, c.config.eagerLazyTokens, "EAGER token budget mismatch")
        assertEquals(rag, c.config.ragTokens, "RAG token budget mismatch")
        assertEquals(graphify, c.config.graphifyTokens, "Graphify token budget mismatch")
        assertEquals(overhead, c.config.overheadTokens, "Overhead token budget mismatch")
        log.info("Token budget verified: $total total, $eager/$rag/$graphify/$overhead split")
    }

    @Then("the RAG section contains at least {int} relevance-ranked chunks")
    fun `rag section contains minimum chunks`(minChunks: Int) {
        val c = composite
        assertNotNull(c, "CompositeContext was not built")
        val chunkCount = c.ragSection.lines().count { it.startsWith("[sim=") }
        assertTrue(chunkCount >= minChunks,
            "Expected at least $minChunks RAG chunks, got $chunkCount")
        log.info("RAG section contains $chunkCount chunks (min required: $minChunks)")
    }

    @Then("each chunk has a similarity score between {double} and {double}")
    fun `chunks have valid similarity scores`(min: Double, max: Double) {
        val c = composite
        assertNotNull(c, "CompositeContext was not built")
        val simRegex = Regex("""\[sim=(\d+\.\d+)\]""")
        c.ragSection.lines().filter { it.startsWith("[sim=") }.forEach { line ->
            val score = simRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            assertNotNull(score, "Missing similarity score in chunk: ${line.take(80)}")
            assertTrue(score in min..max,
                "Similarity $score out of range [$min, $max] for chunk: ${line.take(80)}")
        }
        log.info("All RAG chunks have valid similarity scores in [$min, $max]")
    }
}
