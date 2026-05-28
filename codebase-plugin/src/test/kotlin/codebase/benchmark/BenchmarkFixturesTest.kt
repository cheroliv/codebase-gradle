package codebase.benchmark

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BenchmarkFixturesTest {

    @Test
    fun `samples has 7 entries`() {
        assertEquals(7, BenchmarkFixtures.samples.size)
    }

    @Test
    fun `each sample has non-empty documentId`() {
        for (sample in BenchmarkFixtures.samples) {
            assertTrue(sample.id.isNotEmpty(), "Expected non-empty id for sample with content: ${sample.content.take(50)}")
        }
    }

    @Test
    fun `each sample has expectedCircle between 0 and 4`() {
        for (sample in BenchmarkFixtures.samples) {
            assertTrue(sample.expectedCircle in 0..4, "Expected circle 0-4 but got ${sample.expectedCircle} for ${sample.id}")
        }
    }

    @Test
    fun `samples contains expected document ids`() {
        val ids = BenchmarkFixtures.samples.map { it.id }.toSet()
        assertTrue(ids.contains("C0-strategie"))
        assertTrue(ids.contains("C1-tokens"))
        assertTrue(ids.contains("C2-pedagogie"))
        assertTrue(ids.contains("C2-livre"))
        assertTrue(ids.contains("C3-closed"))
        assertTrue(ids.contains("C4-plugin"))
        assertTrue(ids.contains("C4-readme"))
    }

    @Test
    fun `fillerForThreshold 100 produces string of length about 100`() {
        val result = ContextFiller.fillerForThreshold(100)
        assertTrue(result.isNotEmpty())
        val charCount = result.length
        assertTrue(charCount >= 50, "Expected >= 50 chars but got $charCount")
        assertTrue(charCount <= 2000, "Expected <= 2000 chars but got $charCount")
    }

    @Test
    fun `eagerLazyContext contains EAGER and LAZY`() {
        val result = ContextFiller.eagerLazyContext(500)
        assertTrue(result.contains("EAGER"), "Expected result to contain 'EAGER'")
        assertTrue(result.contains("LAZY"), "Expected result to contain 'LAZY'")
    }

    @Test
    fun `technicalDocumentationParagraph is non-empty`() {
        val result = ContextFiller.technicalDocumentationParagraph(100)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `ressourcesContext contains corpus metier content`() {
        val result = ContextFiller.ressourcesContext(500)
        assertTrue(result.contains("CORPUS METIER", ignoreCase = true), "Expected result to contain 'CORPUS METIER'")
        assertTrue(result.contains("DATASETS TECHNIQUES"), "Expected result to contain 'DATASETS TECHNIQUES'")
        assertTrue(result.contains("CORPUS FORMATION", ignoreCase = true), "Expected result to contain 'CORPUS FORMATION'")
    }

    @Test
    fun `fillerForThreshold scales with threshold parameter`() {
        val small = ContextFiller.fillerForThreshold(50)
        val large = ContextFiller.fillerForThreshold(500)
        assertTrue(large.length > small.length, "Expected larger threshold to produce longer result")
    }

    @Test
    fun `eagerLazyContext scales with threshold parameter`() {
        val small = ContextFiller.eagerLazyContext(100)
        val large = ContextFiller.eagerLazyContext(2000)
        assertTrue(large.length > small.length, "Expected larger threshold to produce longer result")
    }

    @Test
    fun `ressourcesContext scales with threshold parameter`() {
        val small = ContextFiller.ressourcesContext(100)
        val large = ContextFiller.ressourcesContext(2000)
        assertTrue(large.length > small.length, "Expected larger threshold to produce longer result")
    }

    @Test
    fun `SampleDocument data class creation and field access`() {
        val doc = SampleDocument(id = "C0-test", expectedCircle = 0, content = "test content")
        assertEquals("C0-test", doc.id)
        assertEquals(0, doc.expectedCircle)
        assertEquals("test content", doc.content)
    }

    @Test
    fun `ClassificationRequest data class creation and defaults`() {
        val req = ClassificationRequest()
        assertEquals("", req.documentExcerpt)
        assertEquals("", req.context)
    }

    @Test
    fun `ClassificationRequest data class with values`() {
        val req = ClassificationRequest(documentExcerpt = "excerpt", context = "ctx")
        assertEquals("excerpt", req.documentExcerpt)
        assertEquals("ctx", req.context)
    }

    @Test
    fun `CircleClassification data class creation and defaults`() {
        val c = CircleClassification()
        assertEquals(-1, c.predictedCircle)
        assertEquals(0.0, c.confidenceScore)
        assertEquals("", c.reasoning)
    }

    @Test
    fun `CircleClassification data class with values`() {
        val c = CircleClassification(predictedCircle = 3, confidenceScore = 0.95, reasoning = "because")
        assertEquals(3, c.predictedCircle)
        assertEquals(0.95, c.confidenceScore)
        assertEquals("because", c.reasoning)
    }
}
