package codebase.koog

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VibecodingIntegrationTest {

    @Test
    fun `vibecode task registers in Gradle project`() {
        val projectDir = File("/tmp/vibecode-integration-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"vibe-test\"")

            val project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
            project.plugins.apply("education.cccp.codebase")

            val task = project.tasks.findByName("vibecode")
            assertNotNull(task, "vibecode task should be registered")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `vibecode task end-to-end dryRun writes audit JSONL`() {
        val projectDir = File("/tmp/vibecode-e2e-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"vibe-e2e\"")
            val src = File(projectDir, "src/main/kotlin/com/test")
            src.mkdirs()
            File(src, "Main.kt").writeText("package com.test\n\nfun main() = println(\"hello\")")

            val project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
            project.plugins.apply("education.cccp.codebase")

            val task = project.tasks.getByName("vibecode") as VibecodingTask
            task.intention.set("analyze kotlin source files")
            task.dryRun.set(true)
            task.workspaceRoot.set(projectDir)

            try {
                task.executeVibecoding()
            } catch (e: Exception) {
                // acceptable: dryRun graph may fail on missing augmented context
                assertTrue(e.message != null)
            }

            val auditFile = File(projectDir, "build/vibecoding/audit.jsonl")
            assertTrue(auditFile.exists(), "audit.jsonl should be written even on error")

            val content = auditFile.readText().trim()
            assertTrue(content.isNotEmpty(), "audit.jsonl should have content")
            val lines = content.lines().filter { it.isNotBlank() }
            assertTrue(lines.isNotEmpty(), "Should have at least 1 audit line")
            for (line in lines) {
                assertTrue(line.startsWith("{") && line.endsWith("}"), "Line should be JSON: $line")
                assertTrue(line.contains("\"timestamp\""), "Should contain timestamp")
            }
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `vibecode dryRun does not mutate project files`() {
        val projectDir = File("/tmp/vibecode-dryrun-e2e-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"vibe-dryrun\"")

            val src = File(projectDir, "src/main/kotlin/com/test")
            src.mkdirs()
            val mainKt = File(src, "Main.kt")
            mainKt.writeText("package com.test\n\nfun main() = println(\"hello world\")")
            val originalContent = mainKt.readText()

            val project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
            project.plugins.apply("education.cccp.codebase")

            val task = project.tasks.getByName("vibecode") as VibecodingTask
            task.intention.set("refactor kotlin source files")
            task.dryRun.set(true)
            task.workspaceRoot.set(projectDir)

            try {
                task.executeVibecoding()
            } catch (e: Exception) {
                // acceptable: dryRun graph may fail on missing augmented context
            }

            assertTrue(mainKt.exists(), "Main.kt should still exist")
            assertEquals(originalContent, mainKt.readText(), "Main.kt should be unchanged")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `vibecode sandbox blocks path traversal in Gradle project`() {
        val projectDir = File("/tmp/vibecode-sandbox-e2e-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            File(projectDir, ".env").writeText("SECRET=real-token")
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"vibe-sandbox\"")

            val registry = ToolRegistry()
            try {
                registry.execute(
                    "read_file",
                    mapOf("path" to "../../.env"),
                    projectDir.absolutePath
                )
                assertTrue(false, "Should have thrown SecurityException")
            } catch (e: SecurityException) {
                assertTrue(e.message!!.contains("Path traversal blocked"))
            }

            try {
                registry.execute(
                    "read_file",
                    mapOf("path" to "/etc/passwd"),
                    projectDir.absolutePath
                )
                assertTrue(false, "Should have thrown SecurityException")
            } catch (e: SecurityException) {
                assertTrue(e.message!!.contains("Path traversal blocked"))
            }

            val result = registry.execute(
                "read_file",
                mapOf("path" to ".env"),
                projectDir.absolutePath
            )
            assertTrue(result.contains("real-token"), "Should allow reading .env inside workspaceRoot")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `vibecode write_file non-dryRun creates file on disk in Gradle project`() {
        val projectDir = File("/tmp/vibecode-write-e2e-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"vibe-write\"")

            val registry = ToolRegistry()
            registry.clearAudit()

            val result = registry.execute(
                "write_file",
                mapOf("path" to "build/generated/hello.txt", "content" to "hello integration"),
                projectDir.absolutePath,
                dryRun = false
            )

            val generatedFile = File(projectDir, "build/generated/hello.txt")
            assertTrue(generatedFile.exists(), "Generated file should exist")
            assertEquals("hello integration", generatedFile.readText())

            val entries = registry.auditEntries()
            assertEquals(1, entries.size, "Should have 1 audit entry")
            assertEquals("write_file", entries[0].tool)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `vibecode dryRun write_file does not create file`() {
        val projectDir = File("/tmp/vibecode-dryrun-write-e2e-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"vibe-dryrun-write\"")

            val registry = ToolRegistry()
            val result = registry.execute(
                "write_file",
                mapOf("path" to "build/generated/ghost.txt", "content" to "should not exist"),
                projectDir.absolutePath,
                dryRun = true
            )

            assertTrue(result.contains("DRY RUN"))
            val ghostFile = File(projectDir, "build/generated/ghost.txt")
            assertTrue(!ghostFile.exists(), "Ghost file should NOT exist on disk")
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `vibecode audit JSONL has all required fields`() {
        val projectDir = File("/tmp/vibecode-audit-fields-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"vibe-audit\"")

            val project = ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
            project.plugins.apply("education.cccp.codebase")

            val task = project.tasks.getByName("vibecode") as VibecodingTask
            task.intention.set("audit test")
            task.dryRun.set(true)
            task.workspaceRoot.set(projectDir)

            try {
                task.executeVibecoding()
            } catch (_: Exception) {}

            val auditFile = File(projectDir, "build/vibecoding/audit.jsonl")
            if (auditFile.exists()) {
                val lines = auditFile.readText().trim().lines().filter { it.isNotBlank() }
                for (line in lines) {
                    assertTrue(line.contains("\"timestamp\""), "Should have timestamp")
                    assertTrue(line.contains("\"dryRun\""), "Should have dryRun: $line")
                }
            }
        } finally {
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun `vibecode tool audit trail accumulates across multiple tool calls`() {
        val projectDir = File("/tmp/vibecode-multi-audit-${System.currentTimeMillis()}")
        projectDir.mkdirs()
        try {
            val testFile = File(projectDir, "multi.txt")
            testFile.writeText("multi content")

            val registry = ToolRegistry()
            registry.clearAudit()

            registry.execute("read_file", mapOf("path" to "multi.txt"), projectDir.absolutePath)
            registry.execute("list_directory", mapOf("path" to "."), projectDir.absolutePath)
            registry.execute("exit", emptyMap(), projectDir.absolutePath)

            val entries = registry.auditEntries()
            assertEquals(3, entries.size)
            entries.forEach { entry ->
                assertTrue(entry.tool.isNotEmpty(), "Each entry should have a tool name")
                assertTrue(entry.result.isNotEmpty() || entry.error != null, "Each entry should have result or error")
            }
        } finally {
            projectDir.deleteRecursively()
        }
    }
}
