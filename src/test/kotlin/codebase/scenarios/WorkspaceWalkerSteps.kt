package codebase.scenarios

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory

class WorkspaceWalkerSteps {

    private val log = LoggerFactory.getLogger(WorkspaceWalkerSteps::class.java)
    private val ctx = PgVectorTestContext

    private var discoveredFiles = listOf<WorkspaceFile>()

    @When("I walk the datasets directory with WorkspaceWalker")
    fun `walk datasets directory`() {
        val walker = WorkspaceWalker(java.io.File(ctx.DATASETS_DIR))
        discoveredFiles = walker.walk()
        log.info("WorkspaceWalker discovered ${discoveredFiles.size} file(s)")
        discoveredFiles.forEach { f ->
            log.info("  ${f.fileName} (${f.extension}, ${f.fileSize} bytes)")
        }
    }

    @Then("at least {int} files are discovered")
    fun `at least N files discovered`(min: Int) {
        assert(discoveredFiles.size >= min) {
            "Expected at least $min files, got ${discoveredFiles.size}"
        }
        log.info("${discoveredFiles.size} files discovered (min expected: $min)")
    }

    @Then("all discovered files have valid extension metadata")
    fun `all files have valid extension`() {
        val validExts = setOf("kt", "adoc")
        for (f in discoveredFiles) {
            assert(f.extension in validExts) {
                "File ${f.fileName} has unexpected extension: ${f.extension}"
            }
        }
        log.info("All discovered files have valid extensions (kt, adoc)")
    }

    @Then("no files from build, .git, .gradle, or node_modules are included")
    fun `no build artifact files included`() {
        val skipTokens = listOf("/build/", "/.git/", "/.gradle/", "/node_modules/")
        for (f in discoveredFiles) {
            for (token in skipTokens) {
                assert(token !in f.filePath) {
                    "Skipped path token '$token' found in: ${f.filePath}"
                }
            }
        }
        log.info("No build artifact files found in discovered files")
    }

    @When("I tokenize all dataset files into sentence-level chunks")
    fun `tokenize all dataset files into chunks`() {
        val datasetDir = java.io.File(ctx.DATASETS_DIR)
        val allFiles = datasetDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".kt") || f.name.endsWith(".adoc"))
        }?.sortedBy { it.name }
            ?: throw AssertionError("No files found in ${ctx.DATASETS_DIR}")

        ctx.fileChunks.clear()
        for (file in allFiles) {
            val text = file.readText()
            val chunks = ctx.splitIntoSentenceLevelChunks(text)
            ctx.fileChunks[file.name] = chunks
            log.info("${file.name}: ${chunks.size} chunk(s)")
        }
        val total = ctx.fileChunks.values.sumOf { it.size }
        log.info("Total chunks: $total across ${ctx.fileChunks.size} files")
    }

    @Then("the top result is from an AsciiDoc documentation file")
    fun `top result is from adoc file`() {
        val top = ctx.topResults.first()
        assert(top.text.contains(".adoc")) {
            "Top result is not from adoc: ${top.text.take(150)}"
        }
        log.info("Top result verified as AsciiDoc: similarity=${"%.4f".format(top.similarity)}")
    }

    @Then("the top result is from a Kotlin source file")
    fun `top result is from kt file`() {
        val top = ctx.topResults.first()
        assert(top.text.contains(".kt")) {
            "Top result is not from kt: ${top.text.take(150)}"
        }
        log.info("Top result verified as Kotlin: similarity=${"%.4f".format(top.similarity)}")
    }
}
