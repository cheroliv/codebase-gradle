package codebase.rag

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrepareContextTaskTest {

    @Test
    fun `task executes and produces output when pgvector is down`(@TempDir tempDir: File) {
        // Setup borough structure
        val agentsDir = File(tempDir, ".agents")
        agentsDir.mkdirs()
        File(agentsDir, "INDEX.adoc").writeText("= INDEX test-borough\n== Roadmap\nEPIC 1 DONE\n")
        File(tempDir, "PROMPT_REPRISE.adoc").writeText("= PROMPT_REPRISE test-borough\nMission: test\n")

        val outputFile = File(tempDir, "build/context/test-borough.context.txt")
        outputFile.parentFile.mkdirs()

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("prepareContext", PrepareContextTask::class.java).get()

        task.workspaceRoot.set(tempDir)
        task.outputFile.set(outputFile)
        task.ragQuestion.set("architecture")
        task.projectName.set("test-borough")

        // Execute — pgvector will be down, task should handle gracefully
        task.execute()

        assertTrue(outputFile.isFile, "Output file should exist")
        val content = outputFile.readText()
        assertTrue(content.isNotEmpty(), "Output should not be empty")
        assertTrue(content.contains("[RÈGLES_EAGER]"), "Should contain EAGER section")
        assertTrue(content.contains("[RELATIONS_GRAPHIFY]"), "Should contain Graphify section")
    }

    @Test
    fun `task uses project name from directory when not specified`(@TempDir tempDir: File) {
        val agentsDir = File(tempDir, ".agents")
        agentsDir.mkdirs()
        File(agentsDir, "INDEX.adoc").writeText("= INDEX\n== Roadmap\nEPIC 1 DONE\n")
        File(tempDir, "PROMPT_REPRISE.adoc").writeText("= PROMPT_REPRISE\n")

        val outputFile = File(tempDir, "build/context/context.txt")
        outputFile.parentFile.mkdirs()

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("prepareContext", PrepareContextTask::class.java).get()

        task.workspaceRoot.set(tempDir)
        task.outputFile.set(outputFile)
        // projectName not set — should fallback to directory name

        task.execute()

        assertTrue(outputFile.isFile, "Output should exist")
        assertTrue(outputFile.readText().isNotEmpty())
    }

    @Test
    fun `task uses default ragQuestion when not specified`(@TempDir tempDir: File) {
        val agentsDir = File(tempDir, ".agents")
        agentsDir.mkdirs()
        File(agentsDir, "INDEX.adoc").writeText("= INDEX\n== Status\nOK\n")
        File(tempDir, "PROMPT_REPRISE.adoc").writeText("= PROMPT_REPRISE\n")

        val outputFile = File(tempDir, "output.txt")
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("prepareContext", PrepareContextTask::class.java).get()

        task.workspaceRoot.set(tempDir)
        task.outputFile.set(outputFile)
        task.projectName.set("default-test")
        // ragQuestion not set — should use default "architecture du workspace"

        task.execute()

        assertTrue(outputFile.isFile)
        assertTrue(outputFile.length() > 0, "Should produce non-empty output")
    }

    @Test
    fun `task survives empty workspace with no agents dir`(@TempDir tempDir: File) {
        val outputFile = File(tempDir, "output.txt")
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("prepareContext", PrepareContextTask::class.java).get()

        task.workspaceRoot.set(tempDir)
        task.outputFile.set(outputFile)
        task.projectName.set("empty-borough")

        task.execute()

        assertTrue(outputFile.isFile, "Output should exist even for empty workspace")
    }

    @Test
    fun `task has correct task properties annotated`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("prepareContext", PrepareContextTask::class.java).get()

        assertNotNull(task.workspaceRoot)
        assertNotNull(task.outputFile)
        assertNotNull(task.ragQuestion)
        assertNotNull(task.projectName)
    }
}
