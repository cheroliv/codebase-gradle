package codebase.scenarios

import codebase.rag.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StimulusCascadeTest {

    @TempDir
    lateinit var tmpDir: Path

    private fun testRecord(
        id: String, title: String, content: String, target: TargetDocument?, section: String
    ): DilutionRecord {
        val dt = if (target != null) DilutionTarget(target, section, "Test rationale") else null
        val classification = if (target != null) ContentClassification.VISION else ContentClassification.OPINION
        return DilutionRecord(id, title, content, classification, 0.95, dt, "Test rationale", LocalDateTime.now())
    }

    @Test
    fun `S1 DilutionExecutor injects VISION section with metadata and backup`() {
        val docFile = File(tmpDir.toFile(), "WORKSPACE_VISION.adoc")
        docFile.writeText("""
= Document De Test
:jbake-status: draft

== Introduction
Premiere section.

== Session du Test

=== Stimuli dilues dans ce document
[cols="1,1,3"]
|===
| Stimulus | Date | Sections enrichies
| EXISTANT.adoc | 10 May 2026 | Section existante
|===
""".trimStart(), Charsets.UTF_8)

        val executor = DilutionExecutor(tmpDir.toFile().absolutePath)
        val record = testRecord("S1", "Section de test",
            "Ceci est le contenu de la section.\n\nEnrichi par STIMULUS.",
            TargetDocument.WORKSPACE_VISION, "== Session du Test")

        val result = executor.execute(record)
        assertTrue(result.success, "Dilution should succeed: ${result.error}")
        assertTrue(result.backupPath != null, "Backup should be created")

        val content = docFile.readText(Charsets.UTF_8)
        assertTrue(content.contains("=== Section de test"), "Should contain injected section title")
        assertTrue(content.contains("Enrichi par STIMULUS"), "Should contain injected content")
        assertTrue(content.contains("0,95") || content.contains("0.95"), "Should contain confidence score")
        assertTrue(!content.contains("Echec"), "Should not contain error markers")
    }

    @Test
    fun `S2 DilutionExecutor creates backup and preserves original content structure`() {
        val docFile = File(tmpDir.toFile(), "WORKSPACE_ORGANIZATION.adoc")
        val initial = """
= Document Initial
:jbake-status: draft

== Architecture
Contenu important original.

== Principes
Neuf principes fondateurs.
""".trimStart()
        docFile.writeText(initial, Charsets.UTF_8)

        val executor = DilutionExecutor(tmpDir.toFile().absolutePath)
        val record = testRecord("S2", "Section princ",
            "Nouveau contenu insere dans principes.",
            TargetDocument.WORKSPACE_ORGANIZATION, "== Principes")

        val result = executor.execute(record)
        assertTrue(result.success, "Dilution should succeed: ${result.error}")
        assertTrue(result.backupPath != null, "Backup should be created")

        val content = docFile.readText(Charsets.UTF_8)
        assertTrue(content.contains("Contenu important original"),
            "Original content must be preserved")
        assertTrue(content.contains("=== Section princ"),
            "Injected section should appear")
        assertTrue(content.length > initial.length,
            "Content should have grown after injection")
    }

    @Test
    fun `S3 DRY RUN preserves original file content completely`() {
        val docFile = File(tmpDir.toFile(), "WORKSPACE_ORGANIZATION.adoc")
        val initial = """
= Document Initial
:jbake-status: draft

== Architecture
Contenu important original.

== Principes
Neuf principes fondateurs.
""".trimStart()
        docFile.writeText(initial, Charsets.UTF_8)

        val executor = DilutionExecutor(tmpDir.toFile().absolutePath, dryRun = true)
        val record = testRecord("S3", "Section dry run",
            "Contenu qui ne doit pas etre injecte.",
            TargetDocument.WORKSPACE_ORGANIZATION, "== Principes")

        val result = executor.execute(record)
        assertTrue(result.success, "DRY RUN should succeed")

        val content = docFile.readText(Charsets.UTF_8)
        assertEquals(initial.trim(), content.trim(),
            "DRY RUN must not modify file content")
    }

    @Test
    fun `S4 StimulusDetector detects active stimuli`() {
        for (i in 1..4) {
            File(tmpDir.toFile(), "STIMULUS_0${i}.adoc").writeText(
                "= Brain Dump $i\n\nContenu du stimulus $i.", Charsets.UTF_8)
            Thread.sleep(5)
        }

        val detector = StimulusDetector(tmpDir.toFile().absolutePath)
        val allFiles = detector.scan()
        assertEquals(4, allFiles.size, "All 4 stimulus files should be found")

        val active = detector.detectActive()
        assertEquals(4, active.size, "All 4 stimuli should be active (none diluted)")

        val report = detector.reportAsciiDoc(active)
        assertTrue(report.contains("STIMULUS_01"), "Report should list STIMULUS_01")
        assertTrue(report.contains("Stimuli actifs"), "Report should have correct header")
    }

    @Test
    fun `S5 StimulusDetector detects diluted stimuli as excluded from active`() {
        for (i in 1..3) {
            File(tmpDir.toFile(), "STIMULUS_0${i}.adoc").writeText(
                "= Brain Dump $i\n\nContenu.", Charsets.UTF_8)
        }

        File(tmpDir.toFile(), "WORKSPACE_VISION.adoc").writeText("""
=== Stimuli dilués dans ce document
[cols="1,1,3"]
|===
| Stimulus | Date | Sections enrichies
| STIMULUS_01.adoc | 12 May 2026 | Test
|===
""".trimStart(), Charsets.UTF_8)

        val detector = StimulusDetector(tmpDir.toFile().absolutePath)
        val active = detector.detectActive()
        assertEquals(2, active.size,
            "2 stimuli (02 and 03) should be active, STIMULUS_01 is diluted. Active: ${active.map { it.file.name }}")

        val names = active.map { it.file.name }
        assertTrue(names.contains("STIMULUS_02.adoc"), "STIMULUS_02 should be active")
        assertTrue(names.contains("STIMULUS_03.adoc"), "STIMULUS_03 should be active")
    }

    @Test
    fun `S6 StimulusDetector detects stale stimuli`() {
        val recent = File(tmpDir.toFile(), "STIMULUS_RECENT.adoc")
        recent.writeText("= Recent\n\nContent.", Charsets.UTF_8)
        recent.setLastModified(System.currentTimeMillis())

        val old = File(tmpDir.toFile(), "STIMULUS_OLD.adoc")
        old.writeText("= Old\n\nContent.", Charsets.UTF_8)
        old.setLastModified(System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000L)

        val detector = StimulusDetector(tmpDir.toFile().absolutePath)
        val active = detector.detectActive()
        val stale = detector.detectStale()

        assertEquals(2, active.size, "Both stimuli should be active")
        assertEquals(1, stale.size, "One old stimulus should be stale")
        assertEquals("STIMULUS_OLD.adoc", stale.first().file.name, "Old file should be stale")
        assertTrue(stale.first().ageDays >= 4, "Age should be at least 4 days, got ${stale.first().ageDays}")
    }

    @Test
    fun `S7 DilutionExecutor rejects OPINION sections`() {
        val record = testRecord("S7", "Opinion", "Ceci est une opinion.", null, "")

        val executor = DilutionExecutor(tmpDir.toFile().absolutePath)
        val result = executor.execute(record)
        assertTrue(!result.success, "OPINION section should be rejected")
        assertTrue(result.error?.contains("OPINION") == true, "Error should mention OPINION")
    }

    @Test
    fun `S8 DilutionExecutor reports error for missing target file`() {
        val record = testRecord("S8", "Ghost", "Contenu.",
            TargetDocument.WORKSPACE_AS_PRODUCT, "== Intro")

        val executor = DilutionExecutor(tmpDir.toFile().absolutePath)
        val result = executor.execute(record)
        assertTrue(!result.success, "Missing file should fail")
        assertTrue(result.error?.contains("introuvable") == true,
            "Error should mention missing file: ${result.error}")
    }
}
