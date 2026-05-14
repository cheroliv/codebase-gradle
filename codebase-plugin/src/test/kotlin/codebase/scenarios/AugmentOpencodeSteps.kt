package codebase.scenarios

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AugmentOpencodeSteps {

    private val log = LoggerFactory.getLogger(AugmentOpencodeSteps::class.java)

    @When("I write the injected output to {string}")
    fun `write injected output to file`(path: String) {
        val injected = OpencodeInjectorSteps.lastInjected
        assertNotNull(injected, "OpencodeInjector has not produced output yet")

        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(injected)
        log.info("Written {} bytes to {}", file.length(), file.absolutePath)
    }

    @Then("the file {string} exists")
    fun `file exists`(path: String) {
        val file = File(path)
        assertTrue(file.isFile, "Expected file '$path' to exist but it does not")
        log.info("File exists: {}", file.absolutePath)
    }

    @Then("the file {string} contains {string}")
    fun `file contains string`(path: String, expected: String) {
        val file = File(path)
        val content = file.readText()
        assertTrue(content.contains(expected),
            "Expected file '$path' to contain '$expected' but it was not found")
        log.info("File contains '{}'", expected)
    }

    @Then("the file {string} is larger than {int} bytes")
    fun `file larger than bytes`(path: String, minSize: Int) {
        val file = File(path)
        assertTrue(file.length() > minSize,
            "Expected file '$path' to be larger than $minSize bytes, actual size: ${file.length()}")
        log.info("File size: {} bytes (min: {})", file.length(), minSize)
    }
}
