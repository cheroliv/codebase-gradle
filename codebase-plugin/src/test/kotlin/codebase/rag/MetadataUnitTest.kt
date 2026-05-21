package codebase.rag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.*

class MetadataUnitTest {

    @Test
    fun `fromJson should parse QuizMetadata correctly`() {
        val json = """{"type":"Quiz","source":"bronx","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"questions":15,"bareme":20,"dureeMax":"30min"}"""

        val meta = Metadata.fromJson(json)

        assertIs<QuizMetadata>(meta)
        assertEquals(15, meta.questions)
        assertEquals(20, meta.bareme)
        assertEquals("30min", meta.dureeMax)
    }

    @Test
    fun `fromJson should parse PlanMetadata correctly`() {
        val json = """{"type":"Plan","source":"codebase","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":["queens"],"epics":3,"totalPoints":21,"classification":"complexe","estimatedSessions":"3-5"}"""

        val meta = Metadata.fromJson(json)

        assertIs<PlanMetadata>(meta)
        assertEquals(3, meta.epics)
        assertEquals(21, meta.totalPoints)
        assertEquals("complexe", meta.classification)
        assertEquals("3-5", meta.estimatedSessions)
    }

    @Test
    fun `fromJson should parse PDFMetadata correctly`() {
        val json = """{"type":"PDF","source":"brooklyn","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"pages":240,"taille":"15MB","sourceCorpus":"tech-ref"}"""

        val meta = Metadata.fromJson(json)

        assertIs<PDFMetadata>(meta)
        assertEquals(240, meta.pages)
        assertEquals("15MB", meta.taille)
        assertEquals("tech-ref", meta.sourceCorpus)
    }

    @Test
    fun `fromJson should parse unknown type as UnknownMetadata`() {
        val json = """{"type":"INCONNU","source":"test","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[]}"""

        val meta = Metadata.fromJson(json)

        assertIs<UnknownMetadata>(meta)
        assertEquals("INCONNU", meta.type)
    }

    @Test
    fun `fromJson should parse SPG type as UnknownMetadata in codebase`() {
        val json = """{"type":"SPG","source":"newark","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"sessions":24}"""

        val meta = Metadata.fromJson(json)

        assertIs<UnknownMetadata>(meta)
        assertEquals("SPG", meta.type)
    }

    @Test
    fun `fromFile should read metadata from a file`(@TempDir tempDir: Path) {
        val json = """{"type":"Plan","source":"codebase","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"epics":2,"totalPoints":8,"classification":"simple","estimatedSessions":"1-2"}"""
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText(json)

        val meta = Metadata.fromFile(file)

        assertIs<PlanMetadata>(meta)
        assertEquals(2, meta.epics)
    }

    @Test
    fun `PlanMetadata should have correct defaults`() {
        val json = """{"type":"Plan","source":"codebase","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"epics":2,"totalPoints":8,"classification":"simple","estimatedSessions":"1-2"}"""

        val meta = Metadata.fromJson(json)

        assertIs<PlanMetadata>(meta)
        assertEquals(2, meta.epics)
        assertEquals(8, meta.totalPoints)
        assertEquals("simple", meta.classification)
        assertEquals("1-2", meta.estimatedSessions)
    }
}
