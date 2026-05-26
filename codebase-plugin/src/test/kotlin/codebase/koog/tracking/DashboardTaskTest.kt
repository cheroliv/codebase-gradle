package codebase.koog.tracking

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DashboardTaskTest {

    @Test
    fun `task should be registered with correct group and description`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testDashboard", DashboardTask::class.java).get()

        assertEquals("testDashboard", task.name)
        assertEquals("tracking", task.group)
        assertTrue(task.description?.contains("Dashboard") == true)
    }

    @Test
    fun `task should throw when connectionFactory is null`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testDashboard", DashboardTask::class.java).get()

        assertNull(task.connectionFactory)

        val ex = assertThrows(IllegalStateException::class.java) {
            task.executeDashboard()
        }
        assertTrue(ex.message?.contains("ConnectionFactory") == true,
            "Should mention ConnectionFactory, got: ${ex.message}")
    }

    @Test
    fun `task should have connectionFactory as nullable var`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testDashboard", DashboardTask::class.java).get()

        assertNull(task.connectionFactory)
        // Pas de set (ConnectionFactory est une interface lourde, testé dans l'intégration)
    }
}
