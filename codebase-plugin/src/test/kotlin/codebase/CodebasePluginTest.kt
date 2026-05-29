package codebase

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodebasePluginTest {

    private val expectedTasks = setOf(
        "collectFromCodebase",
        "collectCompositeContext",
        "generatePlan",
        "vibecode",
        "vibecodingDashboard",
        "qualityGate",
        "generateCompositeContext"
    )

    @Test
    fun `apply plugin registers all 7 tasks`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        for (taskName in expectedTasks) {
            assertNotNull(project.tasks.findByName(taskName), "Task '$taskName' should be registered")
        }
    }

    @Test
    fun `task count equals 7 after applying plugin`() {
        val project = ProjectBuilder.builder().withName("codebase").build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val registeredTasks = expectedTasks.mapNotNull { project.tasks.findByName(it) }
        assertEquals(7, registeredTasks.size)
    }

    @Test
    fun `vibecode is in generate group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val vibecodeTask = project.tasks.findByName("vibecode")
        assertNotNull(vibecodeTask)
        assertEquals("generate", vibecodeTask.group)
    }

    @Test
    fun `collectFromCodebase is in collect group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val task = project.tasks.findByName("collectFromCodebase")
        assertNotNull(task)
        assertEquals("collect", task.group)
    }

    @Test
    fun `collectCompositeContext is in collect group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val task = project.tasks.findByName("collectCompositeContext")
        assertNotNull(task)
        assertEquals("collect", task.group)
    }

    @Test
    fun `generatePlan is in generate group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val task = project.tasks.findByName("generatePlan")
        assertNotNull(task)
        assertEquals("generate", task.group)
    }

    @Test
    fun `vibecodingDashboard is in tracking group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val task = project.tasks.findByName("vibecodingDashboard")
        assertNotNull(task)
        assertEquals("tracking", task.group)
    }

    @Test
    fun `qualityGate is in validate group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val task = project.tasks.findByName("qualityGate")
        assertNotNull(task)
        assertEquals("validate", task.group)
    }

    @Test
    fun `generateCompositeContext is in generate group`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(CodebasePlugin::class.java)

        val task = project.tasks.findByName("generateCompositeContext")
        assertNotNull(task)
        assertEquals("generate", task.group)
    }

    @Test
    fun `tasks`(@TempDir tempDir: File) {
        val projectDir = tempDir.resolve("consumer")
        projectDir.mkdirs()
        projectDir.resolve("settings.gradle.kts").writeText("""
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "consumer"
        """.trimIndent())
        projectDir.resolve("build.gradle.kts").writeText("""
            plugins { id("education.cccp.codebase") version "0.0.1" }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("tasks", "--stacktrace")
            .withPluginClasspath()
            .build()

        val output = result.output

        println("╔══════════════════════════════════════════════════════════════╗")
        println("║               CODEPLUGIN — TASK DUMP                  ║")
        println("╚══════════════════════════════════════════════════════════════╝")
        println()

        // Extraire uniquement les groupes du plugin (collect, generate, tracking, validate)
        val pluginSection = output.lines()
            .dropWhile { !it.contains("Collect tasks") }
            .takeWhile { !it.contains("Help tasks") }
            .joinToString("\n")
        println(pluginSection)

        println()
        println("╔══════════════════════════════════════════════════════════════╗")
        println("║                    END OF TASK DUMP                        ║")
        println("╚══════════════════════════════════════════════════════════════╝")

        for (task in expectedTasks) {
            assertTrue(output.contains(task), "Task '$task' should appear in task list")
        }
    }
}
