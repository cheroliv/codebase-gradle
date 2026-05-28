package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VisionOpinionClassifierTest {

    private val classifier = VisionOpinionClassifier()

    @Test
    fun `parseResponse extracts VISION from well-formed JSON`() {
        val section = ContentSection("V1", "architecture du DAG", ContentClassification.VISION)
        val raw = """
            {
              "classification": "VISION",
              "confidence": 0.95,
              "rationale": "texte factuel décrivant l'architecture du workspace"
            }
        """.trimIndent()

        val result = classifier.parseResponse(section, raw)

        assertEquals("V1", result.sectionId)
        assertEquals(ContentClassification.VISION, result.classification)
        assertEquals(0.95, result.confidence, 0.001)
        assertEquals("texte factuel décrivant l'architecture du workspace", result.rationale)
    }

    @Test
    fun `parseResponse extracts OPINION from well-formed JSON`() {
        val section = ContentSection("O1", "je préfère Kotlin", ContentClassification.OPINION)
        val raw = """
            {
              "classification": "OPINION",
              "confidence": 0.88,
              "rationale": "exprime une préférence personnelle subjective"
            }
        """.trimIndent()

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.OPINION, result.classification)
        assertEquals(0.88, result.confidence, 0.001)
    }

    @Test
    fun `parseResponse strips markdown code fences around JSON`() {
        val section = ContentSection("V2", "cercles de confiance", ContentClassification.VISION)
        val raw = """
            ```json
            {
              "classification": "VISION",
              "confidence": 0.92,
              "rationale": "décrit une règle de gouvernance formelle"
            }
            ```
        """.trimIndent()

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.VISION, result.classification)
        assertEquals(0.92, result.confidence, 0.001)
    }

    @Test
    fun `parseResponse strips simple code fences without json lang tag`() {
        val section = ContentSection("V3", "stimulus pipeline", ContentClassification.VISION)
        val raw = """
            ```
            {
              "classification": "VISION",
              "confidence": 0.85,
              "rationale": "document factuel sur le pipeline STIMULUS"
            }
            ```
        """.trimIndent()

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.VISION, result.classification)
    }

    @Test
    fun `parseResponse compact OPINION JSON parsed correctly`() {
        val section = ContentSection("O2", "je trouve ça trop complexe", ContentClassification.OPINION)
        val raw = """{"classification": "OPINION","confidence": 0.72,"rationale": "critique subjective"}"""

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.OPINION, result.classification)
        assertEquals(0.72, result.confidence, 0.001)
    }

    @Test
    fun `parseResponse compact VISION JSON parsed correctly`() {
        val section = ContentSection("V4", "codebase role dans le DAG", ContentClassification.VISION)
        val raw = """{"classification": "VISION","confidence": 0.99,"rationale": "document fondateur"}"""

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.VISION, result.classification)
        assertEquals(0.99, result.confidence, 0.001)
    }

    @Test
    fun `parseResponse detects OPINION from plain keyword without structured JSON`() {
        val section = ContentSection("O3", "spéculation Rust", ContentClassification.OPINION)
        val raw = "Je pense que ce texte est une OPINION personnelle non validée."

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.OPINION, result.classification)
    }

    @Test
    fun `parseResponse detects VISION from plain keyword without structured JSON`() {
        val section = ContentSection("V5", "composite context architecture", ContentClassification.VISION)
        val raw = "Ce document est clairement une VISION architecturale du workspace."

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.VISION, result.classification)
    }

    @Test
    fun `parseResponse falls back to expectedClassification when no keyword`() {
        val section = ContentSection("unknown", "un texte quelconque sans mot-clé", ContentClassification.VISION)
        val raw = "Ceci est un texte neutre sans indicateur."

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.VISION, result.classification)
    }

    @Test
    fun `parseResponse falls back to OPINION expectedClassification when no keyword`() {
        val section = ContentSection("unknown-op", "texte neutre", ContentClassification.OPINION)
        val raw = "Un contenu sans classification explicite."

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.OPINION, result.classification)
    }

    @Test
    fun `parseResponse parses confidence with decimal from JSON`() {
        val section = ContentSection("C1", "test", ContentClassification.VISION)
        val raw = """{"classification": "VISION", "confidence": 0.75, "rationale": "test"}"""

        val result = classifier.parseResponse(section, raw)

        assertEquals(0.75, result.confidence, 0.001)
    }

    @Test
    fun `parseResponse returns default 0_8 confidence when field missing`() {
        val section = ContentSection("C2", "test", ContentClassification.VISION)
        val raw = """{"classification": "VISION", "rationale": "pas de confidence"}"""

        val result = classifier.parseResponse(section, raw)

        assertEquals(0.8, result.confidence, 0.001)
    }

    @Test
    fun `parseResponse returns default rationale when field missing`() {
        val section = ContentSection("C3", "test", ContentClassification.VISION)
        val raw = """{"classification": "VISION", "confidence": 0.9}"""

        val result = classifier.parseResponse(section, raw)

        assertEquals("classification automatique", result.rationale)
    }

    @Test
    fun `parseResponse preserves section content in result`() {
        val section = ContentSection("V6", "contenu original de test", ContentClassification.VISION)
        val raw = """{"classification": "VISION", "confidence": 0.5, "rationale": "test"}"""

        val result = classifier.parseResponse(section, raw)

        assertEquals("V6", result.sectionId)
        assertEquals("contenu original de test", result.content)
    }

    @Test
    fun `parseResponse preserves sectionId in result`() {
        val section = ContentSection("section-42", "du contenu", ContentClassification.OPINION)
        val raw = """{"classification": "OPINION", "confidence": 0.6, "rationale": "subjective"}"""

        val result = classifier.parseResponse(section, raw)

        assertEquals("section-42", result.sectionId)
    }

    @Test
    fun `parseResponse handles whitespace around JSON fields`() {
        val section = ContentSection("W1", "test", ContentClassification.VISION)
        val raw = """
            {
                "classification" :   "VISION",
                "confidence"    :0.91,
                "rationale"  :  "justification avec espaces"
            }
        """.trimIndent()

        val result = classifier.parseResponse(section, raw)

        assertEquals(ContentClassification.VISION, result.classification)
        assertEquals(0.91, result.confidence, 0.001)
        assertEquals("justification avec espaces", result.rationale)
    }

    // ══════ Data classes et enum tests ══════

    @Test
    fun `TestSections has 10 predefined sections`() {
        assertEquals(10, TestSections.all.size)
    }

    @Test
    fun `TestSections V1 to V5 are all VISION`() {
        val visions = TestSections.all.take(5)
        visions.forEach { section ->
            assertEquals(
                ContentClassification.VISION,
                section.expectedClassification,
                "${section.id} should be VISION"
            )
        }
    }

    @Test
    fun `TestSections O1 to O5 are all OPINION`() {
        val opinions = TestSections.all.drop(5).take(5)
        opinions.forEach { section ->
            assertEquals(
                ContentClassification.OPINION,
                section.expectedClassification,
                "${section.id} should be OPINION"
            )
        }
    }

    @Test
    fun `ClassificationReport counts vision and opinion correctly`() {
        val report = ClassificationReport(
            sections = listOf(
                SectionClassification("s1", "", ContentClassification.VISION, 0.9, ""),
                SectionClassification("s2", "", ContentClassification.VISION, 0.8, ""),
                SectionClassification("s3", "", ContentClassification.OPINION, 0.7, ""),
            ),
            visionCount = 2,
            opinionCount = 1,
            averageConfidence = 0.8,
            errors = 0
        )

        assertEquals(2, report.visionCount)
        assertEquals(1, report.opinionCount)
    }

    @Test
    fun `ClassificationReport averageConfidence stored correctly`() {
        val report = ClassificationReport(averageConfidence = 0.85, errors = 1)
        assertEquals(0.85, report.averageConfidence, 0.001)
        assertEquals(1, report.errors)
    }

    @Test
    fun `SectionClassification contains all fields`() {
        val sc = SectionClassification(
            sectionId = "sec-1", content = "contenu",
            classification = ContentClassification.VISION,
            confidence = 0.93, rationale = "raison valable"
        )

        assertEquals("sec-1", sc.sectionId)
        assertEquals("contenu", sc.content)
        assertEquals(ContentClassification.VISION, sc.classification)
        assertEquals(0.93, sc.confidence, 0.001)
        assertEquals("raison valable", sc.rationale)
    }

    @Test
    fun `ContentClassification enum VISION and OPINION values exist`() {
        assertEquals("VISION", ContentClassification.VISION.name)
        assertEquals("OPINION", ContentClassification.OPINION.name)
    }
}
