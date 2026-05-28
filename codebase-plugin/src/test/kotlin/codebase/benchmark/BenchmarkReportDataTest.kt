package codebase.benchmark

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests combinés des data classes BenchmarkReport, ThresholdData, CrossingData
 * et leur export AsciiDoc via BenchmarkReportExporter.
 */
class BenchmarkReportDataTest {

    @Test
    fun `CrossingData stores all fields correctly`() {
        val crossing = CrossingData(
            documentId = "doc-001", expectedCircle = 4, actualCircle = 3,
            confidenceScore = 0.87, excerpt = "Ce document parle de code open source"
        )
        assertEquals("doc-001", crossing.documentId)
        assertEquals(4, crossing.expectedCircle)
        assertEquals(3, crossing.actualCircle)
        assertEquals(0.87, crossing.confidenceScore, 0.001)
        assertEquals("Ce document parle de code open source", crossing.excerpt)
    }

    @Test
    fun `ThresholdData stores thresholds and crossings`() {
        val crossing = CrossingData("d1", 2, 2, 0.95, "excerpt")
        val threshold = ThresholdData(
            threshold = "10000 tokens", totalSamples = 50,
            errorRate = 0.12, boundaryCrossings = listOf(crossing)
        )
        assertEquals("10000 tokens", threshold.threshold)
        assertEquals(50, threshold.totalSamples)
        assertEquals(0.12, threshold.errorRate, 0.001)
        assertEquals(1, threshold.boundaryCrossings.size)
        assertEquals("d1", threshold.boundaryCrossings[0].documentId)
    }

    @Test
    fun `BenchmarkReport aggregates thresholds and scenario`() {
        val threshold = ThresholdData("5000 tokens", 30, 0.08, emptyList())
        val report = BenchmarkReport(
            scenario = "BASELINE", channels = listOf("RAG_ONLY"),
            summary = "Benchmark baseline sans contexte augmenté",
            results = listOf(threshold)
        )
        assertEquals("BASELINE", report.scenario)
        assertEquals(listOf("RAG_ONLY"), report.channels)
        assertEquals(1, report.results.size)
        assertEquals("5000 tokens", report.results[0].threshold)
    }

    @Test
    fun `CrossingData default values`() {
        val default = CrossingData()
        assertEquals("", default.documentId)
        assertEquals(0, default.expectedCircle)
        assertEquals(0, default.actualCircle)
        assertEquals(0.0, default.confidenceScore, 0.001)
    }

    @Test
    fun `ThresholdData default values`() {
        val default = ThresholdData()
        assertEquals("", default.threshold)
        assertEquals(0, default.totalSamples)
        assertEquals(0.0, default.errorRate, 0.001)
        assertEquals(0, default.boundaryCrossings.size)
    }

    @Test
    fun `BenchmarkReport default values`() {
        val default = BenchmarkReport()
        assertEquals("", default.scenario)
        assertEquals(0, default.channels.size)
        assertEquals("", default.summary)
        assertEquals(0, default.results.size)
    }

    @Test
    fun `Crossing stores basic fields`() {
        val crossing = Crossing("doc-42", 3, 2, 0.76)
        assertEquals("doc-42", crossing.documentId)
        assertEquals(3, crossing.expectedCircle)
        assertEquals(2, crossing.actualCircle)
        assertEquals(0.76, crossing.confidenceScore, 0.001)
    }

    @Test
    fun `ThresholdResult aggregates crossings`() {
        val crossing = Crossing("d1", 4, 4, 0.99)
        val result = ThresholdResult("20000 tokens", 25, 0.04, listOf(crossing))
        assertEquals("20000 tokens", result.threshold)
        assertEquals(25, result.totalSamples)
        assertEquals(0.04, result.errorRate, 0.001)
        assertEquals(1, result.crossings.size)
    }

    @Test
    fun `BenchmarkReportExporter parseObject deserializes from JSON`() {
        val json = """
        {
          "scenario": "FULL-STACK",
          "channels": ["EAGER", "RAG"],
          "summary": "Test complet",
          "results": [
            {
              "threshold": "10000 tokens",
              "totalSamples": 42,
              "errorRate": 0.05,
              "boundaryCrossings": [
                {
                  "documentId": "cross-1",
                  "expectedCircle": 4,
                  "actualCircle": 4,
                  "confidenceScore": 0.93,
                  "excerpt": "extrait du document"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val report = BenchmarkReportExporter.parseObject(json)

        assertEquals("FULL-STACK", report.scenario)
        assertEquals(listOf("EAGER", "RAG"), report.channels)
        assertEquals("Test complet", report.summary)
        assertEquals(1, report.results.size)
        assertEquals("10000 tokens", report.results[0].threshold)
        assertEquals(42, report.results[0].totalSamples)
        assertEquals(0.05, report.results[0].errorRate, 0.001)
        assertEquals(1, report.results[0].boundaryCrossings.size)
        assertEquals("cross-1", report.results[0].boundaryCrossings[0].documentId)
    }

    @Test
    fun `BenchmarkReportExporter exportAsciiDoc generates valid adoc`() {
        val json = """
        {
          "scenario": "BASELINE",
          "channels": [],
          "summary": "",
          "results": []
        }
        """.trimIndent()

        val adoc = BenchmarkReportExporter.exportAsciiDoc(json, "SCENARIO-001")

        assertNotNull(adoc)
        assertEquals(adoc.contains("Benchmark"), true, "Should contain Benchmark heading")
    }
}
