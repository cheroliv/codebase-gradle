package codebase.quality

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QualityGateTaskTest {

    @Test
    fun `task should be registered with correct group and description`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("qualityGate", QualityGateTask::class.java).get()

        assertEquals("qualityGate", task.name)
        assertEquals("validate", task.group)
        assertTrue(task.description?.contains("Quality") == true)
    }

    @Test
    fun `task should have default property values`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("qualityGate", QualityGateTask::class.java).get()

        assertEquals("", task.output.get())
        assertEquals("", task.domain.get())
        assertEquals(0.60, task.minAcceptableScore.get())
        assertEquals(true, task.enableSentimentCheck.get())
        assertEquals(true, task.enableOffTopicCheck.get())
        assertEquals(true, task.enablePiiCheck.get())
    }

    @Test
    fun `task should accept custom property values`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("qualityGate", QualityGateTask::class.java) {
            it.output.set("val x = 42")
            it.domain.set("CDA")
            it.minAcceptableScore.set(0.80)
            it.enableSentimentCheck.set(false)
        }.get()

        assertEquals("val x = 42", task.output.get())
        assertEquals("CDA", task.domain.get())
        assertEquals(0.80, task.minAcceptableScore.get())
        assertEquals(false, task.enableSentimentCheck.get())
    }

    @Test
    fun `execute with clean CDA output should pass`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("qualityGate", QualityGateTask::class.java) {
            it.output.set("class Calculator { fun add(a: Int, b: Int) = a + b }")
            it.domain.set("CDA")
        }.get()

        task.executeQualityGate()
    }

    @Test
    fun `execute with PII output should fail`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("qualityGate", QualityGateTask::class.java) {
            it.output.set("token=ghp_test1234 password=secret")
            it.domain.set("CDA")
        }.get()

        var failed = false
        try {
            task.executeQualityGate()
        } catch (_: RuntimeException) {
            failed = true
        }
        assertTrue(failed, "Task should fail on PII output")
    }

    @Test
    fun `execute with off-topic FPA output should fail`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("qualityGate", QualityGateTask::class.java) {
            it.output.set("@SpringBootApplication class App")
            it.domain.set("FPA")
        }.get()

        var failed = false
        try {
            task.executeQualityGate()
        } catch (_: RuntimeException) {
            failed = true
        }
        assertTrue(failed, "Task should fail on off-topic FPA output")
    }

    @Test
    fun `task validation result is accessible after execution`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("qualityGate", QualityGateTask::class.java) {
            it.output.set("fun sum(a: Int, b: Int) = a + b")
            it.domain.set("CDA")
        }.get()

        task.executeQualityGate()
        assertNotNull(task.lastResult)
        assertEquals(3, task.lastResult!!.results.size)
        assertTrue(task.lastResult!!.passed)
    }

    @Test
    fun `result is null before execution`() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("qualityGate", QualityGateTask::class.java) {
            it.output.set("clean")
            it.domain.set("CDA")
        }.get()

        assertEquals(null, task.lastResult)
    }
}
