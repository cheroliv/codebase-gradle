package codebase.benchmark

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BenchmarkComparisonMainTest {

    @Test
    fun `parseThresholdResults with valid JSON containing 3 thresholds returns 3 ThresholdRecord`() {
        val json = """
        {
            "scenario": "BASELINE",
            "channels": [],
            "summary": "test summary",
            "results": [
                {"threshold": "10K", "totalSamples": 7, "errorRate": 0.1428, "boundaryCrossings": [{"documentId":"C0-strategie","expectedCircle":0,"actualCircle":1,"confidenceScore":0.8,"excerpt":"test"}]},
                {"threshold": "30K", "totalSamples": 7, "errorRate": 0.2857, "boundaryCrossings": [{"documentId":"C1-tokens","expectedCircle":1,"actualCircle":2,"confidenceScore":0.7,"excerpt":"test"},{"documentId":"C2-pedagogie","expectedCircle":2,"actualCircle":3,"confidenceScore":0.6,"excerpt":"test"}]},
                {"threshold": "60K", "totalSamples": 7, "errorRate": 0.0, "boundaryCrossings": []}
            ]
        }
        """.trimIndent()

        val records = BenchmarkComparisonMain.parseThresholdResults(json)

        assertEquals(3, records.size)
        assertEquals("10K", records[0].threshold)
        assertEquals(7, records[0].totalSamples)
        assertEquals(0.1428, records[0].errorRate)
        assertEquals(1, records[0].errorCount)

        assertEquals("30K", records[1].threshold)
        assertEquals(2, records[1].errorCount)

        assertEquals("60K", records[2].threshold)
        assertEquals(0, records[2].errorCount)
    }

    @Test
    fun `parseThresholdResults with JSON containing no results returns empty list`() {
        val json = """
        {
            "scenario": "EMPTY",
            "channels": ["RAG"],
            "summary": "no results",
            "results": []
        }
        """.trimIndent()

        val records = BenchmarkComparisonMain.parseThresholdResults(json)
        assertEquals(0, records.size)
    }

    @Test
    fun `parseThresholdResults with JSON containing 5 thresholds returns 5 ThresholdRecord`() {
        val json = """
        {
            "scenario": "FOUR_CHANNELS",
            "channels": ["EAGER/LAZY","RAG","Graphify","Ressources"],
            "summary": "4 channels test",
            "results": [
                {"threshold": "10K", "totalSamples": 7, "errorRate": 0.0, "boundaryCrossings": []},
                {"threshold": "30K", "totalSamples": 7, "errorRate": 0.0, "boundaryCrossings": []},
                {"threshold": "60K", "totalSamples": 7, "errorRate": 0.1428, "boundaryCrossings": [{"documentId":"C3-closed","expectedCircle":3,"actualCircle":4,"confidenceScore":0.55,"excerpt":"test"}]},
                {"threshold": "100K", "totalSamples": 7, "errorRate": 0.2857, "boundaryCrossings": [{"documentId":"C0-strategie","expectedCircle":0,"actualCircle":1,"confidenceScore":0.4,"excerpt":"t"},{"documentId":"C4-plugin","expectedCircle":4,"actualCircle":2,"confidenceScore":0.35,"excerpt":"t"}]},
                {"threshold": "128K", "totalSamples": 7, "errorRate": 0.4285, "boundaryCrossings": [{"documentId":"C0","expectedCircle":0,"actualCircle":2,"confidenceScore":0.3,"excerpt":"t"},{"documentId":"C1","expectedCircle":1,"actualCircle":3,"confidenceScore":0.25,"excerpt":"t"},{"documentId":"C2","expectedCircle":2,"actualCircle":4,"confidenceScore":0.2,"excerpt":"t"}]}
            ]
        }
        """.trimIndent()

        val records = BenchmarkComparisonMain.parseThresholdResults(json)

        assertEquals(5, records.size)
        assertEquals(0.0, records[0].errorRate)
        assertEquals(0.4285, records[4].errorRate)
        assertEquals(3, records[4].errorCount)
    }

    @Test
    fun `parseThresholdResults edge case with single crossing`() {
        val json = """
        {
            "scenario": "SINGLE",
            "channels": ["RAG"],
            "summary": "single",
            "results": [
                {"threshold": "128K", "totalSamples": 7, "errorRate": 0.142857142857, "boundaryCrossings": [{"documentId":"C4-plugin","expectedCircle":4,"actualCircle":3,"confidenceScore":0.44,"excerpt":"foundry/public/plantuml-gradle/"}]}
            ]
        }
        """.trimIndent()

        val records = BenchmarkComparisonMain.parseThresholdResults(json)

        assertEquals(1, records.size)
        assertEquals(1, records[0].errorCount)
        assertEquals("128K", records[0].threshold)
    }

    @Test
    fun `generateComparisonAsciiDoc with 1 ScenarioRecord contains scenarioId`() {
        val record = BenchmarkComparisonMain.ScenarioRecord(
            id = "BASELINE",
            channels = emptyList(),
            results = listOf(
                BenchmarkComparisonMain.ThresholdRecord("10K", 0.0, 7, 0)
            )
        )

        val adoc = BenchmarkComparisonMain.generateComparisonAsciiDoc(listOf(record), "test-model")

        assertTrue(adoc.contains("BASELINE"), "AsciiDoc should contain scenario ID 'BASELINE'")
        assertTrue(adoc.contains("EPIC 4"))
        assertTrue(adoc.contains("test-model"))
    }

    @Test
    fun `generateComparisonAsciiDoc with multiple ScenarioRecords contains all IDs`() {
        val records = listOf(
            BenchmarkComparisonMain.ScenarioRecord(
                id = "BASELINE",
                channels = emptyList(),
                results = listOf(
                    BenchmarkComparisonMain.ThresholdRecord("10K", 0.0, 7, 0),
                    BenchmarkComparisonMain.ThresholdRecord("30K", 0.1428, 7, 1)
                )
            ),
            BenchmarkComparisonMain.ScenarioRecord(
                id = "RAG_ONLY",
                channels = listOf("RAG"),
                results = listOf(
                    BenchmarkComparisonMain.ThresholdRecord("10K", 0.0, 7, 0)
                )
            )
        )

        val adoc = BenchmarkComparisonMain.generateComparisonAsciiDoc(records, "pro-model")

        assertTrue(adoc.contains("BASELINE"))
        assertTrue(adoc.contains("RAG_ONLY"))
        assertTrue(adoc.contains("pro-model"))
    }

    @Test
    fun `generateComparisonAsciiDoc contains expected sections`() {
        val record = BenchmarkComparisonMain.ScenarioRecord(
            id = "FOUR_CHANNELS",
            channels = listOf("EAGER/LAZY", "RAG", "Graphify", "Ressources"),
            results = listOf(
                BenchmarkComparisonMain.ThresholdRecord("128K", 0.04, 7, 0)
            )
        )

        val adoc = BenchmarkComparisonMain.generateComparisonAsciiDoc(listOf(record), "deepseek-v4")
        assertTrue(adoc.contains("Matrice de Comparaison"))
        assertTrue(adoc.contains("Scénarios Exécutés"))
        assertTrue(adoc.contains("Analyse par Scénario"))
        assertTrue(adoc.contains("Évolution du Taux d'Erreur par Seuil"))
        assertTrue(adoc.contains("Métrique Clé"))
    }

    @Test
    fun `evaluateGate with low errorRate contains PASS`() {
        val record = BenchmarkComparisonMain.ScenarioRecord(
            id = "RAG_GRAPHIFY_WORKSPACE",
            channels = listOf("RAG", "Graphify"),
            results = listOf(
                BenchmarkComparisonMain.ThresholdRecord("128K", 0.02, 7, 0)
            )
        )

        val result = BenchmarkComparisonMain.evaluateGate(listOf(record))
        assertTrue(result.contains("PASS"), "Expected gate result to contain 'PASS'")
    }

    @Test
    fun `evaluateGate with high errorRate contains FAIL`() {
        val record = BenchmarkComparisonMain.ScenarioRecord(
            id = "RAG_GRAPHIFY_WORKSPACE",
            channels = listOf("RAG", "Graphify"),
            results = listOf(
                BenchmarkComparisonMain.ThresholdRecord("128K", 0.55, 7, 4)
            )
        )

        val result = BenchmarkComparisonMain.evaluateGate(listOf(record))
        assertTrue(result.contains("FAIL"), "Expected gate result to contain 'FAIL'")
    }

    @Test
    fun `evaluateGate with exact gate boundary 5 percent contains FAIL`() {
        val record = BenchmarkComparisonMain.ScenarioRecord(
            id = "RAG_GRAPHIFY_WORKSPACE",
            channels = listOf("RAG", "Graphify"),
            results = listOf(
                BenchmarkComparisonMain.ThresholdRecord("128K", 0.05, 7, 1)
            )
        )

        val result = BenchmarkComparisonMain.evaluateGate(listOf(record))
        assertTrue(result.contains("FAIL"), "5.0% exact should FAIL (gate is < 5.0%, not <=)")
    }

    @Test
    fun `evaluateGate with zero errorRate contains PASS`() {
        val record = BenchmarkComparisonMain.ScenarioRecord(
            id = "RAG_GRAPHIFY_WORKSPACE",
            channels = listOf("RAG", "Graphify"),
            results = listOf(
                BenchmarkComparisonMain.ThresholdRecord("128K", 0.0, 7, 0)
            )
        )

        val result = BenchmarkComparisonMain.evaluateGate(listOf(record))
        assertTrue(result.contains("PASS"))
    }

    @Test
    fun `evaluateGate without RAG_GRAPHIFY_WORKSPACE scenario shows warning`() {
        val record = BenchmarkComparisonMain.ScenarioRecord(
            id = "BASELINE",
            channels = emptyList(),
            results = listOf(
                BenchmarkComparisonMain.ThresholdRecord("128K", 0.1, 7, 1)
            )
        )

        val result = BenchmarkComparisonMain.evaluateGate(listOf(record))
        assertTrue(result.contains("n'a pas été exécuté"), "Expected warning about missing RAG_GRAPHIFY_WORKSPACE")
    }

    @Test
    fun `ScenarioRecord data class creation and field access`() {
        val record = BenchmarkComparisonMain.ScenarioRecord(
            id = "TEST",
            channels = listOf("RAG"),
            results = listOf(
                BenchmarkComparisonMain.ThresholdRecord("10K", 0.1, 7, 1)
            )
        )
        assertEquals("TEST", record.id)
        assertEquals(1, record.channels.size)
        assertEquals("RAG", record.channels[0])
        assertEquals(1, record.results.size)
    }

    @Test
    fun `ThresholdRecord data class creation and field access`() {
        val tr = BenchmarkComparisonMain.ThresholdRecord("128K", 0.4285, 7, 3)
        assertEquals("128K", tr.threshold)
        assertEquals(0.4285, tr.errorRate)
        assertEquals(7, tr.totalSamples)
        assertEquals(3, tr.errorCount)
    }
}
