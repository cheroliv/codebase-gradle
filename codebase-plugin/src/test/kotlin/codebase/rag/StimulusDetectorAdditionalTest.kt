package codebase.rag

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests additionnels pour StimulusDetector — couvre reportAsciiDoc et exportStimuliJson.
 */
class StimulusDetectorAdditionalTest {

    private val detector = StimulusDetector("/tmp/fake-workspace")

    @Test
    fun `reportAsciiDoc produces structured report with stale and fresh stimuli`() {
        val stimuli = listOf(
            ActiveStimulus(
                file = StimulusFile("brain-dump-01.adoc", "/ws/brain-dump-01.adoc",
                    LocalDateTime.of(2026, 5, 28, 10, 0), 2048, 0),
                ageDays = 0, stale = false
            ),
            ActiveStimulus(
                file = StimulusFile("old-idea.adoc", "/ws/old-idea.adoc",
                    LocalDateTime.of(2026, 5, 20, 8, 0), 4096, 8),
                ageDays = 8, stale = true
            ),
        )

        val report = detector.reportAsciiDoc(stimuli)

        assertTrue(report.contains("Détection de Stimuli Actifs"))
        assertTrue(report.contains("EPIC 10"))
        assertTrue(report.contains("brain-dump-01.adoc"))
        assertTrue(report.contains("old-idea.adoc"))
        assertTrue(report.contains("⚠️ STALE"))
        assertTrue(report.contains("✅ Récent"))
        assertTrue(report.contains("2"), "Should show total count")
        assertTrue(report.contains("Stimuli en retard de dilution"))
    }

    @Test
    fun `reportAsciiDoc with no stale stimuli shows no stale warning`() {
        val stimuli = listOf(
            ActiveStimulus(
                file = StimulusFile("fresh.adoc", "/ws/fresh.adoc",
                    LocalDateTime.of(2026, 5, 28, 12, 0), 1024, 0),
                ageDays = 0, stale = false
            ),
        )

        val report = detector.reportAsciiDoc(stimuli)

        assertTrue(report.contains("Total stimuli actifs | 1"))
        assertTrue(report.contains("Stale (> 2 jours) | 0"))
        assertTrue(!report.contains("Stimuli en retard de dilution"))
    }

    @Test
    fun `reportAsciiDoc with empty stimuli list`() {
        val report = detector.reportAsciiDoc(emptyList())

        assertTrue(report.contains("Détection de Stimuli Actifs"))
        assertTrue(report.contains("Total stimuli actifs | 0"))
    }

    @Test
    fun `exportStimuliJson produces valid JSON with stimuli`() {
        val stimuli = listOf(
            ActiveStimulus(
                file = StimulusFile("brain.adoc", "/ws/brain.adoc",
                    LocalDateTime.of(2026, 5, 28, 14, 0), 1536, 1),
                ageDays = 1, stale = false
            ),
        )

        val json = detector.exportStimuliJson(stimuli)

        assertTrue(json.contains("\"totalActive\": 1"))
        assertTrue(json.contains("\"staleCount\": 0"))
        assertTrue(json.contains("\"name\": \"brain.adoc\""))
        assertTrue(json.contains("\"stale\": false"))
        assertTrue(json.startsWith("{"))
        assertTrue(json.trimEnd().endsWith("}"))
    }

    @Test
    fun `exportStimuliJson with stale stimulus includes stale flag`() {
        val stimuli = listOf(
            ActiveStimulus(
                file = StimulusFile("stale-dump.adoc", "/ws/stale-dump.adoc",
                    LocalDateTime.of(2026, 5, 15, 9, 0), 8192, 13),
                ageDays = 13, stale = true
            ),
        )

        val json = detector.exportStimuliJson(stimuli)

        assertTrue(json.contains("\"staleCount\": 1"))
        assertTrue(json.contains("\"stale\": true"))
        assertTrue(json.contains("\"ageDays\": 13"))
        assertTrue(json.contains("\"sizeBytes\": 8192"))
    }

    @Test
    fun `exportStimuliJson with empty list`() {
        val json = detector.exportStimuliJson(emptyList())

        assertTrue(json.contains("\"totalActive\": 0"))
        assertTrue(json.contains("\"staleCount\": 0"))
        assertTrue(json.contains("\"stimuli\": ["))
        assertTrue(json.startsWith("{"))
        assertTrue(json.trimEnd().endsWith("}"))
    }

    @Test
    fun `exportStimuliJson with multiple stimuli uses proper commas`() {
        val stimuli = listOf(
            ActiveStimulus(StimulusFile("a.adoc","/a", LocalDateTime.now(),100,0), 0, false),
            ActiveStimulus(StimulusFile("b.adoc","/b", LocalDateTime.now(),200,0), 0, false),
        )

        val json = detector.exportStimuliJson(stimuli)

        assertTrue(json.contains("\"totalActive\": 2"))
        assertTrue(json.contains("\"name\": \"a.adoc\""))
        assertTrue(json.contains("\"name\": \"b.adoc\""))
        // Should start and end with braces
        assertTrue(json.startsWith("{"))
        assertTrue(json.trimEnd().endsWith("}"))
    }

    @Test
    fun `scan returns empty list for invalid workspace`() {
        val badDetector = StimulusDetector("/nonexistent/path/that/does/not/exist")
        val files = badDetector.scan()
        assertEquals(0, files.size)
    }
}
