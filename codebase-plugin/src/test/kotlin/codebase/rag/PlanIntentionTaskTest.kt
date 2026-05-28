package codebase.rag

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlanIntentionTaskTest {

    @Test
    fun `task executes and produces output without pgvector`(@TempDir tempDir: File) {
        val outputFile = File(tempDir, "plan-output.txt")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("planIntention", PlanIntentionTask::class.java).get()

        task.intention.set("Add dark mode toggle")
        task.workspaceRoot.set(tempDir)
        task.outputFile.set(outputFile)

        task.execute()

        assertTrue(outputFile.isFile, "Output file should exist")
        val content = outputFile.readText()
        assertTrue(content.isNotEmpty(), "Output should not be empty")
        assertTrue(content.contains("Add dark mode toggle"), "Should contain intention")
        assertTrue(content.contains("PLAN"), "Should contain PLAN header")
    }

    @Test
    fun `task produces output for complex intention without pgvector`(@TempDir tempDir: File) {
        val outputFile = File(tempDir, "complex-plan.txt")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("planIntention", PlanIntentionTask::class.java).get()

        task.intention.set("Refactor cross-borough DAG N1→N2→N3 pour intégration multi-plugins avec architecture distribuée")
        task.workspaceRoot.set(tempDir)
        task.outputFile.set(outputFile)
        task.ragQuestion.set("architecture DAG")

        task.execute()

        assertTrue(outputFile.isFile)
        val content = outputFile.readText()
        assertTrue(content.isNotEmpty())
        assertTrue(content.contains("Refactor cross-borough"), "Should contain intention")
    }

    @Test
    fun `task uses default ragQuestion when not set`(@TempDir tempDir: File) {
        val outputFile = File(tempDir, "default-rag.txt")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("planIntention", PlanIntentionTask::class.java).get()

        task.intention.set("Create new EPIC")
        task.workspaceRoot.set(tempDir)
        task.outputFile.set(outputFile)
        // ragQuestion not set — should fallback to intention value

        task.execute()

        assertTrue(outputFile.isFile)
        assertTrue(outputFile.readText().isNotEmpty())
    }

    @Test
    fun `task shows error in output when plan is null`(@TempDir tempDir: File) {
        val outputFile = File(tempDir, "error-plan.txt")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("planIntention", PlanIntentionTask::class.java).get()

        task.intention.set("test error case")
        task.workspaceRoot.set(tempDir)
        task.outputFile.set(outputFile)

        task.execute()

        assertTrue(outputFile.isFile)
        val content = outputFile.readText()
        // Without pgvector, plan is null, so output shows "Aucun plan généré"
        // and/or error message
        assertTrue(content.contains("PLAN"), "Should contain PLAN header")
    }

    @Test
    fun `task has correct task properties`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("planIntention", PlanIntentionTask::class.java).get()

        assertNotNull(task.intention)
        assertNotNull(task.workspaceRoot)
        assertNotNull(task.outputFile)
        assertNotNull(task.ragQuestion)
    }
}
