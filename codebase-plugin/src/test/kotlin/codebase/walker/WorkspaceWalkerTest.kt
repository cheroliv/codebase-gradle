package codebase.walker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceWalkerTest {

    @Test
    fun `walk discovers all files in temp dir`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("a.kt").writeText("hello")
        dir.resolve("b.adoc").writeText("world")
        dir.resolve("c.txt").writeText("test")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(3, files.size)
    }

    @Test
    fun `walk excludes build directory`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("source.kt").writeText("val x = 1")
        val buildDir = dir.resolve("build")
        buildDir.mkdir()
        buildDir.resolve("generated.class").writeText("binary")
        buildDir.resolve("output.txt").writeText("output")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(1, files.size)
        assertEquals("source.kt", files[0].fileName)
    }

    @Test
    fun `walk excludes hidden directories`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("visible.kt").writeText("hello")
        val hiddenDir = dir.resolve(".hidden")
        hiddenDir.mkdir()
        hiddenDir.resolve("secret.txt").writeText("secret")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(1, files.size)
        assertEquals("visible.kt", files[0].fileName)
    }

    @Test
    fun `walk excludes git directory`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("main.kt").writeText("fun main()")
        val gitDir = dir.resolve(".git")
        gitDir.mkdir()
        gitDir.resolve("HEAD").writeText("ref: refs/heads/main")
        gitDir.resolve("config").writeText("[core]")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(1, files.size)
    }

    @Test
    fun `walk excludes gradle directory`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("build.gradle.kts").writeText("plugins {}")
        val gradleDir = dir.resolve(".gradle")
        gradleDir.mkdir()
        gradleDir.resolve("cache.properties").writeText("key=value")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(1, files.size)
    }

    @Test
    fun `walk excludes node_modules directory`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("package.json").writeText("{}")
        val nodeModules = dir.resolve("node_modules")
        nodeModules.mkdir()
        nodeModules.resolve("lodash.js").writeText("module.exports = {}")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(1, files.size)
    }

    @Test
    fun `walk excludes kotlin directory`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("App.kt").writeText("fun main()")
        val kotlinDir = dir.resolve(".kotlin")
        kotlinDir.mkdir()
        kotlinDir.resolve("sessions").mkdir()
        kotlinDir.resolve("sessions").resolve("session.lock").writeText("")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(1, files.size)
    }

    @Test
    fun `WorkspaceFile extension is correct`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("test.kt").writeText("val x = 1")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(1, files.size)
        assertEquals("kt", files[0].extension)
        assertEquals("test.kt", files[0].fileName)
    }

    @Test
    fun `WorkspaceFile fileSize is greater than zero for non-empty file`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        val content = "package codebase\n\nfun main() {\n    println(\"Hello, World!\")\n}\n"
        dir.resolve("main.kt").writeText(content)

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(1, files.size)
        assertTrue(files[0].fileSize > 0)
        assertEquals(content.length.toLong(), files[0].fileSize)
    }

    @Test
    fun `walk returns empty list for empty directory`(@TempDir tempDir: Path) {
        val walker = WorkspaceWalker(tempDir.toFile())
        val files = walker.walk()

        assertTrue(files.isEmpty())
    }

    @Test
    fun `walk sorts results by filePath`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        dir.resolve("c.txt").writeText("c")
        dir.resolve("a.txt").writeText("a")
        dir.resolve("b.txt").writeText("b")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(3, files.size)
        val paths = files.map { it.fileName }
        assertEquals(listOf("a.txt", "b.txt", "c.txt"), paths)
    }

    @Test
    fun `walk discovers nested files in non-excluded dirs`(@TempDir tempDir: Path) {
        val dir = tempDir.toFile()
        val srcDir = dir.resolve("src")
        srcDir.mkdir()
        srcDir.resolve("main.kt").writeText("fun main()")
        srcDir.resolve("util.kt").writeText("fun helper()")

        val walker = WorkspaceWalker(dir)
        val files = walker.walk()

        assertEquals(2, files.size)
    }
}
