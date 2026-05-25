package codebase.koog.tools

import education.cccp.contracts.vibecoding.tools.ExecGradleTool
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ExecGradleToolTest {

    @Test
    fun `valid gradle task passes validation`() {
        ExecGradleTool.validateGradleTask("compileKotlin")
        ExecGradleTool.validateGradleTask("test")
        ExecGradleTool.validateGradleTask("build")
        ExecGradleTool.validateGradleTask("publishToMavenLocal")
    }

    @Test
    fun `clean build is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecGradleTool.validateGradleTask("clean build")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `refresh dependencies is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecGradleTool.validateGradleTask("build --refresh-dependencies")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `rm task is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecGradleTool.validateGradleTask("rm -rf build")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `execute blocking runs gradle task`() {
        val result = ExecGradleTool.executeBlocking(
            task = "tasks",
            workingDir = System.getProperty("user.dir")
        )
        assertTrue(result.contains("GRADLE EXIT: 0"),
            "Expected success exit code: ${result.take(200)}")
    }

    @Test
    fun `async execute returns result`() = runBlocking {
        val result = ExecGradleTool.execute(
            ExecGradleTool.Args(
                task = "tasks",
                workingDir = System.getProperty("user.dir")
            )
        )
        assertTrue(result.startsWith("GRADLE EXIT: 0"),
            "Expected EXIT: 0, got: ${result.take(200)}")
    }
}
