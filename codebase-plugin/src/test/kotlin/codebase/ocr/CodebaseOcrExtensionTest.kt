package codebase.ocr

import codebase.CodebasePlugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CodebaseOcrExtensionTest {

    private fun createProject(): Project = ProjectBuilder.builder()
        .withName("test-ocr")
        .build()
        .also { it.pluginManager.apply(CodebasePlugin::class.java) }

    @Test
    fun `extension is registered under name codebaseOcr`() {
        val project = createProject()
        val ext = project.extensions.findByName("codebaseOcr")
        assertNotNull(ext, "codebaseOcr extension should be registered after applying plugin")
        assertTrue(ext is CodebaseOcrExtension, "extension should be of type CodebaseOcrExtension")
    }

    @Test
    fun `default convention values are set`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        assertEquals("gemini", ext.ocrProvider.get())
        assertEquals("gemini-2.5-flash", ext.geminiModel.get())
        assertEquals("fr", ext.ocrLanguage.get())
        assertEquals("asciidoc", ext.outputFormat.get())
    }

    @Test
    fun `ocrProvider can be overridden`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        ext.ocrProvider.set("tesseract")
        assertEquals("tesseract", ext.ocrProvider.get())
    }

    @Test
    fun `geminiModel can be overridden`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        ext.geminiModel.set("gemini-2.5-pro")
        assertEquals("gemini-2.5-pro", ext.geminiModel.get())
    }

    @Test
    fun `ocrLanguage can be overridden`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        ext.ocrLanguage.set("en")
        assertEquals("en", ext.ocrLanguage.get())
    }

    @Test
    fun `outputFormat can be overridden to markdown`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        ext.outputFormat.set("markdown")
        assertEquals("markdown", ext.outputFormat.get())
    }

    @Test
    fun `geminiApiKeys list is initially empty`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        assertTrue(ext.geminiApiKeys.get().isEmpty())
    }

    @Test
    fun `geminiApiKeys can be populated`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        val keys = listOf("key-1", "key-2", "key-3")
        ext.geminiApiKeys.set(keys)
        assertEquals(keys, ext.geminiApiKeys.get())
    }

    @Test
    fun `inputDir can be set`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        val dir = project.projectDir.resolve("scans")
        ext.inputDir.set(dir)
        assertEquals(dir, ext.inputDir.get().asFile)
    }

    @Test
    fun `ocrEnabled defaults to false`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        assertFalse(ext.ocrEnabled.get())
    }

    @Test
    fun `ocrEnabled can be enabled`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        ext.ocrEnabled.set(true)
        assertTrue(ext.ocrEnabled.get())
    }

    @Test
    fun `maxTokens has a default of 8192`() {
        val project = createProject()
        val ext = project.extensions.getByType(CodebaseOcrExtension::class.java)

        assertEquals(8192, ext.maxTokens.get())
    }
}
