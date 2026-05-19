package codebase.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VibecodingToolSteps(private val world: VibecodingToolWorld) {

    @Given("a VibecodingTool World is initialized")
    fun `vibecoding tool world is initialized`() {
        assertNotNull(world.workspaceRoot, "Workspace root should be initialized")
        assertTrue(world.workspaceRoot.isDirectory, "Workspace root should be a directory")
    }

    @When("I execute ExecShellTool with command {string}")
    fun `execute exec shell tool`(command: String) {
        try {
            world.shellResult = executeShellCommand(command, world.workspaceRoot)
            world.shellRejected = false
        } catch (e: IllegalArgumentException) {
            world.shellRejected = true
            world.rejectionMessage = e.message ?: ""
        } catch (e: SecurityException) {
            world.shellRejected = true
            world.rejectionMessage = e.message ?: ""
        }
    }

    @When("I execute ExecShellTool with command {string} and timeout {int} milliseconds")
    fun `execute exec shell tool with timeout`(command: String, timeoutMs: Int) {
        try {
            world.shellResult = executeShellCommandWithTimeout(command, world.workspaceRoot, timeoutMs)
            world.shellRejected = false
        } catch (e: SecurityException) {
            world.shellRejected = true
            world.rejectionMessage = e.message ?: ""
        }
    }

    @When("I execute ExecGradleTool with task {string}")
    fun `execute exec gradle tool`(task: String) {
        try {
            codebase.koog.tools.ExecGradleTool.validateGradleTask(task)
            world.gradleResult = codebase.koog.tools.ExecGradleTool.executeBlocking(task, System.getProperty("user.dir"))
            world.gradleRejected = false
        } catch (e: SecurityException) {
            world.gradleRejected = true
            world.gradleRejectionMessage = e.message ?: ""
        }
    }

    @Then("the shell exit code is {int}")
    fun `shell exit code is`(expectedExitCode: Int) {
        assertTrue(!world.shellRejected, "Shell command should not be rejected")
        assertTrue(world.shellResult.contains("EXIT: $expectedExitCode"),
            "Expected exit code $expectedExitCode in result: $world.shellResult")
    }

    @Then("the output contains {string}")
    fun `output contains`(expected: String) {
        val output = if (!world.shellRejected) world.shellResult else ""
        assertTrue(output.contains(expected),
            "Output should contain '$expected', got: $output")
    }

    @Then("the shell command is rejected")
    fun `shell command is rejected`() {
        assertTrue(world.shellRejected, "Shell command should be rejected")
    }

    @Then("the rejection message contains {string}")
    fun `rejection message contains`(expected: String) {
        val msg = if (world.gradleRejected && world.gradleRejectionMessage.isNotEmpty())
            world.gradleRejectionMessage else world.rejectionMessage
        assertTrue(
            msg.contains(expected, ignoreCase = true),
            "Rejection message should contain '$expected', got: '$msg'"
        )
    }

    @Then("the gradle exit code is {int}")
    fun `gradle exit code is`(expectedExitCode: Int) {
        assertTrue(!world.gradleRejected, "Gradle task should not be rejected")
        assertTrue(world.gradleResult.contains("EXIT: $expectedExitCode"),
            "Expected gradle exit code $expectedExitCode in result: $world.gradleResult")
    }

    @Then("the gradle task is rejected")
    fun `gradle task is rejected`() {
        assertTrue(world.gradleRejected, "Gradle task should be rejected")
    }

    private fun executeShellCommand(command: String, workspaceRoot: java.io.File): String {
        return codebase.koog.tools.ExecShellTool.executeBlocking(command, workspaceRoot.absolutePath)
    }

    private fun executeShellCommandWithTimeout(command: String, workspaceRoot: java.io.File, timeoutMs: Int): String {
        return codebase.koog.tools.ExecShellTool.executeBlocking(command, workspaceRoot.absolutePath, timeoutMs)
    }

    private fun executeGradleTask(task: String, workspaceRoot: java.io.File): String {
        return codebase.koog.tools.ExecGradleTool.executeBlocking(task, System.getProperty("user.dir"))
    }

    private fun executeGradleValidate(task: String) {
        codebase.koog.tools.ExecGradleTool.validateGradleTask(task)
    }
}
