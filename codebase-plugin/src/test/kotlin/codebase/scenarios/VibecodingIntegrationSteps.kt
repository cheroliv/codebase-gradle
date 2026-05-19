package codebase.scenarios

import codebase.koog.ToolRegistry
import codebase.koog.VibecodingGraph
import codebase.koog.VibecodingState
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VibecodingIntegrationSteps(private val world: VibecodingIntegrationWorld) {

    @Given("a temporary Gradle project with Kotlin source files")
    fun `temporary gradle project with Kotlin source files`() {
        val tmp = File("/tmp/vibecoding-integration-${System.currentTimeMillis()}")
        tmp.mkdirs()
        world.tempProjectDir = tmp

        File(tmp, "build.gradle.kts").writeText("""
            plugins { kotlin("jvm") version "2.1.0" }
            group = "com.test"
        """.trimIndent())

        File(tmp, "settings.gradle.kts").writeText("rootProject.name = \"vibe-test\"")

        val src = File(tmp, "src/main/kotlin/com/test")
        src.mkdirs()

        val mainFile = File(src, "Main.kt")
        mainFile.writeText("package com.test\n\nfun main() = println(\"Hello World\")")
        world.sourceFiles["src/main/kotlin/com/test/Main.kt"] = mainFile.readText()
        world.originalContents["src/main/kotlin/com/test/Main.kt"] = mainFile.readText()

        val utilFile = File(src, "Util.kt")
        utilFile.writeText("package com.test\n\nfun add(a: Int, b: Int) = a + b")
        world.sourceFiles["src/main/kotlin/com/test/Util.kt"] = utilFile.readText()
        world.originalContents["src/main/kotlin/com/test/Util.kt"] = utilFile.readText()
    }

    @Given("the vibecode project has a .env secret file")
    fun `vibecode project has env secret file`() {
        val envFile = File(world.tempProjectDir, ".env")
        envFile.writeText("SECRET_KEY=abc123\nAPI_TOKEN=xyz789")
        world.sourceFiles[".env"] = envFile.readText()
        world.originalContents[".env"] = envFile.readText()
    }

    @When("I execute the vibecode task with intention {string} and dryRun true")
    fun `execute vibecode task with intention and dryRun true`(intention: String) {
        val project = ProjectBuilder.builder()
            .withProjectDir(world.tempProjectDir)
            .build()
        project.plugins.apply("education.cccp.codebase")

        val task = project.tasks.getByName("vibecode") as codebase.koog.VibecodingTask
        task.intention.set(intention)
        task.dryRun.set(true)
        task.workspaceRoot.set(world.tempProjectDir)

        try {
            task.executeVibecoding()
            world.taskCompleted = true
            world.taskError = null
        } catch (e: Exception) {
            world.taskCompleted = false
            world.taskError = e.message
        }

        val auditFile = File(world.tempProjectDir, "build/vibecoding/audit.jsonl")
        if (auditFile.exists()) {
            world.auditTrailFile = auditFile
            world.auditTrailContent = auditFile.readText()
        }
    }

    @When("I execute a non-dryRun write_file to {string} with content {string}")
    fun `execute non-dryRun write_file`(path: String, content: String) {
        world.registry.clearAudit()
        world.registry.execute(
            "write_file",
            mapOf("path" to path, "content" to content),
            world.tempProjectDir.absolutePath,
            dryRun = false
        )
        world.generatedFile = File(world.tempProjectDir, path)
        world.auditEntries = world.registry.auditEntries()
    }

    @Then("the vibecode task completes without error")
    fun `vibecode task completes without error`() {
        assertTrue(world.taskCompleted, "Task should complete without error, got: ${world.taskError}")
    }

    @Then("the audit trail file {string} exists")
    fun `audit trail file exists`(relativePath: String) {
        val file = File(world.tempProjectDir, relativePath)
        world.auditTrailFile = file
        assertTrue(file.exists(), "Audit trail file $relativePath should exist")
    }

    @Then("the v4-audit trail contains text {string}")
    fun `v4 audit trail contains text`(expected: String) {
        assertTrue(
            world.auditTrailContent.contains(expected),
            "Audit should contain '$expected', got: ${world.auditTrailContent}"
        )
    }

    @Then("each audit line is valid JSON")
    fun `each audit line is valid JSON`() {
        val lines = world.auditTrailContent.trim().lines().filter { it.isNotBlank() }
        assertTrue(lines.isNotEmpty(), "Audit trail should have at least one line")
        for (line in lines) {
            assertTrue(
                line.startsWith("{") && line.endsWith("}"),
                "Each audit line should be a JSON object, got: $line"
            )
        }
    }

    @Then("the v4-audit trail content is non-empty")
    fun `v4 audit trail content is non-empty`() {
        assertTrue(world.auditTrailContent.isNotBlank(), "Audit trail should have content")
    }

    @Then("all project source files are unchanged")
    fun `all project source files are unchanged`() {
        for ((relativePath, originalContent) in world.originalContents) {
            val file = File(world.tempProjectDir, relativePath)
            assertTrue(file.exists(), "Source file $relativePath should still exist")
            assertEquals(originalContent, file.readText(), "Source file $relativePath should be unchanged")
        }
    }

    @When("the ToolRegistry attempts {string} path traversal in the project")
    fun `toolRegistry attempts path traversal`(traversalPath: String) {
        try {
            world.registry.execute(
                "read_file",
                mapOf("path" to traversalPath),
                world.tempProjectDir.absolutePath
            )
            world.rejectionMessage = ""
        } catch (e: SecurityException) {
            world.rejectionMessage = e.message ?: "SecurityException"
        } catch (e: Exception) {
            world.rejectionMessage = e.message ?: "Exception"
        }
    }

    @Then("the v4-path traversal request is rejected")
    fun `v4 path traversal is rejected`() {
        assertTrue(world.rejectionMessage.isNotEmpty(), "Path traversal should be rejected")
        assertTrue(
            world.rejectionMessage.contains("Path traversal"),
            "Rejection message should mention path traversal, got: ${world.rejectionMessage}"
        )
    }

    @Then("the v4-audit trail has at least {int} entries")
    fun `v4 audit trail has at least entries`(minCount: Int) {
        val lines = world.auditTrailContent.trim().lines().filter { it.isNotBlank() }
        assertTrue(lines.size >= minCount, "Expected at least $minCount audit entries, got ${lines.size}")
    }

    @Then("the v4-audit entry at index {int} has field {string}")
    fun `v4 audit entry at index has field`(index: Int, fieldName: String) {
        val lines = world.auditTrailContent.trim().lines().filter { it.isNotBlank() }
        assertTrue(index < lines.size, "Index $index out of bounds (${lines.size} lines)")
        assertTrue(
            lines[index].contains("\"$fieldName\""),
            "Audit line $index should contain field '$fieldName', got: ${lines[index]}"
        )
    }

    @Then("the v4-audit entry at index {int} has field {string} set to true")
    fun `v4 audit entry at index has field set to true`(index: Int, fieldName: String) {
        val lines = world.auditTrailContent.trim().lines().filter { it.isNotBlank() }
        assertTrue(index < lines.size, "Index $index out of bounds (${lines.size} lines)")
        assertTrue(
            lines[index].contains("\"$fieldName\":true"),
            "Audit line $index should have '\"$fieldName\":true', got: ${lines[index]}"
        )
    }

    @Then("the file {string} exists in the project")
    fun `file exists in project`(relativePath: String) {
        val file = File(world.tempProjectDir, relativePath)
        assertTrue(file.exists(), "File $relativePath should exist in the project")
    }

    @Then("the v4-generated file contains {string}")
    fun `file contains`(expected: String) {
        assertTrue(
            world.generatedFile.readText().contains(expected),
            "File should contain '$expected', got: ${world.generatedFile.readText()}"
        )
    }

    @Then("the v4-audit trail has {int} entry with tool {string}")
    fun `v4 audit trail has entry with tool`(count: Int, expectedTool: String) {
        assertEquals(count, world.auditEntries.size, "Should have $count entries, got ${world.auditEntries.size}")
        if (count > 0) {
            assertEquals(expectedTool, world.auditEntries[0].tool, "Tool should be '$expectedTool'")
        }
    }
}
