package codebase.rag

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssembleWorkspaceContextTaskTest {

    @Test
    fun `task aggregates context files from multiple boroughs`(@TempDir tempDir: File) {
        // Create borough directories with context files
        val boroughA = File(tempDir, "borough-a")
        boroughA.mkdirs()
        val ctxDirA = File(boroughA, "build/context")
        ctxDirA.mkdirs()
        File(ctxDirA, "borough-a.context.txt").writeText("= CONTEXTE borough-a\nSection A\n= RAG borough-a\n[sim=0.9] chunk A\n")

        val boroughB = File(tempDir, "borough-b")
        boroughB.mkdirs()
        val ctxDirB = File(boroughB, "build/context")
        ctxDirB.mkdirs()
        File(ctxDirB, "borough-b.context.txt").writeText("= CONTEXTE borough-b\nSection B\n= RAG borough-b\n[sim=0.8] chunk B\n")

        val outputFile = File(tempDir, "workspace-context.txt")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("assembleWorkspaceContext", AssembleWorkspaceContextTask::class.java).get()

        task.foundryDir.set(tempDir)
        task.outputFile.set(outputFile)

        task.execute()

        assertTrue(outputFile.isFile, "Output file should exist")
        val content = outputFile.readText()
        assertTrue(content.contains("borough-a"), "Should contain borough-a")
        assertTrue(content.contains("borough-b"), "Should contain borough-b")
        assertTrue(content.contains("CONTEXTE WORKSPACE AUGMENTE"), "Should contain workspace header")
        assertTrue(content.contains("Sommaire"), "Should contain summary")
        assertTrue(content.length > 200, "Should be > 200 bytes")
    }

    @Test
    fun `task handles empty foundry with no context files`(@TempDir tempDir: File) {
        val emptyDir = File(tempDir, "empty-borough")
        emptyDir.mkdirs()

        File(emptyDir, "build").mkdirs()

        val outputFile = File(tempDir, "empty-output.txt")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("assembleWorkspaceContext", AssembleWorkspaceContextTask::class.java).get()

        task.foundryDir.set(tempDir)
        task.outputFile.set(outputFile)

        task.execute()

        assertTrue(outputFile.isFile, "Output file should exist even for empty foundry")
        val content = outputFile.readText()
        assertTrue(content.contains("Aucun fichier contexte"), "Should contain fallback message")
    }

    @Test
    fun `task ignores dirs without build context subdirectory`(@TempDir tempDir: File) {
        File(tempDir, "no-context-dir").mkdirs()

        val outputFile = File(tempDir, "output.txt")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("assembleWorkspaceContext", AssembleWorkspaceContextTask::class.java).get()

        task.foundryDir.set(tempDir)
        task.outputFile.set(outputFile)

        task.execute()

        assertTrue(outputFile.isFile)
        val content = outputFile.readText()
        assertTrue(content.contains("Aucun fichier contexte"))
    }

    @Test
    fun `task ignores non-context-txt files in context dir`(@TempDir tempDir: File) {
        val boroughA = File(tempDir, "borough-a")
        boroughA.mkdirs()
        val ctxDirA = File(boroughA, "build/context")
        ctxDirA.mkdirs()
        File(ctxDirA, "borough-a.context.txt").writeText("= CONTEXTE borough-a\nvalid context\n")
        File(ctxDirA, "metadata.json").writeText("{\"ignored\": true}")
        File(ctxDirA, "borough-a.txt").writeText("not a context file")

        val outputFile = File(tempDir, "output.txt")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("assembleWorkspaceContext", AssembleWorkspaceContextTask::class.java).get()

        task.foundryDir.set(tempDir)
        task.outputFile.set(outputFile)

        task.execute()

        assertTrue(outputFile.isFile)
        val content = outputFile.readText()
        assertTrue(content.contains("borough-a"), "Should include borough-a")
        assertTrue(content.contains("valid context"), "Should contain context content")
        assertTrue(!content.contains("not a context file"), "Should exclude non-context files")
        assertTrue(!content.contains("\"ignored\""), "Should exclude metadata.json")
    }

    @Test
    fun `task has correct task properties`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("assembleWorkspaceContext", AssembleWorkspaceContextTask::class.java).get()

        assertNotNull(task.foundryDir)
        assertNotNull(task.outputFile)
    }

    @Test
    fun `task creates output parent directories`(@TempDir tempDir: File) {
        val boroughA = File(tempDir, "borough-a")
        boroughA.mkdirs()
        val ctxDirA = File(boroughA, "build/context")
        ctxDirA.mkdirs()
        File(ctxDirA, "borough-a.context.txt").writeText("= CONTEXTE\ncontent\n")

        val outputFile = File(tempDir, "nested/dirs/output.txt")
        assertTrue(!outputFile.parentFile.exists(), "Parent should not exist before task")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("assembleWorkspaceContext", AssembleWorkspaceContextTask::class.java).get()

        task.foundryDir.set(tempDir)
        task.outputFile.set(outputFile)

        task.execute()

        assertTrue(outputFile.isFile, "Task should create parent dirs")
    }
}
