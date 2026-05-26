package codebase.koog

import contracts.vibecoding.registry.ToolRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ToolRegistryTest {

    private val registry = ToolRegistry()

    @Test
    fun `read_file should reject files larger than 10MB`(@TempDir tempDir: File) {
        val largeFile = File(tempDir, "large.bin")
        val tenMbPlusOne = (10 * 1024 * 1024) + 1

        largeFile.outputStream().use { out ->
            var written = 0
            val buf = ByteArray(8192)
            while (written < tenMbPlusOne) {
                val chunk = minOf(buf.size, tenMbPlusOne - written)
                out.write(buf, 0, chunk)
                written += chunk
            }
        }

        assertThrows(SecurityException::class.java) {
            registry.execute(
                toolName = "read_file",
                arguments = mapOf("path" to largeFile.absolutePath),
                workspaceRoot = tempDir.absolutePath
            )
        }
    }

    @Test
    fun `audit entry should contain workspaceRoot`(@TempDir tempDir: File) {
        registry.clearAudit()
        val smallFile = File(tempDir, "audit.txt")
        smallFile.writeText("content")

        registry.execute(
            toolName = "read_file",
            arguments = mapOf("path" to smallFile.absolutePath),
            workspaceRoot = tempDir.absolutePath
        )

        val entry = registry.auditEntries().last()
        assertEquals(tempDir.absolutePath, entry.workspaceRoot,
            "Audit entry should contain workspaceRoot")
    }
}
