package codebase.koog

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests unitaires pour VibecodingTask — tâche Gradle de vibecoding.
 *
 * Ces tests valident la configuration de la tâche (groupe, description,
 * propriétés) sans exécuter le pipeline complet.
 */
class VibecodingTaskTest {

    @Test
    fun `task should be registered with correct group and description`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java).get()

        assertEquals("vibecode", task.name)
        assertEquals("generate", task.group)
        assertTrue(task.description?.contains("Vibecoding") == true, "Description should mention Vibecoding")
    }

    @Test
    fun `task should have default property values`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java).get()

        assertEquals("", task.intention.get())
        assertFalse(task.dryRun.get())
        assertEquals(10, task.maxActions.get())
    }

    @Test
    fun `task should accept custom property values`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java) {
            it.intention.set("Fix typo in README")
            it.dryRun.set(true)
            it.maxActions.set(5)
        }.get()

        assertEquals("Fix typo in README", task.intention.get())
        assertTrue(task.dryRun.get())
        assertEquals(5, task.maxActions.get())
    }

    @Test
    fun `task should have default session timeout of 300 seconds`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java).get()

        assertEquals(300, task.sessionTimeoutSeconds.get())
    }

    @Test
    fun `task should have resume option defaulting to empty`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java).get()

        assertEquals("", task.resume.get())
    }

    @Test
    fun `task should accept resume option`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java) {
            it.resume.set("session-abc123")
        }.get()

        assertEquals("session-abc123", task.resume.get())
    }

    @Test
    fun `task should accept custom session timeout`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java) {
            it.sessionTimeoutSeconds.set(60)
        }.get()

        assertEquals(60, task.sessionTimeoutSeconds.get())
    }

    @Test
    fun `task should have a toolRegistry`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java).get()

        assertNotNull(task.toolRegistry)
        assertTrue(task.toolRegistry.toolCount() > 0, "ToolRegistry should be initialized")
        assertTrue(task.toolRegistry.toolNames().contains("read_file"), "Should have read_file tool")
        assertTrue(task.toolRegistry.toolNames().contains("exec_shell"), "Should have exec_shell tool")
        assertTrue(task.toolRegistry.toolNames().contains("exec_gradle"), "Should have exec_gradle tool")
    }

    @Test
    fun `execute with dryRun should not fail`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java) {
            it.intention.set("Test dry run")
            it.dryRun.set(true)
            it.maxActions.set(2)
        }.get()

        // La tâche ne doit pas lever d'exception en dryRun
        assertDoesNotThrow {
            task.executeVibecoding()
        }
    }

    @Test
    fun `execution should create audit directory`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java) {
            it.intention.set("Test audit")
            it.dryRun.set(true)
            it.maxActions.set(1)
            it.workspaceRoot.set(tempDir)
        }.get()

        task.executeVibecoding()

        val auditDir = File(tempDir, "build/vibecoding")
        assertTrue(auditDir.exists(), "Audit directory should exist")
        val auditFile = File(auditDir, "audit.jsonl")
        assertTrue(auditFile.exists(), "Audit file should exist")
        assertTrue(auditFile.readText().isNotBlank(), "Audit file should not be blank")
    }

    @Test
    fun `connectionFactory should be nullable and injectable`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java).get()

        assertNull(task.connectionFactory, "Should be null by default")

        // Inject sans crash
        task.connectionFactory = null
        assertNull(task.connectionFactory)
    }

    @Test
    fun `execute should not crash when connectionFactory is null`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java) {
            it.intention.set("Test no CF crash")
            it.dryRun.set(true)
            it.maxActions.set(1)
        }.get()

        // Null par défaut — le path normal doit survivre
        assertNull(task.connectionFactory)
        assertDoesNotThrow { task.executeVibecoding() }
    }

    @Test
    fun `audit file should contain valid JSONL`(@TempDir tempDir: File) {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()
        val task = project.tasks.register("vibecode", VibecodingTask::class.java) {
            it.intention.set("Test JSONL")
            it.dryRun.set(true)
            it.maxActions.set(2)
            it.workspaceRoot.set(tempDir)
        }.get()

        task.executeVibecoding()

        val auditFile = File(tempDir, "build/vibecoding/audit.jsonl")
        val lines = auditFile.readLines()
        assertTrue(lines.isNotEmpty(), "Audit should have at least one line")
        lines.forEach { line ->
            assertTrue(line.startsWith("{"))
            assertTrue(line.contains("\"timestamp\""))
            assertTrue(line.contains("\"dryRun\""))
        }
        assertTrue(lines.any { it.contains("\"intention\"") },
            "At least one audit line must contain the session intention")
    }
}
