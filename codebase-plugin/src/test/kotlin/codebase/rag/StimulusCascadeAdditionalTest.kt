package codebase.rag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests additionnels pour StimulusCascade — couvre parseSections, parseRoutingResponse, sha256, archiveReport.
 */
class StimulusCascadeAdditionalTest {

    private val cascade = StimulusCascade(dryRun = true)

    @Test
    fun `parseSections with simple text without headings`() {
        val sections = cascade.parseSections("Juste un paragraphe de texte simple.")
        assertEquals(1, sections.size)
        assertEquals("S1", sections[0].id)
        assertEquals("Juste un paragraphe de texte simple.", sections[0].title)
    }

    @Test
    fun `parseSections with multiple level-2 headings`() {
        val text = """
            = Document Principal
            == Section Architecture
            Contenu sur l'architecture DAG N0-N3
            
            == Section Gouvernance
            Règles de gouvernance agent
            
            == Section Produit
            Business model et roadmap
        """.trimIndent()

        val sections = cascade.parseSections(text)

        assertEquals(3, sections.size)
        assertEquals("S1", sections[0].id)
        assertEquals("Section Architecture", sections[0].title)
        assertTrue(sections[0].content.contains("DAG N0-N3"))
        assertEquals("S2", sections[1].id)
        assertEquals("Section Gouvernance", sections[1].title)
        assertEquals("S3", sections[2].id)
        assertEquals("Section Produit", sections[2].title)
    }

    @Test
    fun `parseSections extracts content between headings`() {
        val text = """
            == Premier
            Contenu du premier bloc
            sur plusieurs lignes
            
            == Deuxieme
            Contenu du deuxieme
        """.trimIndent()

        val sections = cascade.parseSections(text)

        assertEquals(2, sections.size)
        assertEquals("Contenu du premier bloc\nsur plusieurs lignes", sections[0].content)
        assertEquals("Contenu du deuxieme", sections[1].content)
    }

    @Test
    fun `parseSections with heading equals sign prefix strips it`() {
        val text = """
            = Main Title
            == Cle Architecture
            Le contenu sur l'architecture.
        """.trimIndent()

        val sections = cascade.parseSections(text)
        assertEquals(1, sections.size)
        assertEquals("Cle Architecture", sections[0].title)
    }

    @Test
    fun `parseRoutingResponse parses WORKSPACE_VISION target`() {
        val section = StimulusCascade.ParsedSection("S1", "Architecture DAG", "content")
        val raw = """{"targetDocument": "WORKSPACE_VISION", "suggestedSection": "== Regles DAG", "rationale": "Architecture core"}"""

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals(TargetDocument.WORKSPACE_VISION, result.targetDocument)
        assertEquals("== Regles DAG", result.suggestedSection)
        assertEquals("Architecture core", result.rationale)
    }

    @Test
    fun `parseRoutingResponse parses WORKSPACE_AS_PRODUCT target`() {
        val section = StimulusCascade.ParsedSection("S2", "Pricing", "content")
        val raw = """{"targetDocument": "WORKSPACE_AS_PRODUCT", "suggestedSection": "== Pricing Strategy", "rationale": "Business model"}"""

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals(TargetDocument.WORKSPACE_AS_PRODUCT, result.targetDocument)
    }

    @Test
    fun `parseRoutingResponse parses WHAT_THE_GAMES_BEEN_MISSING target`() {
        val section = StimulusCascade.ParsedSection("S3", "Idee radicale", "content")
        val raw = """{"targetDocument": "WHAT_THE_GAMES_BEEN_MISSING", "suggestedSection": "== Idees futures", "rationale": "Speculation"}"""

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals(TargetDocument.WHAT_THE_GAMES_BEEN_MISSING, result.targetDocument)
    }

    @Test
    fun `parseRoutingResponse parses WORKSPACE_ORGANIZATION target`() {
        val section = StimulusCascade.ParsedSection("S4", "Dossiers", "content")
        val raw = """{"targetDocument": "WORKSPACE_ORGANIZATION", "suggestedSection": "== Structure", "rationale": "Org"}"""

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals(TargetDocument.WORKSPACE_ORGANIZATION, result.targetDocument)
    }

    @Test
    fun `parseRoutingResponse strips markdown code fences around JSON`() {
        val section = StimulusCascade.ParsedSection("S5", "Test", "content")
        val raw = """
            ```json
            {"targetDocument": "WORKSPACE_VISION", "suggestedSection": "== Test", "rationale": "test"}
            ```
        """.trimIndent()

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals(TargetDocument.WORKSPACE_VISION, result.targetDocument)
        assertEquals("== Test", result.suggestedSection)
    }

    @Test
    fun `parseRoutingResponse falls back to WORKSPACE_VISION for unknown target`() {
        val section = StimulusCascade.ParsedSection("S6", "Unknown", "content")
        val raw = """{"targetDocument": "UNKNOWN_DOC", "suggestedSection": "== Fallback", "rationale": "unknown"}"""

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals(TargetDocument.WORKSPACE_VISION, result.targetDocument)
    }

    @Test
    fun `parseRoutingResponse uses default suggestedSection when field missing`() {
        val section = StimulusCascade.ParsedSection("S7", "No section", "content")
        val raw = """{"targetDocument": "WORKSPACE_VISION", "rationale": "just rationale"}"""

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals("== Nouveau contenu (S7)", result.suggestedSection)
    }

    @Test
    fun `parseRoutingResponse uses default rationale when field missing`() {
        val section = StimulusCascade.ParsedSection("S8", "No rationale", "content")
        val raw = """{"targetDocument": "WHAT_THE_GAMES_BEEN_MISSING", "suggestedSection": "== Ideas"}"""

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals("routage automatique", result.rationale)
    }

    @Test
    fun `sha256 produces consistent 64-character hash`() {
        val hash = cascade.sha256("test input")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `sha256 produces different hash for different inputs`() {
        val hash1 = cascade.sha256("input A")
        val hash2 = cascade.sha256("input B")
        assertTrue(hash1 != hash2)
    }

    @Test
    fun `archiveReport creates directory and files`(@TempDir tempDir: File) {
        val reporter = StimulusCascade(dryRun = true, workspaceRoot = tempDir.absolutePath)

        val record = DilutionRecord(
            sectionId = "S1", sectionTitle = "Architecture", content = "content",
            classification = ContentClassification.VISION, confidence = 0.9,
            dilutionTarget = DilutionTarget(TargetDocument.WORKSPACE_VISION, "== Test", "ok"),
            classificationRationale = "Vision", timestamp = LocalDateTime.of(2026, 5, 28, 16, 0)
        )
        val report = StimulusCascadeReport(
            source = "Brain dump", sourceHash = "abc123",
            sections = listOf(record), visionCount = 1, opinionCount = 0, dilutedCount = 1,
            timestamp = LocalDateTime.of(2026, 5, 28, 16, 0)
        )

        val archivePath = reporter.archiveReport(report)

        val archiveDir = File(archivePath)
        assertTrue(archiveDir.isDirectory, "Archive dir should exist")
        assertTrue(File(archiveDir, "brain-dump-original.adoc").isFile)
        assertTrue(File(archiveDir, "cascade-report.json").isFile)
        assertTrue(File(archiveDir, "cascade-report.adoc").isFile)

        val originalContent = File(archiveDir, "brain-dump-original.adoc").readText()
        assertEquals("Brain dump", originalContent)
    }
}
