package codebase.koog.llm

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class FakeVisionProviderTest {

    @Test
    fun `processImage returns structured AsciiDoc without network call`() = runBlocking {
        val provider = FakeVisionProvider()
        val fakePng = createFakeImage(100, 50)
        val result = provider.processImage(fakePng, "image/png", "fr")

        assertTrue(result.contains("= Titre Principal"), "Should have FR title, got: $result")
        assertTrue(result.contains("FakeVisionProvider"))
        assertTrue(result.contains("Confiance: haute"))
        assertTrue(result.contains("Section 1"))
        assertTrue(result.contains("Section 2"))
        assertTrue(result.contains("| Colonne A"))
        assertTrue(result.contains("| Valeur 1"))
        assertTrue(result.contains("Premier élément de liste"))
        assertTrue(result.contains("caractères gras"))
    }

    @Test
    fun `processImage adapts title to language`() = runBlocking {
        val provider = FakeVisionProvider()
        val fakePng = createFakeImage(50, 50)

        val fr = provider.processImage(fakePng, "image/png", "fr")
        assertTrue(fr.contains("= Titre Principal"))

        val en = provider.processImage(fakePng, "image/png", "en")
        assertTrue(en.contains("= Main Title"))

        val de = provider.processImage(fakePng, "image/png", "de")
        assertTrue(de.contains("= Haupttitel"))
    }

    @Test
    fun `processImage reports image size in output`() = runBlocking {
        val provider = FakeVisionProvider()
        val bytes = ByteArray(42) { 'x'.code.toByte() }
        val result = provider.processImage(bytes, "image/jpeg", "fr")
        assertTrue(result.contains("42 bytes"))
    }

    @Test
    fun `processImage reports model name in comment`() = runBlocking {
        val provider = FakeVisionProvider()
        val fakePng = createFakeImage(32, 32)
        val result = provider.processImage(fakePng, "image/png", "en", model = "gemini-2.5-pro")
        assertTrue(result.contains("gemini-2.5-pro"))
    }

    @Test
    fun `processImage with default language is french`() = runBlocking {
        val provider = FakeVisionProvider()
        val fakePng = createFakeImage(16, 16)
        val result = provider.processImage(fakePng, "image/gif")
        assertTrue(result.contains("Titre Principal"))
        assertTrue(result.contains("Langue: fr"))
    }

    private fun createFakeImage(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val tempFile = File.createTempFile("fake-ocr", ".png")
        ImageIO.write(image, "png", tempFile)
        val bytes = tempFile.readBytes()
        tempFile.delete()
        return bytes
    }
}
