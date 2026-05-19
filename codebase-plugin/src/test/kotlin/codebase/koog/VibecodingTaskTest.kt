package codebase.koog

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VibecodingTaskTest {

    @Test
    fun `ToolRegistry contains 7 tools`() {
        val registry = ToolRegistry()
        assertEquals(7, registry.toolCount(), "ToolRegistry should contain 7 tools")
    }

    @Test
    fun `ToolRegistry contains required tool names`() {
        val registry = ToolRegistry()
        val names = registry.toolNames()
        assertTrue(names.contains("read_file"), "Should contain read_file")
        assertTrue(names.contains("write_file"), "Should contain write_file")
        assertTrue(names.contains("edit_file"), "Should contain edit_file")
        assertTrue(names.contains("list_directory"), "Should contain list_directory")
        assertTrue(names.contains("exit"), "Should contain exit")
        assertTrue(names.contains("exec_shell"), "Should contain exec_shell")
        assertTrue(names.contains("exec_gradle"), "Should contain exec_gradle")
    }

    @Test
    fun `read_file tool works`() {
        val projectDir = File("/tmp/vibecoding-tool-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val testFile = File(projectDir, "test.txt")
            testFile.writeText("hello vibecoding")
            val registry = ToolRegistry()
            val result = registry.execute("read_file", mapOf("path" to "test.txt"), projectDir.absolutePath)
            assertTrue(result.contains("hello vibecoding"), "read_file should return file content")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `write_file tool works`() {
        val projectDir = File("/tmp/vibecoding-write-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val registry = ToolRegistry()
            val result = registry.execute(
                "write_file",
                mapOf("path" to "output.txt", "content" to "generated content"),
                projectDir.absolutePath
            )
            val writtenFile = File(projectDir, "output.txt")
            assertTrue(writtenFile.exists(), "File should be created")
            assertEquals("generated content", writtenFile.readText())
            assertTrue(result.contains("File written"), "Result should confirm write")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `edit_file tool works`() {
        val projectDir = File("/tmp/vibecoding-edit-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val testFile = File(projectDir, "edit-me.txt")
            testFile.writeText("Hello World")
            val registry = ToolRegistry()
            val result = registry.execute(
                "edit_file",
                mapOf("path" to "edit-me.txt", "oldString" to "Hello World", "newString" to "Hello Vibecoding"),
                projectDir.absolutePath
            )
            assertEquals("Hello Vibecoding", testFile.readText(), "File should be edited")
            assertTrue(result.contains("File edited"), "Result should confirm edit")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `list_directory tool works`() {
        val projectDir = File("/tmp/vibecoding-list-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            File(projectDir, "foo.txt").writeText("foo")
            File(projectDir, "bar.txt").writeText("bar")
            val registry = ToolRegistry()
            val result = registry.execute("list_directory", mapOf("path" to "."), projectDir.absolutePath)
            assertTrue(result.contains("foo.txt"), "Should list foo.txt")
            assertTrue(result.contains("bar.txt"), "Should list bar.txt")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `exit tool terminates loop`() {
        val registry = ToolRegistry()
        val result = registry.execute("exit", emptyMap(), "/tmp")
        assertTrue(result.contains("terminated"), "Exit should terminate the loop")
    }

    @Test
    fun `unknown tool throws exception`() {
        val registry = ToolRegistry()
        try {
            registry.execute("nonexistent", emptyMap(), "/tmp")
            assertTrue(false, "Should have thrown")
        } catch (e: Exception) {
            assertTrue(e is codebase.koog.tools.ToolkitIsMissingException, "Should be ToolkitIsMissingException")
        }
    }

    @Test
    fun `VibecodingState has correct defaults`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp"
        )
        assertEquals("test", state.intention)
        assertEquals("/tmp", state.workspaceRoot)
        assertEquals(false, state.dryRun)
        assertEquals(10, state.maxActions)
        assertEquals(0, state.iteration)
        assertEquals(false, state.finished)
    }

    @Test
    fun `VibecodingState maxActions is configurable`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp",
            maxActions = 5
        )
        assertEquals(5, state.maxActions)
    }

    @Test
    fun `VibecodingState dryRun is configurable`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp",
            dryRun = true
        )
        assertEquals(true, state.dryRun)
    }

    @Test
    fun `VibecodingGraph buildSystemPrompt contains required fields`() {
        val graph = VibecodingGraph()
        val state = VibecodingState(
            intention = "Add dark mode toggle",
            workspaceRoot = "/tmp/test",
            dryRun = true,
            maxActions = 5
        )
        val prompt = graph.buildSystemPrompt(state)

        assertTrue(prompt.contains("Intention"), "Prompt must contain 'Intention'")
        assertTrue(prompt.contains("Plan"), "Prompt must contain 'Plan'")
        assertTrue(prompt.contains("WorkspaceRoot"), "Prompt must contain 'WorkspaceRoot'")
        assertTrue(prompt.contains("DryRun"), "Prompt must contain 'DryRun'")
        assertTrue(prompt.contains("Add dark mode toggle"), "Prompt must contain the intention")
        assertTrue(prompt.contains("/tmp/test"), "Prompt must contain the workspaceRoot")
    }

    @Test
    fun `VibecodingGraph executes with dryRun`() {
        val graph = VibecodingGraph()
        val state = VibecodingState(
            intention = "dry run test",
            workspaceRoot = "/tmp/test",
            dryRun = true,
            maxActions = 3
        )
        val result = graph.execute(state)
        assertNotNull(result, "Execute should return non-null state")
    }

    @Test
    fun `VibecodingState isFinal when maxActions reached`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp",
            maxActions = 3,
            iteration = 3
        )
        assertTrue(state.isFinal, "State at iteration 3/3 should be final")
    }

    @Test
    fun `VibecodingState isFinal when finished flag true`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp",
            finished = true
        )
        assertTrue(state.isFinal, "State with finished=true should be final")
    }

    @Test
    fun `VibecodingState withError sets error and finishes`() {
        val state = VibecodingState(
            intention = "test",
            workspaceRoot = "/tmp"
        )
        val errored = state.withError("something went wrong")
        assertEquals("something went wrong", errored.error)
        assertTrue(errored.finished, "State with error should be finished")
    }

    @Test
    fun `VibecodingState nextIteration increments correctly`() {
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp", iteration = 0)
        val next = state.nextIteration()
        assertEquals(1, next.iteration)
    }

    @Test
    fun `dryRun write_file does not write to disk`() {
        val projectDir = File("/tmp/vibecoding-dryrun-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val registry = ToolRegistry()
            val result = registry.execute(
                "write_file",
                mapOf("path" to "should-not-exist.txt", "content" to "ghost content"),
                projectDir.absolutePath,
                dryRun = true
            )
            assertTrue(result.contains("DRY RUN"), "dryRun write_file should return DRY RUN message")
            val ghostFile = File(projectDir, "should-not-exist.txt")
            assertTrue(!ghostFile.exists(), "dryRun write_file must NOT create file on disk")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `dryRun edit_file does not modify file`() {
        val projectDir = File("/tmp/vibecoding-dryrun-edit-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val testFile = File(projectDir, "preserve-me.txt")
            testFile.writeText("original content")
            val registry = ToolRegistry()
            val result = registry.execute(
                "edit_file",
                mapOf("path" to "preserve-me.txt", "oldString" to "original content", "newString" to "modified content"),
                projectDir.absolutePath,
                dryRun = true
            )
            assertTrue(result.contains("DRY RUN"), "dryRun edit_file should return DRY RUN message")
            assertEquals("original content", testFile.readText(), "dryRun edit_file must NOT modify file")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `dryRun exec_shell does not execute`() {
        val projectDir = File("/tmp/vibecoding-dryrun-shell-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val registry = ToolRegistry()
            val result = registry.execute(
                "exec_shell",
                mapOf("command" to "echo hello"),
                projectDir.absolutePath,
                dryRun = true
            )
            assertTrue(result.contains("DRY RUN"), "dryRun exec_shell should return DRY RUN message")
            assertTrue(!result.contains("EXIT:"), "dryRun exec_shell must NOT execute command")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `dryRun exec_gradle does not execute`() {
        val projectDir = File("/tmp/vibecoding-dryrun-gradle-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val registry = ToolRegistry()
            val result = registry.execute(
                "exec_gradle",
                mapOf("task" to "compileKotlin"),
                projectDir.absolutePath,
                dryRun = true
            )
            assertTrue(result.contains("DRY RUN"), "dryRun exec_gradle should return DRY RUN message")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `sandbox blocks path traversal with double dots`() {
        val registry = ToolRegistry()
        try {
            registry.execute(
                "read_file",
                mapOf("path" to "../../etc/passwd"),
                "/tmp/vibecoding-sandbox"
            )
            assertTrue(false, "Should have thrown SecurityException for path traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Path traversal blocked"), "Should be path traversal")
        }
    }

    @Test
    fun `sandbox blocks absolute path outside workspaceRoot`() {
        val registry = ToolRegistry()
        try {
            registry.execute(
                "read_file",
                mapOf("path" to "/etc/passwd"),
                "/tmp/vibecoding-sandbox"
            )
            assertTrue(false, "Should have thrown SecurityException for absolute path outside root")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("Path traversal blocked"), "Should be path traversal")
        }
    }

    @Test
    fun `sandbox allows files inside workspaceRoot`() {
        val projectDir = File("/tmp/vibecoding-sandbox-ok-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val safeFile = File(projectDir, "safe.txt")
            safeFile.writeText("safe content")
            val registry = ToolRegistry()
            val result = registry.execute(
                "read_file",
                mapOf("path" to "safe.txt"),
                projectDir.absolutePath
            )
            assertTrue(result.contains("safe content"), "Should allow read inside workspaceRoot")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `sandbox allows subdirectory traversal inside workspaceRoot`() {
        val projectDir = File("/tmp/vibecoding-sandbox-nested-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val nestedDir = File(projectDir, "sub/dir")
            nestedDir.mkdirs()
            val nestedFile = File(nestedDir, "deep.txt")
            nestedFile.writeText("deep content")
            val registry = ToolRegistry()
            val result = registry.execute(
                "read_file",
                mapOf("path" to "sub/dir/deep.txt"),
                projectDir.absolutePath
            )
            assertTrue(result.contains("deep content"), "Should allow nested dirs inside workspaceRoot")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `audit trail records successful tool execution`() {
        val projectDir = File("/tmp/vibecoding-audit-test-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val testFile = File(projectDir, "audit-me.txt")
            testFile.writeText("audit content")
            val registry = ToolRegistry()
            registry.clearAudit()
            registry.execute(
                "read_file",
                mapOf("path" to "audit-me.txt"),
                projectDir.absolutePath
            )
            val entries = registry.auditEntries()
            assertEquals(1, entries.size, "Audit trail should have 1 entry")
            assertEquals("read_file", entries[0].tool)
            assertTrue(entries[0].result.contains("audit content"))
            assertTrue(entries[0].error == null, "No error expected")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `audit trail records failed tool execution`() {
        val registry = ToolRegistry()
        registry.clearAudit()
        try {
            registry.execute(
                "read_file",
                mapOf("path" to "nonexistent.txt"),
                "/tmp/vibecoding-audit-error"
            )
            assertTrue(false, "Should have thrown")
        } catch (e: Exception) {
            val entries = registry.auditEntries()
            assertEquals(1, entries.size, "Audit trail should have 1 error entry")
            assertEquals("read_file", entries[0].tool)
            assertTrue(entries[0].error != null, "Should have error message")
        }
    }

    @Test
    fun `audit trail records multiple executions`() {
        val projectDir = File("/tmp/vibecoding-audit-multi-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val testFile = File(projectDir, "multi.txt")
            testFile.writeText("multi")
            val registry = ToolRegistry()
            registry.clearAudit()
            registry.execute("read_file", mapOf("path" to "multi.txt"), projectDir.absolutePath)
            registry.execute("list_directory", mapOf("path" to "."), projectDir.absolutePath)
            registry.execute("exit", emptyMap(), projectDir.absolutePath)
            val entries = registry.auditEntries()
            assertEquals(3, entries.size, "Should have 3 audit entries")
            assertEquals("read_file", entries[0].tool)
            assertEquals("list_directory", entries[1].tool)
            assertEquals("exit", entries[2].tool)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `audit trail truncates result to 500 chars`() {
        val projectDir = File("/tmp/vibecoding-audit-trunc-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val bigContent = "x".repeat(1000)
            val testFile = File(projectDir, "big.txt")
            testFile.writeText(bigContent)
            val registry = ToolRegistry()
            registry.clearAudit()
            registry.execute("read_file", mapOf("path" to "big.txt"), projectDir.absolutePath)
            val entries = registry.auditEntries()
            assertEquals(1, entries.size)
            assertTrue(entries[0].result.length <= 500, "Audit result should be truncated to 500 chars")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `read_file works with dryRun enabled`() {
        val projectDir = File("/tmp/vibecoding-dryrun-read-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val testFile = File(projectDir, "readable.txt")
            testFile.writeText("readable content")
            val registry = ToolRegistry()
            val result = registry.execute(
                "read_file",
                mapOf("path" to "readable.txt"),
                projectDir.absolutePath,
                dryRun = true
            )
            assertTrue(result.contains("readable content"), "read_file should still work in dryRun")
        } finally {
            projectDir.deleteRecursively()
        }
    }
}
