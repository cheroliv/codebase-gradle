package codebase.rag

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DilutionExecutorTest {

    private val executor = DilutionExecutor("/tmp/test", dryRun = true)

    @Test
    fun `findSectionStart finds == heading`() {
        val content = """
            = Document
            == Section A
            contenu section A
            == Section B
            contenu section B
        """.trimIndent()

        val pos = executor.findSectionStart(content, "== Section B")
        assertNotNull(pos)
        assertTrue(pos > 0)
    }

    @Test
    fun `findSectionStart finds === heading`() {
        val content = """
            = Document
            === Sous-section
            contenu
        """.trimIndent()

        val pos = executor.findSectionStart(content, "== Sous-section")
        assertNotNull(pos)
    }

    @Test
    fun `findSectionStart returns null when section not found`() {
        val content = "= Document\n== Section A\ncontenu"
        val pos = executor.findSectionStart(content, "== Inexistante")
        assertNull(pos)
    }

    @Test
    fun `findSectionStart strips == prefix from search`() {
        val content = """
            == Mon Titre
            contenu
        """.trimIndent()

        val pos = executor.findSectionStart(content, "== Mon Titre")
        assertNotNull(pos)
    }

    @Test
    fun `findSectionStart strips whitespace from search`() {
        val content = "== Titre Avec Espaces\ncontenu"
        val pos = executor.findSectionStart(content, "== Titre Avec Espaces")
        assertNotNull(pos)
    }

    @Test
    fun `formatDilutedSection includes all metadata fields`() {
        val record = DilutionRecord(
            sectionId = "S1",
            sectionTitle = "Architecture DAG",
            content = "Le DAG a 4 niveaux.",
            classification = ContentClassification.VISION,
            confidence = 0.93,
            dilutionTarget = DilutionTarget(
                TargetDocument.WORKSPACE_VISION,
                "== Gouvernance",
                "Contenu architectural"
            ),
            classificationRationale = "Vision technique",
            timestamp = LocalDateTime.of(2026, 5, 28, 14, 0)
        )

        val formatted = executor.formatDilutedSection(record)

        assertTrue(formatted.contains("=== Architecture DAG"))
        assertTrue(formatted.contains("Le DAG a 4 niveaux."))
        assertTrue(formatted.contains("VISION (confiance: 0,93)"))
        assertTrue(formatted.contains("Vision technique"))
        assertTrue(formatted.contains("Contenu architectural"))
        assertTrue(formatted.contains("Metadonnees de dilution"))
    }

    @Test
    fun `formatDilutedSection OPINION record shows dash for null dilution target`() {
        val record = DilutionRecord(
            sectionId = "O1", sectionTitle = "Opinion", content = "Je pense...",
            classification = ContentClassification.OPINION, confidence = 0.5,
            dilutionTarget = null, classificationRationale = "subjectif",
            timestamp = LocalDateTime.of(2026, 5, 28, 12, 0)
        )

        val formatted = executor.formatDilutedSection(record)

        assertTrue(formatted.contains("OPINION (confiance: 0,50)"))
        assertTrue(formatted.contains("—")) // rational routage placeholder
    }

    @Test
    fun `formatDilutedSection includes date in dd MMM yyyy format`() {
        val record = DilutionRecord(
            sectionId = "S1", sectionTitle = "Test", content = "test",
            classification = ContentClassification.VISION, confidence = 0.9,
            dilutionTarget = DilutionTarget(TargetDocument.WORKSPACE_VISION, "== S", "r"),
            classificationRationale = "r", timestamp = LocalDateTime.of(2026, 3, 15, 10, 0)
        )

        val formatted = executor.formatDilutedSection(record)

        assertTrue(formatted.contains("15 mars 2026"))
    }

    @Test
    fun `formatDilutedSection has AsciiDoc table structure`() {
        val record = DilutionRecord(
            sectionId = "S1", sectionTitle = "T", content = "c",
            classification = ContentClassification.VISION, confidence = 0.8,
            dilutionTarget = DilutionTarget(TargetDocument.WORKSPACE_VISION, "== S", "r"),
            classificationRationale = "r", timestamp = LocalDateTime.now()
        )

        val formatted = executor.formatDilutedSection(record)

        assertTrue(formatted.contains("[cols=\"2,3\"]"))
        assertTrue(formatted.contains("|==="))
        assertTrue(formatted.contains("| Date |"))
        assertTrue(formatted.contains("| Classification |"))
        assertTrue(formatted.contains("| Rationale classification |"))
        assertTrue(formatted.contains("| Rationale routage |"))
    }

    @Test
    fun `injectSection inserts content after existing heading`() {
        val content = """
            = Document
            == Section A
            contenu section A
            == Section B
            contenu section B
        """.trimIndent()

        val formatted = "\n=== Nouvelle section\nNouveau contenu\n"
        val result = executor.injectSection(content, formatted, "== Section B")

        assertTrue(result.contains("Section B")) // après insertion
        assertTrue(result.contains("Nouveau contenu"))
        assertTrue(result.startsWith("= Document"))
    }

    @Test
    fun `injectSection creates new heading when section not found`() {
        val content = "= Document\n== Section A\ncontenu"

        val formatted = "\n=== New Section\nnew content\n"
        val result = executor.injectSection(content, formatted, "== Inexistante")

        assertTrue(result.contains("== Inexistante"))
        assertTrue(result.contains("new content"))
        assertTrue(result.endsWith("new content\n"))
    }

    @Test
    fun `findLastSectionEnd returns correct position after last heading`() {
        val content = """
            = Document
            == Section A
            contenu section A
            == Section B
            contenu section B
        """.trimIndent()

        val pos = executor.findLastSectionEnd(content)

        assertTrue(pos > 0)
        assertTrue(pos <= content.length)
    }

    @Test
    fun `findLastSectionEnd empty content returns 0`() {
        val pos = executor.findLastSectionEnd("")
        assertEquals(0, pos)
    }

    @Test
    fun `findLastSectionEnd no headings returns 0`() {
        val pos = executor.findLastSectionEnd("juste du texte sans titre")
        assertEquals(0, pos)
    }

    @Test
    fun `DilutionResult success stores documentPath`() {
        val result = DilutionResult(
            documentPath = "WORKSPACE_VISION.adoc",
            sectionInjected = "== Architecture",
            success = true,
            backupPath = "/tmp/backup",
            error = null
        )

        assertEquals("WORKSPACE_VISION.adoc", result.documentPath)
        assertEquals("== Architecture", result.sectionInjected)
        assertEquals(true, result.success)
        assertEquals("/tmp/backup", result.backupPath)
        assertEquals(null, result.error)
    }

    @Test
    fun `DilutionResult failure stores error message`() {
        val result = DilutionResult(
            documentPath = "",
            sectionInjected = "",
            success = false,
            backupPath = null,
            error = "Aucune cible de dilution"
        )

        assertEquals(false, result.success)
        assertEquals("Aucune cible de dilution", result.error)
    }
}
