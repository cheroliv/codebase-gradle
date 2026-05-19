package codebase.scenarios

import codebase.koog.ToolRegistry
import codebase.koog.VibecodingGraph
import codebase.koog.VibecodingState
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VibecodingTaskSteps(private val world: VibecodingTaskWorld) {

    private var project: Project? = null

    @Given("a VibecodingTask World is initialized")
    fun `vibecoding task world is initialized`() {
        assertTrue(world.workspaceRoot.isDirectory, "Workspace root should be a directory")
    }

    @When("I evaluate the project")
    fun `evaluate the project`() {
        project = ProjectBuilder.builder()
            .withProjectDir(world.workspaceRoot)
            .build()
        project!!.plugins.apply("education.cccp.codebase")
        val task = project!!.tasks.findByName("vibecode")
        world.taskRegistered = task != null
        world.taskType = task?.javaClass?.simpleName ?: ""
    }

    @Then("the task {string} is registered")
    fun `task is registered`(taskName: String) {
        assertTrue(world.taskRegistered, "Task '$taskName' should be registered")
    }

    @Then("the task type is {string}")
    fun `task type is`(expectedType: String) {
        assertTrue(
            world.taskType.contains(expectedType, ignoreCase = true),
            "Task type should contain '$expectedType', got: '${world.taskType}'"
        )
    }

    @When("I execute the Gradle task {string} with dryRun")
    fun `execute gradle task with dry run`(taskName: String) {
        project = ProjectBuilder.builder()
            .withProjectDir(world.workspaceRoot)
            .build()
        project!!.plugins.apply("education.cccp.codebase")

        val task = project!!.tasks.getByName("vibecode") as codebase.koog.VibecodingTask
        task.intention.set("test intention with dryRun")
        task.dryRun.set(true)
        task.workspaceRoot.set(world.workspaceRoot)

        try {
            task.executeVibecoding()
            world.taskCompleted = true
            world.taskFailed = false
        } catch (e: Exception) {
            world.taskCompleted = false
            world.taskFailed = true
            world.failureMessage = e.message ?: "unknown error"
        }

        val auditFile = world.workspaceRoot.resolve("build/vibecoding/audit.jsonl")
        if (auditFile.exists()) {
            world.auditTrailFile = auditFile
            world.auditTrailContent = auditFile.readText()
        }
    }

    @Then("the task completes successfully")
    fun `task completes successfully`() {
        assertTrue(world.taskCompleted, "Task should complete successfully, but failed: ${world.failureMessage}")
    }

    @Then("the audit trail file exists")
    fun `audit trail file exists`() {
        assertNotNull(world.auditTrailFile, "Audit trail file should exist")
        assertTrue(world.auditTrailFile!!.exists(), "Audit trail file should exist on disk")
    }

    @Then("the audit trail contains {string}")
    fun `audit trail contains`(expected: String) {
        assertTrue(
            world.auditTrailContent.contains(expected),
            "Audit trail should contain '$expected', got: ${world.auditTrailContent}"
        )
    }

    @Then("the audit trail is a valid JSONL file")
    fun `audit trail is valid JSONL`() {
        val lines = world.auditTrailContent.trim().lines().filter { it.isNotBlank() }
        assertTrue(lines.isNotEmpty(), "Audit trail should have at least one line")
        for (line in lines) {
            assertTrue(
                line.startsWith("{") && line.endsWith("}"),
                "Each audit line should be a JSON object, got: $line"
            )
        }
    }

    @Then("each audit line contains {string}")
    fun `each audit line contains field`(fieldName: String) {
        val lines = world.auditTrailContent.trim().lines().filter { it.isNotBlank() }
        for (line in lines) {
            assertTrue(
                line.contains("\"$fieldName\""),
                "Each audit line should contain field '$fieldName', got: $line"
            )
        }
    }

    @When("I create a ToolRegistry")
    fun `create tool registry`() {
        val registry = ToolRegistry()
        world.toolCount = registry.toolCount()
        world.toolNames = registry.toolNames()
    }

    @Then("the registry contains tool {string}")
    fun `registry contains tool`(toolName: String) {
        assertTrue(
            world.toolNames.contains(toolName),
            "ToolRegistry should contain '$toolName'. Available tools: ${world.toolNames}"
        )
    }

    @When("I execute the VibecodingGraph with maxActions {int}")
    fun `execute vibecoding graph with maxActions`(maxActions: Int) {
        val graph = VibecodingGraph()
        val state = VibecodingState(
            intention = "test bounded iterations",
            workspaceRoot = "/tmp/test",
            dryRun = true,
            maxActions = maxActions
        )
        val result = graph.execute(state)
        world.iterationCount = result.iteration
        world.stateFinished = result.finished
    }

    @Then("the iteration count does not exceed {int}")
    fun `iteration count does not exceed`(max: Int) {
        assertTrue(
            world.iterationCount <= max,
            "Iteration count ${world.iterationCount} should not exceed $max"
        )
    }

    @Then("the state is marked as finished")
    fun `state is marked as finished`() {
        assertTrue(world.stateFinished, "VibecodingState should be finished")
    }

    @When("I create a VibecodingState with dryRun true")
    fun `create vibecoding state with dryRun true`() {
        world.iterationCount = 0
        world.stateFinished = false
        val state = VibecodingState(
            intention = "test dryRun",
            workspaceRoot = "/tmp/test",
            dryRun = true
        )
        world.taskCompleted = state.dryRun
    }

    @Then("the state dryRun is true")
    fun `state dryRun is true`() {
        assertTrue(world.taskCompleted, "VibecodingState dryRun should be true")
    }

    @When("I execute the {string} tool with path {string} containing {string}")
    fun `execute tool with path containing`(toolName: String, path: String, content: String) {
        val toolDir = java.io.File("/tmp/vibecoding-cucumber-tool-${System.currentTimeMillis()}")
        toolDir.mkdirs()
        try {
            val testFile = java.io.File(toolDir, path)
            testFile.writeText(content)
            val registry = ToolRegistry()
            world.auditTrailContent = registry.execute(toolName, mapOf("path" to path), toolDir.absolutePath)
        } finally {
            toolDir.deleteRecursively()
        }
    }

    @Then("the tool output contains {string}")
    fun `tool output contains`(expected: String) {
        val source = if (world.lastOutput.isNotEmpty()) world.lastOutput else world.auditTrailContent
        assertTrue(
            source.contains(expected),
            "Tool output should contain '$expected', got: $source"
        )
    }

    @When("I v3-execute the {string} tool with path {string} in workspace {string}")
    fun `v3 execute tool with path in workspace`(toolName: String, path: String, workspace: String) {
        try {
            world.lastOutput = world.registry.execute(
                toolName,
                mapOf("path" to path),
                workspace
            )
            world.rejectionMessage = ""
        } catch (e: SecurityException) {
            world.lastOutput = ""
            world.rejectionMessage = e.message ?: "SecurityException"
        } catch (e: Exception) {
            world.lastOutput = ""
            world.rejectionMessage = e.message ?: "Exception"
        }
    }

    @Then("the v3-tool-execution is rejected")
    fun `v3 tool execution is rejected`() {
        assertTrue(world.rejectionMessage.isNotEmpty(), "Tool execution should be rejected")
    }

    @Then("the v3-rejection contains {string}")
    fun `v3 rejection message contains`(expected: String) {
        assertTrue(
            world.rejectionMessage.contains(expected),
            "Rejection message should contain '$expected', got: ${world.rejectionMessage}"
        )
    }

    @When("I v3-execute the {string} tool with path {string} containing {string} in workspace temp")
    fun `v3 execute tool with path containing in workspace temp`(toolName: String, path: String, content: String) {
        val tempDir = File("/tmp/vibecoding-cuc-sec-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        world.tempWorkspace = tempDir
        val testFile = File(tempDir, path)
        testFile.writeText(content)
        world.lastOutput = world.registry.execute(toolName, mapOf("path" to path), tempDir.absolutePath)
    }

    @Then("the v3-tool-execution succeeds")
    fun `v3 tool execution succeeds`() {
        assertTrue(world.lastOutput.isNotEmpty(), "Tool execution should succeed with output")
    }

    @Then("the v3-tool-output contains {string}")
    fun `v3 tool output contains`(expected: String) {
        assertTrue(
            world.lastOutput.contains(expected),
            "Tool output should contain '$expected', got: ${world.lastOutput}"
        )
    }

    @When("I v3-execute the {string} tool with dryRun and path {string} content {string}")
    fun `v3 execute tool with dryRun and path content`(toolName: String, path: String, content: String) {
        val tempDir = File("/tmp/vibecoding-cuc-dryrun-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        world.tempWorkspace = tempDir
        world.lastOutput = world.registry.execute(
            toolName,
            mapOf("path" to path, "content" to content),
            tempDir.absolutePath,
            dryRun = true
        )
    }

    @When("I v3-execute the {string} tool with dryRun on {string} replacing {string} with {string}")
    fun `v3 execute edit tool with dryRun`(toolName: String, path: String, oldStr: String, newStr: String) {
        world.lastOutput = world.registry.execute(
            toolName,
            mapOf("path" to path, "oldString" to oldStr, "newString" to newStr),
            world.tempWorkspace!!.absolutePath,
            dryRun = true
        )
    }

    @When("I v3-execute the {string} tool with dryRun and command {string}")
    fun `v3 execute exec_shell with dryRun and command`(toolName: String, command: String) {
        val tempDir = File("/tmp/vibecoding-cuc-dryrun-shell-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        world.tempWorkspace = tempDir
        world.lastOutput = world.registry.execute(
            toolName,
            mapOf("command" to command),
            tempDir.absolutePath,
            dryRun = true
        )
    }

    @Then("the v3-tool-output does not contain {string}")
    fun `v3 tool output does not contain`(excluded: String) {
        assertTrue(
            !world.lastOutput.contains(excluded),
            "Tool output should NOT contain '$excluded', got: ${world.lastOutput}"
        )
    }

    @When("I v3-execute the {string} tool with dryRun and task {string}")
    fun `v3 execute exec_gradle with dryRun and task`(toolName: String, task: String) {
        val tempDir = File("/tmp/vibecoding-cuc-dryrun-gradle-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        world.tempWorkspace = tempDir
        world.lastOutput = world.registry.execute(
            toolName,
            mapOf("task" to task),
            tempDir.absolutePath,
            dryRun = true
        )
    }

    @When("I v3-execute tool {string} on {string} with content {string}")
    fun `v3 execute tool on file with content`(toolName: String, path: String, content: String) {
        val tempDir = File("/tmp/vibecoding-cuc-audit-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        world.tempWorkspace = tempDir
        val testFile = File(tempDir, path)
        testFile.writeText(content)
        world.registry.execute(toolName, mapOf("path" to path), tempDir.absolutePath)
    }

    @When("I v3-execute tool {string} with path {string}")
    fun `v3 execute tool list_directory with path`(toolName: String, path: String) {
        val tempDir = File("/tmp/vibecoding-cuc-audit-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        world.tempWorkspace = tempDir
        world.registry.execute(toolName, mapOf("path" to path), tempDir.absolutePath)
    }

    @When("I v3-execute tool {string}")
    fun `v3 execute tool exit`(toolName: String) {
        world.registry.execute(toolName, emptyMap(), "/tmp")
    }

    @When("I v3-execute the {string} tool with path {string} containing {int} chars")
    fun `v3 execute tool with big content`(toolName: String, path: String, charCount: Int) {
        val tempDir = File("/tmp/vibecoding-cuc-audit-big-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        world.tempWorkspace = tempDir
        val bigContent = "x".repeat(charCount)
        val testFile = File(tempDir, path)
        testFile.writeText(bigContent)
        world.registry.clearAudit()
        world.registry.execute(toolName, mapOf("path" to path), tempDir.absolutePath)
        world.auditEntries = world.registry.auditEntries()
    }

    @Then("the file {string} does not exist on disk")
    fun `file does not exist on disk`(fileName: String) {
        val file = File(world.tempWorkspace, fileName)
        assertTrue(!file.exists(), "File '$fileName' should NOT exist on disk (dryRun)")
    }

    @Given("a file {string} with content {string}")
    fun `file with content`(fileName: String, content: String) {
        val tempDir = File("/tmp/vibecoding-cuc-edit-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        world.tempWorkspace = tempDir
        val file = File(tempDir, fileName)
        file.writeText(content)
    }

    @Then("the file {string} still contains {string}")
    fun `file still contains`(fileName: String, expected: String) {
        val file = File(world.tempWorkspace, fileName)
        assertTrue(file.exists(), "File should exist")
        assertTrue(file.readText().contains(expected), "File should still contain '$expected'")
    }

    @Given("the audit trail is cleared")
    fun `audit trail is cleared`() {
        world.registry.clearAudit()
    }

    @Then("the audit trail has {int} entries")
    fun `audit trail has entries`(count: Int) {
        world.auditEntries = world.registry.auditEntries()
        assertEquals(count, world.auditEntries.size, "Audit trail should have $count entries, got ${world.auditEntries.size}")
    }

    @Then("the audit trail has {int} entry")
    fun `audit trail has entry`(count: Int) {
        world.auditEntries = world.registry.auditEntries()
        assertEquals(count, world.auditEntries.size, "Audit trail should have $count entry, got ${world.auditEntries.size}")
    }

    @Then("the audit entry {int} has tool {string}")
    fun `audit entry has tool`(index: Int, expectedTool: String) {
        assertTrue(
            index < world.auditEntries.size,
            "Index $index out of bounds (${world.auditEntries.size} entries)"
        )
        assertEquals(expectedTool, world.auditEntries[index].tool, "Expected tool '${expectedTool}' at index $index")
    }

    @Then("the audit entry has error")
    fun `audit entry has error`() {
        assertTrue(
            world.auditEntries.isNotEmpty(),
            "Audit trail should have at least 1 entry"
        )
        assertTrue(
            world.auditEntries[0].error != null,
            "First audit entry should have an error"
        )
    }

    @Then("the audit entry result is at most {int} characters")
    fun `audit entry result truncated`(maxChars: Int) {
        assertTrue(world.auditEntries.isNotEmpty(), "Should have at least 1 entry")
        assertTrue(
            world.auditEntries[0].result.length <= maxChars,
            "Audit result should be ≤ $maxChars chars, got ${world.auditEntries[0].result.length}"
        )
    }

    private fun assertEquals(expected: Any, actual: Any, message: String) {
        kotlin.test.assertEquals(expected, actual, message)
    }
}
