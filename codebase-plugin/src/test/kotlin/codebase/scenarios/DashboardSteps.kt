package codebase.scenarios

import codebase.CodebasePlugin
import codebase.koog.tracking.DashboardTask
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

/**
 * Step Definitions Cucumber pour le DashboardTask Gradle (@epic_v_8).
 *
 * Pattern PicoContainer standardisé — le world est injecté par constructeur
 * et partagé entre VibecodingSteps et DashboardSteps via le même World class.
 */
class DashboardSteps(private val world: VibecodingWorld) {

    // ── Given ──

    @Given("the codebase plugin is applied to a Gradle project")
    fun `codebase plugin applied to gradle project`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(CodebasePlugin::class.java)
        world.gradleProject = project
    }

    // ── When ──

    @When("I look up the {string} task")
    fun `look up task by name`(taskName: String) {
        val project = world.gradleProject
        assertNotNull(project, "Gradle project must be initialized in Background")
        val task = project.tasks.findByName(taskName)
        assertNotNull(task, "Task '$taskName' should be registered by CodebasePlugin")
        world.foundTask = task
    }

    @When("I execute the {string} task without connection factory")
    fun `execute task without connection factory`(taskName: String) {
        val project = world.gradleProject
        assertNotNull(project, "Gradle project must be initialized")
        val task = project.tasks.findByName(taskName) as? DashboardTask
        assertNotNull(task, "Task '$taskName' should be a DashboardTask")

        world.dashboardException = assertThrows(IllegalStateException::class.java) {
            task.executeDashboard()
        }
    }

    // ── Then ──

    @Then("the task group is {string}")
    fun `task group is`(expectedGroup: String) {
        val task = world.foundTask
        assertNotNull(task, "Task must be found")
        assertEquals(expectedGroup, task.group)
    }

    @Then("the task description contains {string}")
    fun `task description contains`(expectedFragment: String) {
        val task = world.foundTask
        assertNotNull(task, "Task must be found")
        val desc = task.description ?: ""
        assertTrue(desc.contains(expectedFragment, ignoreCase = true),
            "Description should contain '$expectedFragment', got: '$desc'")
    }

    @Then("a vibecoding dashboard IllegalStateException is thrown")
    fun `dashboard illegal state exception thrown`() {
        assertNotNull(world.dashboardException, "IllegalStateException should have been thrown")
    }

    @Then("the vibecoding dashboard error contains {string}")
    fun `dashboard error contains`(expected: String) {
        val msg = world.dashboardException?.message
        assertTrue(msg != null && msg.contains(expected, ignoreCase = true),
            "Error should contain '$expected', got: $msg")
    }
}
