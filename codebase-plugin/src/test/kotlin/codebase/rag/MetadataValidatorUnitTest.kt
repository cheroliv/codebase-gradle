package codebase.rag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.*

class MetadataValidatorUnitTest {

    @Test
    fun `validate should return Valid for correct SPG metadata`(@TempDir tempDir: Path) {
        val json = """{"type":"SPG","source":"manhattan","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":["queens"],"sessions":24}"""
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText(json)

        val result = MetadataValidator.validate(file, expectedVersion = "1.0", expectedType = "SPG")

        assertIs<MetadataValidator.ValidationResult.Valid>(result)
        assertIs<SPGMetadata>(result.metadata)
        assertEquals(24, result.metadata.sessions)
    }

    @Test
    fun `validate should return Invalid for missing file`() {
        val file = File("/tmp/nonexistent-metadata-${System.nanoTime()}.json")

        val result = MetadataValidator.validate(file)

        assertIs<MetadataValidator.ValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("introuvable"))
    }

    @Test
    fun `validate should return Invalid for malformed JSON`(@TempDir tempDir: Path) {
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText("{pas du json valide}")

        val result = MetadataValidator.validate(file)

        assertIs<MetadataValidator.ValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("illisible"))
    }

    @Test
    fun `validate should return Invalid for version mismatch`(@TempDir tempDir: Path) {
        val json = """{"type":"SPG","source":"test","version":"2.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"sessions":24}"""
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText(json)

        val result = MetadataValidator.validate(file, expectedVersion = "1.0")

        assertIs<MetadataValidator.ValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("Version incompatible"))
    }

    @Test
    fun `validate should accept same major version`(@TempDir tempDir: Path) {
        val json = """{"type":"SPG","source":"test","version":"1.5","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"sessions":24}"""
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText(json)

        val result = MetadataValidator.validate(file, expectedVersion = "1.0")

        assertIs<MetadataValidator.ValidationResult.Valid>(result)
    }

    @Test
    fun `validate should return Invalid for type mismatch`(@TempDir tempDir: Path) {
        val json = """{"type":"SPG","source":"test","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":[],"sessions":24}"""
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText(json)

        val result = MetadataValidator.validate(file, expectedType = "SPD")

        assertIs<MetadataValidator.ValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("Type inattendu"))
    }

    @Test
    fun `validate should return Invalid for blank dependency`(@TempDir tempDir: Path) {
        val json = """{"type":"SPG","source":"test","version":"1.0","generatedAt":"2026-05-18T19:00:00Z","model":"pro","dependencies":["queens","  "],"sessions":24}"""
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText(json)

        val result = MetadataValidator.validate(file)

        assertIs<MetadataValidator.ValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("Dépendance vide"))
    }

    @Test
    fun `validate should accept without version or type checks`(@TempDir tempDir: Path) {
        val json = """{"type":"Plan","source":"codebase","version":"3.0","generatedAt":"2026-05-18T19:00:00Z","model":"flash","dependencies":["queens"],"epics":2,"totalPoints":8,"classification":"simple","estimatedSessions":"1-2"}"""
        val file = File(tempDir.toFile(), "metadata.json")
        file.writeText(json)

        val result = MetadataValidator.validate(file)

        assertIs<MetadataValidator.ValidationResult.Valid>(result)
    }

    @Test
    fun `isVersionCompatible should return true for same major`() {
        assertTrue(MetadataValidator.isVersionCompatible("1.0", "1.5"))
        assertTrue(MetadataValidator.isVersionCompatible("1.9.3", "1.0.0"))
    }

    @Test
    fun `isVersionCompatible should return false for different major`() {
        assertFalse(MetadataValidator.isVersionCompatible("2.0", "1.0"))
        assertFalse(MetadataValidator.isVersionCompatible("1.0", "2.0"))
    }

    @Test
    fun `isVersionCompatible should return false for invalid input`() {
        assertFalse(MetadataValidator.isVersionCompatible("abc", "1.0"))
        assertFalse(MetadataValidator.isVersionCompatible("1.0", "xyz"))
    }
}
