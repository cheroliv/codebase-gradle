package codebase.rag

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StimulusCascadeTest {

    private val cascade = StimulusCascade(dryRun = true)

    // ══════ exportCascadeJson ══════

    @Test
    fun `exportCascadeJson produces valid JSON with empty sections`() {
        val report = StimulusCascadeReport(
            source = "test brain dump",
            sourceHash = "abcdef1234567890",
            sections = emptyList(),
            visionCount = 0,
            opinionCount = 0,
            dilutedCount = 0,
            timestamp = LocalDateTime.of(2026, 5, 28, 14, 30, 0)
        )

        val json = cascade.exportCascadeJson(report)

        assertTrue(json.contains("\"totalSections\": 0"))
        assertTrue(json.contains("\"visionCount\": 0"))
        assertTrue(json.contains("\"opinionCount\": 0"))
        assertTrue(json.contains("\"dilutedCount\": 0"))
        assertTrue(json.contains("\"sourceHash\": \"abcdef1234567890\""))
        assertTrue(json.contains("\"timestamp\""))
    }

    @Test
    fun `exportCascadeJson includes timestamp in ISO format`() {
        val report = StimulusCascadeReport(
            source = "test",
            sourceHash = "hash",
            timestamp = LocalDateTime.of(2026, 1, 15, 10, 30, 45)
        )

        val json = cascade.exportCascadeJson(report)

        assertTrue(json.contains("2026-01-15T10:30:45"))
    }

    @Test
    fun `exportCascadeJson includes VISION section with dilution target`() {
        val target = DilutionTarget(
            targetDocument = TargetDocument.WORKSPACE_VISION,
            suggestedSection = "== Architecture DAG",
            rationale = "Contenu architectural valide"
        )
        val record = DilutionRecord(
            sectionId = "S1",
            sectionTitle = "Architecture N0-N3",
            content = "Le DAG a 4 niveaux.",
            classification = ContentClassification.VISION,
            confidence = 0.95,
            dilutionTarget = target,
            classificationRationale = "Vision claire",
            timestamp = LocalDateTime.of(2026, 5, 28, 12, 0)
        )
        val report = StimulusCascadeReport(
            source = "brain dump",
            sourceHash = "abc",
            sections = listOf(record),
            visionCount = 1,
            opinionCount = 0,
            dilutedCount = 1
        )

        val json = cascade.exportCascadeJson(report)

        assertTrue(json.contains("\"sectionId\": \"S1\""))
        assertTrue(json.contains("\"classification\": \"VISION\""))
        assertTrue(json.contains("\"confidence\": 0.95"))
        assertTrue(json.contains("\"document\": \"WORKSPACE_VISION.adoc\""))
        assertTrue(json.contains("\"section\": \"== Architecture DAG\""))
        assertTrue(json.contains("\"visionCount\": 1"))
    }

    @Test
    fun `exportCascadeJson includes OPINION section with null dilution target`() {
        val record = DilutionRecord(
            sectionId = "O1",
            sectionTitle = "Préférence Kotlin",
            content = "Je préfère Kotlin.",
            classification = ContentClassification.OPINION,
            confidence = 0.72,
            dilutionTarget = null,
            classificationRationale = "Opinion subjective",
            timestamp = LocalDateTime.of(2026, 5, 28, 12, 0)
        )
        val report = StimulusCascadeReport(
            source = "brain dump",
            sourceHash = "abc",
            sections = listOf(record),
            visionCount = 0,
            opinionCount = 1,
            dilutedCount = 0
        )

        val json = cascade.exportCascadeJson(report)

        assertTrue(json.contains("\"classification\": \"OPINION\""))
        assertTrue(json.contains("\"dilutionTarget\": null"))
        assertTrue(json.contains("\"opinionCount\": 1"))
    }

    @Test
    fun `exportCascadeJson handles multiple sections with proper JSON commas`() {
        val vRecord = DilutionRecord(
            sectionId = "S1", sectionTitle = "V", content = "v",
            classification = ContentClassification.VISION, confidence = 0.9,
            dilutionTarget = DilutionTarget(TargetDocument.WORKSPACE_VISION, "== Test", "ok"),
            classificationRationale = "r", timestamp = LocalDateTime.now()
        )
        val oRecord = DilutionRecord(
            sectionId = "O1", sectionTitle = "O", content = "o",
            classification = ContentClassification.OPINION, confidence = 0.6,
            dilutionTarget = null, classificationRationale = "r2", timestamp = LocalDateTime.now()
        )
        val report = StimulusCascadeReport(
            source = "test", sourceHash = "abc",
            sections = listOf(vRecord, oRecord),
            visionCount = 1, opinionCount = 1, dilutedCount = 1
        )

        val json = cascade.exportCascadeJson(report)

        assertTrue(json.contains("\"totalSections\": 2"))
        // Vérifie que le JSON est syntaxiquement valide (ouvre/ferme correctement)
        assertTrue(json.startsWith("{"))
        assertTrue(json.trimEnd().endsWith("}"))
    }

    // ══════ exportCascadeAsciiDoc ══════

    @Test
    fun `exportCascadeAsciiDoc contains report title and metadata`() {
        val report = StimulusCascadeReport(
            source = "test source",
            sourceHash = "abc123def456",
            sections = emptyList(),
            timestamp = LocalDateTime.of(2026, 5, 28, 14, 0)
        )

        val adoc = cascade.exportCascadeAsciiDoc(report)

        assertTrue(adoc.contains("= EPIC 10 — STIMULUS Cascade Report"))
        assertTrue(adoc.contains(":toc: left"))
        assertTrue(adoc.contains("Pipeline STIMULUS"))
        assertTrue(adoc.contains("abc123def456")) // hash partiel
        assertTrue(adoc.contains("Synthese du Pipeline"))
    }

    @Test
    fun `exportCascadeAsciiDoc metadata table shows correct counts`() {
        val report = StimulusCascadeReport(
            source = "test", sourceHash = "hash123",
            sections = listOf(
                DilutionRecord("S1", "V", "c", ContentClassification.VISION, 0.9,
                    DilutionTarget(TargetDocument.WORKSPACE_VISION, "== S", "r"), "r",
                    LocalDateTime.of(2026, 5, 28, 12, 0)),
                DilutionRecord("S2", "V2", "c2", ContentClassification.VISION, 0.8,
                    DilutionTarget(TargetDocument.WORKSPACE_AS_PRODUCT, "== P", "r2"), "r2",
                    LocalDateTime.of(2026, 5, 28, 12, 0)),
                DilutionRecord("O1", "O", "o", ContentClassification.OPINION, 0.5, null, "r3",
                    LocalDateTime.of(2026, 5, 28, 12, 0)),
            ),
            visionCount = 2, opinionCount = 1, dilutedCount = 2,
            timestamp = LocalDateTime.of(2026, 5, 28, 14, 0)
        )

        val adoc = cascade.exportCascadeAsciiDoc(report)

        assertTrue(adoc.contains("| Sections totales | 3"))
        assertTrue(adoc.contains("| VISION (diluees) | 2"))
        assertTrue(adoc.contains("| OPINION (confinees) | 1"))
    }

    @Test
    fun `exportCascadeAsciiDoc shows impacted documents table`() {
        val report = StimulusCascadeReport(
            source = "test", sourceHash = "h",
            sections = listOf(
                DilutionRecord("S1", "V", "c", ContentClassification.VISION, 0.9,
                    DilutionTarget(TargetDocument.WORKSPACE_VISION, "== A", "r"), "r",
                    LocalDateTime.now()),
                DilutionRecord("S2", "V2", "c2", ContentClassification.VISION, 0.8,
                    DilutionTarget(TargetDocument.WORKSPACE_VISION, "== B", "r2"), "r2",
                    LocalDateTime.now()),
                DilutionRecord("S3", "V3", "c3", ContentClassification.VISION, 0.7,
                    DilutionTarget(TargetDocument.WHAT_THE_GAMES_BEEN_MISSING, "== C", "r3"), "r3",
                    LocalDateTime.now()),
            ),
            visionCount = 3, opinionCount = 0, dilutedCount = 3
        )

        val adoc = cascade.exportCascadeAsciiDoc(report)

        assertTrue(adoc.contains("Documents cibles impactes"))
        assertTrue(adoc.contains("WORKSPACE_VISION.adoc"))
        assertTrue(adoc.contains("WHAT_THE_GAMES_BEEN_MISSING.adoc"))
        assertTrue(adoc.contains("| 2")) // 2 sections pour WORKSPACE_VISION
    }

    @Test
    fun `exportCascadeAsciiDoc classification table shows all sections`() {
        val report = StimulusCascadeReport(
            source = "test", sourceHash = "h",
            sections = listOf(
                DilutionRecord("S1", "Titre Vision", "c1", ContentClassification.VISION, 0.95,
                    DilutionTarget(TargetDocument.WORKSPACE_VISION, "== Test", "r"), "r",
                    LocalDateTime.now()),
                DilutionRecord("O1", "Titre Opinion", "c2", ContentClassification.OPINION, 0.60,
                    null, "opinion", LocalDateTime.now()),
            ),
            visionCount = 1, opinionCount = 1, dilutedCount = 1
        )

        val adoc = cascade.exportCascadeAsciiDoc(report)

        assertTrue(adoc.contains("Classification par Section"))
        assertTrue(adoc.contains("| S1 | Titre Vision | VISION | 0,95"))
        assertTrue(adoc.contains("| O1 | Titre Opinion | OPINION | 0,60"))
    }

    @Test
    fun `exportCascadeAsciiDoc shows routing decisions for VISION sections`() {
        val target = DilutionTarget(
            TargetDocument.WORKSPACE_AS_PRODUCT,
            "== Business Model",
            "Contenu business stratégique"
        )
        val report = StimulusCascadeReport(
            source = "test", sourceHash = "h",
            sections = listOf(
                DilutionRecord("S1", "Business Model", "Le business model est...",
                    ContentClassification.VISION, 0.92, target, "Vision business",
                    LocalDateTime.now()),
            ),
            visionCount = 1, opinionCount = 0, dilutedCount = 1
        )

        val adoc = cascade.exportCascadeAsciiDoc(report)

        assertTrue(adoc.contains("Decisions de Routage"))
        assertTrue(adoc.contains("=== S1 — Business Model"))
        assertTrue(adoc.contains("WORKSPACE_AS_PRODUCT.adoc"))
        assertTrue(adoc.contains("== Business Model"))
        assertTrue(adoc.contains("Contenu business stratégique"))
        assertTrue(adoc.contains("0,92"))
    }

    @Test
    fun `exportCascadeAsciiDoc shows confined OPINION sections`() {
        val report = StimulusCascadeReport(
            source = "test", sourceHash = "h",
            sections = listOf(
                DilutionRecord("O1", "Opinion Kotlin", "Je pense que Kotlin est mieux.",
                    ContentClassification.OPINION, 0.70, null, "Préférence personnelle",
                    LocalDateTime.now()),
            ),
            visionCount = 0, opinionCount = 1, dilutedCount = 0
        )

        val adoc = cascade.exportCascadeAsciiDoc(report)

        assertTrue(adoc.contains("Sections Confinees (OPINION)"))
        assertTrue(adoc.contains("=== O1 — Opinion Kotlin"))
        assertTrue(adoc.contains("Je pense que Kotlin est mieux."))
        assertTrue(adoc.contains("0,70"))
        assertTrue(adoc.contains("Préférence personnelle"))
    }

    @Test
    fun `exportCascadeAsciiDoc empty opinions shows placeholder`() {
        val report = StimulusCascadeReport(
            source = "test", sourceHash = "h",
            sections = listOf(
                DilutionRecord("S1", "V", "c", ContentClassification.VISION, 0.9,
                    DilutionTarget(TargetDocument.WORKSPACE_VISION, "== T", "r"), "r",
                    LocalDateTime.now()),
            ),
            visionCount = 1, opinionCount = 0, dilutedCount = 1
        )

        val adoc = cascade.exportCascadeAsciiDoc(report)

        assertTrue(adoc.contains("_Aucune section OPINION dans ce brain dump._"))
    }

    @Test
    fun `exportCascadeAsciiDoc includes source code block with content`() {
        val report = StimulusCascadeReport(
            source = "test", sourceHash = "h",
            sections = listOf(
                DilutionRecord("S1", "Titre", "Contenu de la section à afficher".repeat(10),
                    ContentClassification.VISION, 0.85,
                    DilutionTarget(TargetDocument.WORKSPACE_VISION, "== S", "r"), "r",
                    LocalDateTime.now()),
            ),
            visionCount = 1, opinionCount = 0, dilutedCount = 1
        )

        val adoc = cascade.exportCascadeAsciiDoc(report)

        assertTrue(adoc.contains("[source,asciidoc]"))
        assertTrue(adoc.contains("----"))
        assertTrue(adoc.contains("Contenu de la section")) // extrait du contenu
    }

    // ══════ Data classes et enum tests ══════

    @Test
    fun `TargetDocument enum has all 4 values`() {
        assertEquals(4, TargetDocument.entries.size)
    }

    @Test
    fun `DilutionTarget stores document section and rationale`() {
        val target = DilutionTarget(
            targetDocument = TargetDocument.WORKSPACE_AS_PRODUCT,
            suggestedSection = "== Pricing Strategy",
            rationale = "Contenu stratégique business"
        )

        assertEquals(TargetDocument.WORKSPACE_AS_PRODUCT, target.targetDocument)
        assertEquals("== Pricing Strategy", target.suggestedSection)
        assertEquals("Contenu stratégique business", target.rationale)
    }

    @Test
    fun `DilutionRecord with null dilution target is OPINION style`() {
        val record = DilutionRecord(
            sectionId = "O1", sectionTitle = "Opinion", content = "Je pense...",
            classification = ContentClassification.OPINION, confidence = 0.5,
            dilutionTarget = null, classificationRationale = "subjectif",
            timestamp = LocalDateTime.of(2026, 5, 28, 12, 0)
        )

        assertEquals("O1", record.sectionId)
        assertEquals(null, record.dilutionTarget)
        assertEquals(ContentClassification.OPINION, record.classification)
    }

    @Test
    fun `StimulusCascadeReport default values are zero`() {
        val report = StimulusCascadeReport(source = "test", sourceHash = "h")

        assertEquals(0, report.sections.size)
        assertEquals(0, report.visionCount)
        assertEquals(0, report.opinionCount)
        assertEquals(0, report.dilutedCount)
    }

    @Test
    fun `ParsedSection stores id title and content`() {
        val section = StimulusCascade.ParsedSection("S2", "Titre", "Contenu texte")

        assertEquals("S2", section.id)
        assertEquals("Titre", section.title)
        assertEquals("Contenu texte", section.content)
    }

    // ══════ parseSections ══════

    @Test
    fun `parseSections extracts headings from AsciiDoc text`() {
        val text = """
            = Brain Dump
            == Vision Architecture
            Le DAG a 4 niveaux N0-N3.
            Chaque plugin ne référence que des plugins inférieurs.
            == Opinions libres
            Je pense que Kotlin est mieux que Java pour les DSL.
            Franchement, c'est plus lisible.
            == Organisation des fichiers
            Le workspace est structuré en cercles de confiance.
        """.trimIndent()

        val sections = cascade.parseSections(text)

        assertEquals(3, sections.size)
        assertEquals("S1", sections[0].id)
        assertEquals("Vision Architecture", sections[0].title)
        assertTrue(sections[0].content.contains("DAG a 4 niveaux"))
        assertEquals("S2", sections[1].id)
        assertEquals("Opinions libres", sections[1].title)
        assertTrue(sections[1].content.contains("Kotlin"))
        assertEquals("S3", sections[2].id)
        assertEquals("Organisation des fichiers", sections[2].title)
    }

    @Test
    fun `parseSections returns single section for text without headings`() {
        val text = "Ceci est un texte simple sans headings AsciiDoc."

        val sections = cascade.parseSections(text)
        assertEquals(1, sections.size)
        assertEquals("S1", sections[0].id)
        assertEquals("Ceci est un texte simple sans headings AsciiDoc.", sections[0].title)
        assertEquals(text, sections[0].content)
    }

    @Test
    fun `parseSections handles text with only level-1 heading`() {
        val text = "= Titre Principal\nContenu sans sous-sections."

        val sections = cascade.parseSections(text)
        assertEquals(1, sections.size)
        assertEquals("S1", sections[0].id)
        assertEquals("Titre Principal", sections[0].title) // removePrefix("= ") applied
    }

    @Test
    fun `parseSections preserves section boundaries between headings`() {
        val text = """
            == Section A
            Texte de la section A.
            Plusieurs lignes dans A.
            == Section B
            Texte de B, une seule ligne.
            == Section C
            Texte de C.
            Sur deux lignes.
        """.trimIndent()

        val sections = cascade.parseSections(text)
        assertEquals(3, sections.size)
        assertEquals("S1", sections[0].id)
        assertTrue(sections[0].content.startsWith("Texte de la section A"))
        assertEquals("S2", sections[1].id)
        assertTrue(sections[1].content.contains("Texte de B"))
        assertEquals("S3", sections[2].id)
        assertTrue(sections[2].content.contains("Texte de C"))
    }

    // ══════ parseRoutingResponse ══════

    @Test
    fun `parseRoutingResponse routes to WORKSPACE_VISION from JSON`() {
        val section = StimulusCascade.ParsedSection("S1", "Architecture", "contenu")
        val raw = """{"targetDocument": "WORKSPACE_VISION", "suggestedSection": "== DAG N0-N3", "rationale": "Contenu architectural"}"""

        val result = cascade.parseRoutingResponse(section, raw)

        assertEquals(TargetDocument.WORKSPACE_VISION, result.targetDocument)
        assertEquals("== DAG N0-N3", result.suggestedSection)
        assertEquals("Contenu architectural", result.rationale)
    }

    @Test
    fun `parseRoutingResponse routes to WORKSPACE_AS_PRODUCT`() {
        val section = StimulusCascade.ParsedSection("S2", "Business", "contenu")
        val raw = """{"targetDocument": "WORKSPACE_AS_PRODUCT", "suggestedSection": "== Pricing", "rationale": "Stratégie business"}"""

        val result = cascade.parseRoutingResponse(section, raw)
        assertEquals(TargetDocument.WORKSPACE_AS_PRODUCT, result.targetDocument)
    }

    @Test
    fun `parseRoutingResponse routes to WHAT_THE_GAMES_BEEN_MISSING`() {
        val section = StimulusCascade.ParsedSection("S3", "Idée", "contenu")
        val raw = """{"targetDocument": "WHAT_THE_GAMES_BEEN_MISSING", "suggestedSection": "== Idées radicales", "rationale": "Brainstorming"}"""

        val result = cascade.parseRoutingResponse(section, raw)
        assertEquals(TargetDocument.WHAT_THE_GAMES_BEEN_MISSING, result.targetDocument)
    }

    @Test
    fun `parseRoutingResponse routes to WORKSPACE_ORGANIZATION`() {
        val section = StimulusCascade.ParsedSection("S4", "Structure", "contenu")
        val raw = """{"targetDocument": "WORKSPACE_ORGANIZATION", "suggestedSection": "== Inventaire", "rationale": "Organisation"}"""

        val result = cascade.parseRoutingResponse(section, raw)
        assertEquals(TargetDocument.WORKSPACE_ORGANIZATION, result.targetDocument)
    }

    @Test
    fun `parseRoutingResponse falls back to WORKSPACE_VISION for unknown target`() {
        val section = StimulusCascade.ParsedSection("S5", "Inconnu", "contenu")
        val raw = """{"targetDocument": "UNKNOWN_DOC", "suggestedSection": "== Test", "rationale": "?"}"""

        val result = cascade.parseRoutingResponse(section, raw)
        assertEquals(TargetDocument.WORKSPACE_VISION, result.targetDocument)
    }

    @Test
    fun `parseRoutingResponse handles missing fields with defaults`() {
        val section = StimulusCascade.ParsedSection("S6", "Minimal", "contenu")
        val raw = """{"targetDocument": "WORKSPACE_VISION"}"""

        val result = cascade.parseRoutingResponse(section, raw)
        assertEquals(TargetDocument.WORKSPACE_VISION, result.targetDocument)
        assertTrue(result.suggestedSection.contains("S6"), "Should contain section ID")
        assertEquals("routage automatique", result.rationale)
    }

    @Test
    fun `parseRoutingResponse strips markdown code fences`() {
        val section = StimulusCascade.ParsedSection("S7", "Code", "contenu")
        val raw = """
            ```json
            {"targetDocument": "WORKSPACE_VISION", "suggestedSection": "== Gov", "rationale": "Rules"}
            ```
        """.trimIndent()

        val result = cascade.parseRoutingResponse(section, raw)
        assertEquals(TargetDocument.WORKSPACE_VISION, result.targetDocument)
        assertEquals("== Gov", result.suggestedSection)
    }

    // ══════ sha256 ══════

    @Test
    fun `sha256 produces 64-char hex string`() {
        val hash = cascade.sha256("hello world")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun `sha256 is deterministic`() {
        val h1 = cascade.sha256("test")
        val h2 = cascade.sha256("test")
        assertEquals(h1, h2)
    }

    @Test
    fun `sha256 produces different hashes for different inputs`() {
        val h1 = cascade.sha256("test")
        val h2 = cascade.sha256("other")
        assertTrue(h1 != h2)
    }

    // ══════ archiveReport ══════

    @Test
    fun `archiveReport creates directory with timestamp and saves files`(@TempDir tempDir: File) {
        val cascadeWithRoot = StimulusCascade(workspaceRoot = tempDir.absolutePath, dryRun = true)
        val report = StimulusCascadeReport(
            source = "= Brain dump test\n== Section 1\ncontenu",
            sourceHash = "abc123",
            sections = listOf(
                DilutionRecord("S1", "Section 1", "contenu", ContentClassification.VISION, 0.9,
                    DilutionTarget(TargetDocument.WORKSPACE_VISION, "== Test", "ok"), "r",
                    LocalDateTime.of(2026, 5, 28, 14, 0)),
            ),
            visionCount = 1, opinionCount = 0, dilutedCount = 1,
            timestamp = LocalDateTime.of(2026, 5, 28, 14, 30, 0)
        )

        val archivePath = cascadeWithRoot.archiveReport(report)

        val archiveDir = File(archivePath)
        assertTrue(archiveDir.isDirectory, "Archive directory should exist")
        assertTrue(archiveDir.name.contains("2026"), "Should contain year")

        val brainDumpFile = File(archiveDir, "brain-dump-original.adoc")
        assertTrue(brainDumpFile.isFile)
        assertTrue(brainDumpFile.readText().contains("Brain dump test"))

        val jsonFile = File(archiveDir, "cascade-report.json")
        assertTrue(jsonFile.isFile)
        assertTrue(jsonFile.readText().contains("abc123"))

        val adocFile = File(archiveDir, "cascade-report.adoc")
        assertTrue(adocFile.isFile)
        assertTrue(adocFile.readText().contains("EPIC 10"))
    }

    // ══════ dilutionResults ══════

    @Test
    fun `dilutionResults initially empty`() {
        val c = StimulusCascade(dryRun = true)
        assertTrue(c.dilutionResults().isEmpty())
    }
}
