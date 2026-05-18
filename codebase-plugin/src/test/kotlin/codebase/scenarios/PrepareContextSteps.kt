package codebase.scenarios

import codebase.rag.CompositeContextBuilder
import codebase.rag.CompositeContextConfig
import codebase.rag.EmbeddingPipeline
import codebase.rag.OpencodeInjector
import codebase.rag.VectorStore
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.io.File

class PrepareContextSteps {

    private val log = LoggerFactory.getLogger(PrepareContextSteps::class.java)
    private val ctx = PgVectorTestContext

    @When("I run collectFromCodebase scoped to {string} with question {string}")
    fun `run collectFromCodebase scoped to borough`(boroughName: String, question: String) {
        val store = VectorStore(ctx.jdbcUrl(), ctx.jdbcUser(), ctx.jdbcPassword())
        val pipeline = EmbeddingPipeline(store)
        val config = CompositeContextConfig()
        val workspaceRoot = File(".").absoluteFile

        val builder = CompositeContextBuilder(workspaceRoot, store, pipeline, config)
        val composite = builder.buildScoped(boroughName, question)

        val outputDir = File("build/context")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "$boroughName.context.txt")

        OpencodeInjector().injectToFile(composite, outputFile)
        PreparedContextHolder.set(outputFile)
        log.info("CollectFromCodebase '{}' -> {} ({} bytes)", boroughName, outputFile.absolutePath, outputFile.length())
    }

    @Then("the output file {string} exists")
    fun `output file exists`(path: String) {
        val file = File(path)
        assert(file.isFile) { "Expected '$path' to exist" }
    }

    @Then("the output file {string} contains {string}")
    fun `output file contains string`(path: String, expected: String) {
        val content = File(path).readText()
        assert(content.contains(expected)) { "Expected '$path' to contain '$expected'" }
    }

    @Then("the output file size is at least {int} bytes")
    fun `output file size minimum`(minBytes: Int) {
        val file = PreparedContextHolder.get() ?: throw AssertionError("No prepared context file set")
        assert(file.length() >= minBytes) { "Expected >= $minBytes bytes, got ${file.length()}" }
    }

    @Then("the RAG section in output file contains at least {int} similarity-scored chunk")
    fun `rag section contains minimum scored chunks`(minChunks: Int) {
        val file = PreparedContextHolder.get() ?: throw AssertionError("No prepared context file set")
        val count = Regex("""\[sim=""").findAll(file.readText()).count()
        assert(count >= minChunks) { "Expected >= $minChunks sim chunks, got $count" }
    }
}

object PreparedContextHolder {
    private var file: File? = null
    fun set(f: File) { file = f }
    fun get(): File? = file
}
