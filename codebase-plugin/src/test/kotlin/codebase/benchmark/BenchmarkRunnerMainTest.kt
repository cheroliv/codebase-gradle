package codebase.benchmark

import codebase.benchmark.BenchmarkRunnerMain.ChannelConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BenchmarkRunnerMainTest {

    @Test
    fun `resolveChannels BASELINE returns empty channels`() {
        val config = BenchmarkRunnerMain.resolveChannels("BASELINE", "/tmp")
        assertEquals(0, config.channels.size)
    }

    @Test
    fun `resolveChannels RAG_ONLY returns RAG channel`() {
        val config = BenchmarkRunnerMain.resolveChannels("RAG_ONLY", "/tmp")
        assertEquals(1, config.channels.size)
        assertEquals("RAG", config.channels[0])
    }

    @Test
    fun `resolveChannels RAG_GRAPHIFY_LOCAL returns RAG and Graphify with graphPath`() {
        val config = BenchmarkRunnerMain.resolveChannels("RAG_GRAPHIFY_LOCAL", "/home/user/project")
        assertEquals(2, config.channels.size)
        assertTrue(config.channels.contains("RAG"))
        assertTrue(config.channels.contains("Graphify"))
        assertEquals("/home/user/project/build/graph-local.json", config.graphPath)
        assertEquals("project", config.scopeFilter)
    }

    @Test
    fun `resolveChannels RAG_GRAPHIFY_WORKSPACE returns RAG and Graphify with workspace scope`() {
        val config = BenchmarkRunnerMain.resolveChannels("RAG_GRAPHIFY_WORKSPACE", "/home/user/project")
        assertEquals(2, config.channels.size)
        assertTrue(config.channels.contains("RAG"))
        assertTrue(config.channels.contains("Graphify"))
        assertEquals(null, config.graphPath)
        assertEquals("workspace", config.scopeFilter)
    }

    @Test
    fun `resolveChannels FOUR_CHANNELS returns all 4 channels`() {
        val config = BenchmarkRunnerMain.resolveChannels("FOUR_CHANNELS", "/tmp")
        assertEquals(4, config.channels.size)
        assertTrue(config.channels.contains("EAGER/LAZY"))
        assertTrue(config.channels.contains("RAG"))
        assertTrue(config.channels.contains("Graphify"))
        assertTrue(config.channels.contains("Ressources"))
    }

    @Test
    fun `resolveChannels unknown scenarioId returns empty channels`() {
        val config = BenchmarkRunnerMain.resolveChannels("UNKNOWN_SCENARIO", "/tmp")
        assertEquals(0, config.channels.size)
    }

    @Test
    fun `resolveChannels empty scenarioId returns empty channels`() {
        val config = BenchmarkRunnerMain.resolveChannels("", "/tmp")
        assertTrue(config.channels.isEmpty())
    }

    @Test
    fun `ChannelConfig data class creation with defaults`() {
        val config = ChannelConfig(channels = listOf("test"))
        assertEquals(1, config.channels.size)
        assertEquals(null, config.graphPath)
        assertEquals(null, config.scopeFilter)
    }

    @Test
    fun `ChannelConfig data class creation with all fields`() {
        val config = ChannelConfig(
            channels = listOf("A", "B"),
            graphPath = "/some/path.json",
            scopeFilter = "project"
        )
        assertEquals(2, config.channels.size)
        assertEquals("/some/path.json", config.graphPath)
        assertEquals("project", config.scopeFilter)
    }
}
