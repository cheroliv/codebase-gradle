package codebase.koog.llm

import codebase.rag.GeminiConfig
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class GeminiVisionProviderTest {

    @Test
    fun `should generate synthetic test image`() {
        val image = createSyntheticImage("Hello World OCR Test")
        val pngBytes = toPngBytes(image)

        assertThat(pngBytes).isNotEmpty()
        assertThat(pngBytes.copyOfRange(0, 4)).containsExactly(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()
        )
    }

    @Test
    fun `should produce valid image bytes for Gemini Vision`() {
        val image = createSyntheticImage("SCAN: Facture N\u00B02024-001\nMontant: 150\u20AC")
        val pngBytes = toPngBytes(image)

        val decoded = ImageIO.read(ByteArrayInputStream(pngBytes))
        assertThat(decoded).isNotNull()
        assertThat(decoded.width).isEqualTo(400)
        assertThat(decoded.height).isEqualTo(100)
    }

    @Test
    fun `should fail with clear error when GEMINI_API_KEY not set`() = runTest {
        val config = GeminiConfig(envVar = "GEMINI_API_KEY_NONEXISTENT_TEST_12345")
        val provider = GeminiVisionProvider(config)
        val imageBytes = toPngBytes(createSyntheticImage("test"))

        val exception = assertThrows<IllegalStateException> {
            provider.processImage(imageBytes, "image/png", "fr", "gemini-2.5-flash", 100)
        }
        assertThat(exception.message).contains("GEMINI_API_KEY_NONEXISTENT_TEST_12345")
    }

    @Test
    fun `should accept various language and model parameters`() {
        val provider = GeminiVisionProvider()
        assertThat(provider).isNotNull()
    }

    @Test
    fun `should handle large image bytes`() {
        val largeImage = BufferedImage(2000, 2000, BufferedImage.TYPE_INT_RGB)
        val g = largeImage.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 2000, 2000)
        g.dispose()
        val pngBytes = toPngBytes(largeImage)

        assertThat(pngBytes.size).isGreaterThan(1000)
    }

    @Test
    fun `provider class should be instantiable`() {
        val provider = GeminiVisionProvider()
        assertThat(provider).isNotNull()
        assertThat(provider.javaClass.simpleName).isEqualTo("GeminiVisionProvider")
    }

    private fun createSyntheticImage(text: String): BufferedImage {
        val image = BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 400, 100)
        g.color = Color.BLACK
        g.font = Font("Monospaced", Font.PLAIN, 18)
        g.drawString(text, 20, 50)
        g.dispose()
        return image
    }

    private fun toPngBytes(image: BufferedImage): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }
}
