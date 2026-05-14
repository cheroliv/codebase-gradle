package codebase.scenarios

import codebase.rag.CompositeContext
import codebase.rag.OpencodeInjector
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpencodeInjectorSteps {

    private val log = LoggerFactory.getLogger(OpencodeInjectorSteps::class.java)
    private val ctx = PgVectorTestContext

    companion object {
        var lastInjected: String? = null
    }

    @When("I inject the composite context through OpencodeInjector")
    fun `inject composite context`() {
        val composite = CompositeContextHolder.get()
        assertNotNull(composite, "CompositeContextHolder was not set by a previous step")

        val injector = OpencodeInjector()
        val result = injector.inject(composite)

        assertNotNull(result, "OpencodeInjector returned null")
        lastInjected = result
        log.info("OpencodeInjector: {} chars injected", result.length)
    }

    @Then("the injected output contains the header {string}")
    fun `injected output contains header`(header: String) {
        val output = lastInjected
        assertNotNull(output, "Injected output is null")
        assertTrue(output.contains(header),
            "Expected header '$header' not found in injected output")
        log.info("Header '$header' found in injected output")
    }

    @Then("each section after its header has at least 1 line of non-empty content")
    fun `each section has content after header`() {
        val output = lastInjected
        assertNotNull(output, "Injected output is null")

        val headers = listOf("[RÈGLES_EAGER]", "[CONTEXTE_RAG]", "[RELATIONS_GRAPHIFY]")
        val lines = output.lines()

        for (header in headers) {
            val headerIndex = lines.indexOfFirst { it.trim() == header }
            assertTrue(headerIndex >= 0, "Header '$header' not found in injected output")

            var foundContent = false
            for (i in (headerIndex + 1) until lines.size) {
                if (headers.any { lines[i].trim() == it }) break
                if (lines[i].isNotBlank()) {
                    foundContent = true
                    break
                }
            }
            assertTrue(foundContent, "No non-empty content after header '$header'")
            log.info("Content found after header '$header'")
        }
    }
}

object CompositeContextHolder {
    private var composite: CompositeContext? = null

    fun set(ctx: CompositeContext) { composite = ctx }
    fun get(): CompositeContext? = composite
}
