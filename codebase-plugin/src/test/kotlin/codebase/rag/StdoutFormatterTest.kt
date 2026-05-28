package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StdoutFormatterTest {

    @Test
    fun `Tag value class stores label and formats as bracket wrapped`() {
        val tag = StdoutFormatter.Tag("DEBUG")
        assertEquals("DEBUG", tag.label)
        assertEquals("[DEBUG]", tag.toString())
    }

    @Test
    fun `companion Tag constants have expected values`() {
        assertEquals("CTX", StdoutFormatter.Tag.CTX.label)
        assertEquals("PLAN", StdoutFormatter.Tag.PLAN.label)
        assertEquals("RESULT", StdoutFormatter.Tag.RESULT.label)
        assertEquals("ERROR", StdoutFormatter.Tag.ERROR.label)
    }

    @Test
    fun `section formats tag plus content`() {
        val output = captureStdout {
            StdoutFormatter.section(StdoutFormatter.Tag.PLAN, "test content")
        }
        assertEquals("[PLAN] test content\n", output)
    }

    @Test
    fun `banner produces box around title`() {
        val output = captureStdout {
            StdoutFormatter.banner("Hello")
        }
        val lines = output.lines()
        assertTrue(lines.size >= 4)
        assertTrue(lines[0].startsWith("═"))
        assertTrue(lines[1].contains("Hello"))
        assertTrue(lines[2].startsWith("═"))
    }

    @Test
    fun `separator prints 60 dashes`() {
        val output = captureStdout {
            StdoutFormatter.separator()
        }
        assertEquals("─".repeat(60) + "\n", output)
    }

    private fun captureStdout(block: () -> Unit): String {
        val originalOut = System.out
        val stream = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(stream))
        try {
            block()
        } finally {
            System.setOut(originalOut)
        }
        return stream.toString()
    }
}
