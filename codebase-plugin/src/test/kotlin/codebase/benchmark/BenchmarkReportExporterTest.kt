package codebase.benchmark

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BenchmarkReportExporterTest {

    private val sampleJson = """
        {
            "scenario": "spatial-perception-v1",
            "channels": ["EAGER", "RAG", "GRAPHIFY"],
            "summary": "Test de perception spatiale avec 3 seuils de tokens.",
            "results": [
                {
                    "threshold": "5000",
                    "totalSamples": 30,
                    "errorRate": 0.10,
                    "boundaryCrossings": [
                        {
                            "documentId": "C0-strategie",
                            "expectedCircle": 0,
                            "actualCircle": 1,
                            "confidenceScore": 0.87,
                            "excerpt": "WORKSPACE_AS_PRODUCT.adoc"
                        },
                        {
                            "documentId": "C4-plugin",
                            "expectedCircle": 4,
                            "actualCircle": 3,
                            "confidenceScore": 0.65,
                            "excerpt": "foundry/public/plantuml-gradle/"
                        },
                        {
                            "documentId": "C1-tokens",
                            "expectedCircle": 1,
                            "actualCircle": 2,
                            "confidenceScore": 0.72,
                            "excerpt": "configuration/codebase.yml"
                        }
                    ]
                }
            ]
        }
    """.trimIndent()

    @Test
    fun `parseObject with valid JSON 1 threshold 3 crossings`() {
        val report = BenchmarkReportExporter.parseObject(sampleJson)

        assertEquals("spatial-perception-v1", report.scenario)
        assertEquals(listOf("EAGER", "RAG", "GRAPHIFY"), report.channels)
        assertEquals("Test de perception spatiale avec 3 seuils de tokens.", report.summary)
        assertEquals(1, report.results.size)

        val threshold = report.results[0]
        assertEquals("5000", threshold.threshold)
        assertEquals(30, threshold.totalSamples)
        assertEquals(0.10, threshold.errorRate)
        assertEquals(3, threshold.boundaryCrossings.size)

        val c0 = threshold.boundaryCrossings[0]
        assertEquals("C0-strategie", c0.documentId)
        assertEquals(0, c0.expectedCircle)
        assertEquals(1, c0.actualCircle)
        assertEquals(0.87, c0.confidenceScore)
        assertEquals("WORKSPACE_AS_PRODUCT.adoc", c0.excerpt)
    }

    @Test
    fun `parseObject with empty channels and single threshold`() {
        val json = """
            {
                "scenario": "baseline-brute",
                "channels": [],
                "summary": "",
                "results": [
                    {
                        "threshold": "8000",
                        "totalSamples": 42,
                        "errorRate": 0.23,
                        "boundaryCrossings": []
                    }
                ]
            }
        """.trimIndent()

        val report = BenchmarkReportExporter.parseObject(json)

        assertEquals("baseline-brute", report.scenario)
        assertEquals(0, report.channels.size)
        assertEquals(1, report.results.size)
        assertEquals(42, report.results[0].totalSamples)
        assertEquals(0.23, report.results[0].errorRate)
    }

    @Test
    fun `parseObject ignores unknown JSON keys`() {
        val json = """
            {
                "scenario": "test",
                "unknownField": "should-be-ignored",
                "totalScenarios": 5,
                "averageErrorRate": 0.23,
                "results": [
                    {
                        "threshold": "1000",
                        "totalSamples": 10,
                        "errorRate": 0.05,
                        "boundaryCrossings": [],
                        "extraProp": true
                    }
                ]
            }
        """.trimIndent()

        val report = BenchmarkReportExporter.parseObject(json)

        assertEquals("test", report.scenario)
        assertEquals(1, report.results.size)
        assertEquals(10, report.results[0].totalSamples)
        assertEquals(0.05, report.results[0].errorRate)
    }

    @Test
    fun `exportAsciiDoc contains report title and scenario section`() {
        val result = BenchmarkReportExporter.exportAsciiDoc(sampleJson, "fallback-id")

        assertTrue(result.contains("EPIC 4 — Benchmark Perception Spatiale LLM"))
        assertTrue(result.contains("== Scenario: spatial-perception-v1"))
        assertTrue(result.contains("Test de perception spatiale avec 3 seuils de tokens."))
    }

    @Test
    fun `exportAsciiDoc contains threshold sections and names`() {
        val result = BenchmarkReportExporter.exportAsciiDoc(sampleJson, "fallback-id")

        assertTrue(result.contains("== Resultats par Seuil de Tokens"))
        assertTrue(result.contains("=== Seuil 5000"))
        assertTrue(result.contains("10") && result.contains("%"))
        assertTrue(result.contains("30"), "Should contain totalSamples count")
    }

    @Test
    fun `exportAsciiDoc uses scenarioId when scenario field is empty`() {
        val jsonWithoutScenario = """
            {
                "scenario": "",
                "channels": [],
                "summary": "",
                "results": []
            }
        """.trimIndent()

        val result = BenchmarkReportExporter.exportAsciiDoc(jsonWithoutScenario, "custom-scenario-id")

        assertTrue(result.contains("== Scenario: custom-scenario-id"))
    }

    @Test
    fun `exportAsciiDoc contains crossing details`() {
        val result = BenchmarkReportExporter.exportAsciiDoc(sampleJson, "id")

        assertTrue(result.contains("C0-strategie"))
        assertTrue(result.contains("87") && result.contains("%"))
        assertTrue(result.contains("C4-plugin"))
        assertTrue(result.contains("Franchissements de cercle"))
    }

    @Test
    fun `exportAsciiDoc shows no crossings message when empty`() {
        val jsonNoCrossings = """
            {
                "scenario": "clean-run",
                "channels": ["RAG"],
                "summary": "",
                "results": [
                    {
                        "threshold": "2000",
                        "totalSamples": 20,
                        "errorRate": 0.0,
                        "boundaryCrossings": []
                    }
                ]
            }
        """.trimIndent()

        val result = BenchmarkReportExporter.exportAsciiDoc(jsonNoCrossings, "id")

        assertTrue(result.contains("Aucun franchissement de cercle detecte"))
    }

    @Test
    fun `parseObject then exportAsciiDoc produces non-empty AsciiDoc with delimiters`() {
        val report = BenchmarkReportExporter.parseObject(sampleJson)
        assertNotNull(report)

        val adoc = BenchmarkReportExporter.exportAsciiDoc(sampleJson, report.scenario)

        assertTrue(adoc.isNotEmpty())
        assertTrue(adoc.contains("="))

        val expectedMarkers = listOf(":toc:", ":icons:", ":sectnums:", "|===", "[abstract]")
        for (marker in expectedMarkers) {
            assertTrue(adoc.contains(marker), "Missing AsciiDoc marker: $marker")
        }
    }

    @Test
    fun `exportAsciiDoc includes metadata sections`() {
        val result = BenchmarkReportExporter.exportAsciiDoc(sampleJson, "id")

        assertTrue(result.contains("== Cercles de Confiance"))
        assertTrue(result.contains("== Metrique Cle"))
        assertTrue(result.contains("== Echantillons de Test"))
        assertTrue(result.contains("Taux d'erreur de classification"))
    }

    @Test
    fun `exportAsciiDoc contains model name in metadata`() {
        val result = BenchmarkReportExporter.exportAsciiDoc(sampleJson, "id")

        assertTrue(result.contains("deepseek-v4-pro:cloud"))
    }

    @Test
    fun `exportAsciiDoc contains channel list from report`() {
        val result = BenchmarkReportExporter.exportAsciiDoc(sampleJson, "id")

        assertTrue(result.contains("EAGER, RAG, GRAPHIFY"))
    }
}
