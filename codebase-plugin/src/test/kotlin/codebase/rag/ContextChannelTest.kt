package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContextChannelTest {

    @Test
    fun `sealed class has all four channel types`() {
        val all = ContextChannel.all()
        assertEquals(4, all.size)
        assertEquals(ChannelType.EAGER, all[0].type)
        assertEquals(ChannelType.RAG, all[1].type)
        assertEquals(ChannelType.GRAPHIFY, all[2].type)
        assertEquals(ChannelType.RESOURCE, all[3].type)
    }

    @Test
    fun `each channel type has correct budget proportion`() {
        val eager = ContextChannel.Eager()
        assertEquals(0.40, eager.budgetProportion)
        assertEquals("EAGER/LAZY", eager.name)

        val rag = ContextChannel.Rag()
        assertEquals(0.30, rag.budgetProportion)
        assertEquals("RAG/pgvector", rag.name)

        val graphify = ContextChannel.Graphify()
        assertEquals(0.20, graphify.budgetProportion)
        assertEquals("Graphify", graphify.name)

        val resource = ContextChannel.Resource()
        assertEquals(0.10, resource.budgetProportion)
        assertEquals("Ressources", resource.name)
    }

    @Test
    fun `each channel type has section header`() {
        val eager = ContextChannel.Eager("content")
        assertTrue(eager.sectionHeader.contains("RÈGLES_EAGER"))

        val rag = ContextChannel.Rag("content")
        assertTrue(rag.sectionHeader.contains("CONTEXTE_RAG"))

        val graphify = ContextChannel.Graphify("content")
        assertTrue(graphify.sectionHeader.contains("RELATIONS_GRAPHIFY"))

        val resource = ContextChannel.Resource("content")
        assertTrue(resource.sectionHeader.contains("RESSOURCES_COLD"))
    }

    @Test
    fun `isNotEmpty returns true when content has text`() {
        val channel = ContextChannel.Rag("some content")
        assertTrue(channel.isNotEmpty())
    }

    @Test
    fun `isNotEmpty returns false when content is blank`() {
        val channel = ContextChannel.Eager("")
        assertTrue(!channel.isNotEmpty())
    }

    @Test
    fun `withContent creates new instance with updated content`() {
        val channel = ContextChannel.Rag("old")
        val updated = channel.withContent("new")
        assertEquals("new", updated.content)
        assertEquals(ChannelType.RAG, updated.type)
    }

    @Test
    fun `truncateToTokens respects max budget`() {
        val longContent = (1..500).joinToString("\n") { "line $it with some text to consume tokens" }
        val channel = ContextChannel.Eager(longContent)
        val truncated = channel.truncateToTokens(maxTokens = 100)
        assertTrue(truncated.content.length < longContent.length)
        assertTrue(truncated.content.isNotBlank())
    }

    @Test
    fun `truncateToTokens with zero budget returns empty`() {
        val channel = ContextChannel.Rag("some content")
        val truncated = channel.truncateToTokens(maxTokens = 0)
        assertEquals("", truncated.content)
    }

    @Test
    fun `each channel has a description`() {
        val eager = ContextChannel.Eager()
        assertTrue(eager.description.contains("Gouvernance"))
        assertTrue(eager.description.contains("déterministe"))

        val rag = ContextChannel.Rag()
        assertTrue(rag.description.contains("Similarité"))
        assertTrue(rag.description.contains("vectorielle"))

        val graphify = ContextChannel.Graphify()
        assertTrue(graphify.description.contains("Relations"))
        assertTrue(graphify.description.contains("structurelles"))

        val resource = ContextChannel.Resource()
        assertTrue(resource.description.contains("Mémoire"))
        assertTrue(resource.description.contains("froide"))
    }
}
