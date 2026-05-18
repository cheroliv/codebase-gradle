package codebase.rag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.*

class MetadataUnitTest {

    @Test
    fun `fromJson should parse SPGMetadata correctly`() {
        val json = """{"type":"SPG","source":"manhattan","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":["queens","graphify"],"sessions":24,"modules":["accueil","core"],"competences":["C1","C2"]}"""

        val meta = Metadata.fromJson(json)

        assertIs<SPGMetadata>(meta)
        assertEquals("manhattan", meta.source)
        assertEquals("SPG", meta.type)
        assertEquals("1.0", meta.version)
        assertEquals("pro", meta.model)
        assertEquals(listOf("queens", "graphify"), meta.dependencies)
        assertEquals(24, meta.sessions)
        assertEquals(listOf("accueil", "core"), meta.modules)
        assertEquals(listOf("C1", "C2"), meta.competences)
    }

    @Test
    fun `fromJson should parse SPDMetadata correctly`() {
        val json = """{"type":"SPD","source":"newark","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"flash","dependencies":["manhattan"],"sessionNumber":5,"duree":"3h","prerequis":["SPG validé"],"objectifs":["maîtriser Kotlin"]}"""

        val meta = Metadata.fromJson(json)

        assertIs<SPDMetadata>(meta)
        assertEquals("newark", meta.source)
        assertEquals(5, meta.sessionNumber)
        assertEquals("3h", meta.duree)
        assertEquals(listOf("SPG validé"), meta.prerequis)
    }

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
        val json = """{"type":"PDF","source":"brooklyn","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"pages":240,"taille":"15MB","sourceCorpus":"AFNOR"}"""

        val meta = Metadata.fromJson(json)

        assertIs<PDFMetadata>(meta)
        assertEquals(240, meta.pages)
        assertEquals("15MB", meta.taille)
        assertEquals("AFNOR", meta.sourceCorpus)
    }

    @Test
    fun `fromJson should throw on unknown type`() {
        val json = """{"type":"INCONNU","source":"test","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[]}"""

        assertThrows<Exception> { Metadata.fromJson(json) }
    }

    @Test
    fun `fromFile should read metadata from a file`(@TempDir tempDir: Path) {
        val json = """{"type":"SPG","source":"manhattan","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"sessions":24}"""
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText(json)

        val meta = Metadata.fromFile(file)

        assertIs<SPGMetadata>(meta)
        assertEquals(24, meta.sessions)
    }

    @Test
    fun `SPGMetadata should have empty defaults for missing fields`() {
        val json = """{"type":"SPG","source":"test","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"sessions":10}"""

        val meta = Metadata.fromJson(json)

        assertIs<SPGMetadata>(meta)
        assertEquals(emptyList(), meta.modules)
        assertEquals(emptyList(), meta.competences)
    }
}
