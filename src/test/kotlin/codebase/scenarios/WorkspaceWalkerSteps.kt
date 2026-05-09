package codebase.scenarios

import codebase.rag.ChunkTokenizer
import codebase.walker.WorkspaceFile
import codebase.walker.WorkspaceWalker
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory

class WorkspaceWalkerSteps {

    private val log = LoggerFactory.getLogger(WorkspaceWalkerSteps::class.java)
    private val ctx = PgVectorTestContext
    private var discoveredFiles = listOf<WorkspaceFile>()

    @When("I walk the datasets directory with WorkspaceWalker")
    fun `walk datasets with WorkspaceWalker`() {
        val dir = java.io.File("src/test/resources/datasets")
        val walker = WorkspaceWalker(dir)
        discoveredFiles = walker.walk()
        log.info("WorkspaceWalker discovered ${discoveredFiles.size} file(s)")
        discoveredFiles.forEach { f ->
            log.info("  ${f.fileName} (ext=*.${f.extension}, size=${f.fileSize}B)")
        }
    }

    @Then("at least {int} files are discovered")
    fun `at least N files discovered`(minExpected: Int) {
        assert(discoveredFiles.size >= minExpected) {
            "Expected at least $minExpected files, got ${discoveredFiles.size}"
        }
        log.info("Discovered ${discoveredFiles.size} files (min expected: $minExpected)")
    }

    @Then("all discovered files have valid extension metadata")
    fun `valid extension metadata`() {
        assert(discoveredFiles.isNotEmpty()) { "No files discovered" }
        val validExts = setOf("kt", "adoc")
        for (f in discoveredFiles) {
            assert(f.extension in validExts) {
                "Invalid extension '${f.extension}' for ${f.fileName}, expected kt or adoc"
            }
        }
        log.info("All ${discoveredFiles.size} files have valid extension metadata (kt or adoc)")
    }

    @Then("no files from build, .git, .gradle, or node_modules are included")
    fun `no artifact files included`() {
        val artifactPaths = discoveredFiles.filter { f ->
            f.filePath.contains("/build/") || f.filePath.contains("/.git/") ||
                f.filePath.contains("/.gradle/") || f.filePath.contains("/node_modules/")
        }
        assert(artifactPaths.isEmpty()) {
            "Found ${artifactPaths.size} artifact file(s) in discovered list: " +
                artifactPaths.joinToString { it.filePath }
        }
        log.info("No artifact files (build/.git/.gradle/node_modules) in the discovered list")
    }

    @When("I tokenize all dataset files into sentence-level chunks")
    fun `tokenize all files including adoc`() {
        val datasetDir = java.io.File(ctx.DATASETS_DIR)
        val files = datasetDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".kt") || f.name.endsWith(".adoc"))
        }?.sortedBy { it.name } ?: throw AssertionError("No files found in ${ctx.DATASETS_DIR}")

        ctx.fileChunks.clear()
        for (file in files) {
            val text = file.readText()
            val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(text)
            ctx.fileChunks[file.name] = chunks
            log.info("${file.name}: ${chunks.size} chunk(s) produced")
        }
        val total = ctx.fileChunks.values.sumOf { it.size }
        log.info("Total chunks across all files: $total")
    }

    @Then("the top result is from an AsciiDoc documentation file")
    fun `top result is from adoc file`() {
        val top = ctx.topResults.first()
        assert(top.text.contains(".adoc")) {
            "Top result is not from an adoc file. Text: ${top.text.take(200)}"
        }
        log.info("Top result confirmed from AsciiDoc file: similarity=${"%.4f".format(top.similarity)}")
    }

    @Then("the top result is from a Kotlin source file")
    fun `top result is from kt file`() {
        val top = ctx.topResults.first()
        assert(top.text.contains(".kt")) {
            "Top result is not from a Kotlin source file. Text: ${top.text.take(200)}"
        }
        log.info("Top result confirmed from Kotlin source: similarity=${"%.4f".format(top.similarity)}")
    }
}
