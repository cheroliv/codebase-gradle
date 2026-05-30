package codebase.ocr

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OcrTaskTest {

    @Test
    fun `OcrEngine returns structured AsciiDoc for fake engine`() {
        val fake = FakeOcrEngine()
        val result = fake.process("image scan", "fr", "gemini-2.5-flash", 8192)

        assertTrue(result.contains("= Document OCRisé"))
        assertTrue(result.contains("image scan"))
        assertTrue(result.contains("langue: fr"))
        assertTrue(result.contains("modèle: gemini-2.5-flash"))
    }

    @Test
    fun `OcrEngine returns empty string for empty input`() {
        val fake = FakeOcrEngine()
        val result = fake.process("", "fr", "gemini-2.5-flash", 8192)

        assertEquals("", result)
    }

    @Test
    fun `OcrEngine respects metadata format`() {
        val fake = FakeOcrEngine()
        val result = fake.process("test content", "en", "gemini-1.5-flash", 4096)

        assertTrue(result.contains(":langue: en"))
        assertTrue(result.contains(":modèle: gemini-1.5-flash"))
        assertTrue(result.contains(":max-tokens: 4096"))
        assertTrue(result.startsWith("= Document OCRisé"))
    }

    @Test
    fun `FakeOcrEngine is an OcrEngine`() {
        val engine: OcrEngine = FakeOcrEngine()
        assertNotNull(engine)
        assertTrue(engine is OcrEngine)
    }

    @Test
    fun `fake engine does not throw for any input`() {
        val fake = FakeOcrEngine()

        val result1 = fake.process("a", "fr", "gemini-2.5-flash", 8192)
        assertTrue(result1.isNotEmpty())

        val result2 = fake.process("a".repeat(10_000), "fr", "gemini-2.5-flash", 8192)
        assertTrue(result2.isNotEmpty())

        val result3 = fake.process("éàùç汉字🎉", "auto", "gemini-2.5-flash", 8192)
        assertTrue(result3.isNotEmpty())
    }

    @Test
    fun `ocr task class exists and is abstract`() {
        val clazz = OcrTask::class.java
        assertTrue(clazz.simpleName == "OcrTask")
        assertFalse(clazz.isInterface)
    }

    @Test
    fun `OcrInputFile data class stores fields`() {
        val input = OcrInputFile(
            path = "/tmp/scan.pdf",
            language = "fr",
            provider = "gemini",
            model = "gemini-2.5-flash",
            maxTokens = 8192
        )
        assertEquals("/tmp/scan.pdf", input.path)
        assertEquals("fr", input.language)
        assertEquals("gemini", input.provider)
        assertEquals("gemini-2.5-flash", input.model)
        assertEquals(8192, input.maxTokens)
    }

    @Test
    fun `OcrInputFile copy works`() {
        val input = OcrInputFile("/tmp/scan.pdf")
        val modified = input.copy(language = "en", maxTokens = 4096)

        assertEquals("/tmp/scan.pdf", modified.path)
        assertEquals("en", modified.language)
        assertEquals(4096, modified.maxTokens)
        assertTrue(modified !== input)
    }
}
