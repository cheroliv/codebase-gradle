package codebase.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GraphContextBuilderTest {

    private fun buildThreeNodeTwoEdgeModel(): GraphModel {
        val nodeA = GraphNode(id = "A", label = "NodeA", type = "file", community = "core")
        val nodeB = GraphNode(id = "B", label = "NodeB", type = "class", community = "core")
        val nodeC = GraphNode(id = "C", label = "NodeC", type = "method", community = "utils")
        val edgeAB = GraphEdge(source = "A", target = "B", type = "imports", label = "uses")
        val edgeAC = GraphEdge(source = "A", target = "C", type = "calls", label = "invokes")
        return GraphModel(nodes = listOf(nodeA, nodeB, nodeC), edges = listOf(edgeAB, edgeAC))
    }

    @Test
    fun `neighborhood returns target node and its neighbors`() {
        val model = buildThreeNodeTwoEdgeModel()
        val result = GraphContextBuilder.neighborhood(model, "A", maxDepth = 1)

        assertTrue(result.contains("Target node: NodeA (file) [A]"))
        assertTrue(result.contains("NodeB (class) [B]"))
        assertTrue(result.contains("NodeC (method) [C]"))
        assertTrue(result.contains("imports"))
        assertTrue(result.contains("calls"))
        assertTrue(result.contains("Related nodes (2):"))
        assertTrue(result.contains("Direct relationships (2 edges):"))
    }

    @Test
    fun `neighborhood returns edge direction information`() {
        val model = buildThreeNodeTwoEdgeModel()
        val result = GraphContextBuilder.neighborhood(model, "A", maxDepth = 1)

        assertTrue(result.contains("→"))
    }

    @Test
    fun `neighborhood maxDepth 2 returns same direct neighbors`() {
        val nodeA = GraphNode(id = "A", label = "NodeA", type = "file")
        val nodeB = GraphNode(id = "B", label = "NodeB", type = "class")
        val nodeC = GraphNode(id = "C", label = "NodeC", type = "method")
        val edgeAB = GraphEdge(source = "A", target = "B", type = "imports", label = null)
        val edgeBC = GraphEdge(source = "B", target = "C", type = "calls", label = null)

        val model = GraphModel(nodes = listOf(nodeA, nodeB, nodeC), edges = listOf(edgeAB, edgeBC))
        val result = GraphContextBuilder.neighborhood(model, "A", maxDepth = 2)

        assertTrue(result.contains("NodeB"))
        assertTrue(result.contains("Related nodes (1):"))
    }

    @Test
    fun `neighborhood with unknown nodeId returns empty string`() {
        val model = buildThreeNodeTwoEdgeModel()
        val result = GraphContextBuilder.neighborhood(model, "NONEXISTENT", maxDepth = 1)

        assertEquals("", result)
    }

    @Test
    fun `neighborhood with no edges returns only target node info`() {
        val isolated = GraphNode(id = "X", label = "IsolatedNode", type = "file", community = "solo")
        val model = GraphModel(nodes = listOf(isolated), edges = emptyList())

        val result = GraphContextBuilder.neighborhood(model, "X", maxDepth = 1)

        assertTrue(result.contains("Target node: IsolatedNode (file) [X]"))
        assertTrue(result.contains("Community: solo"))
        assertTrue(result.contains("Related nodes (0):"))
        assertTrue(result.contains("Direct relationships (0 edges):"))
    }

    @Test
    fun `neighborhood for node with incoming edges includes source nodes`() {
        val nodeA = GraphNode(id = "src", label = "Source", type = "file")
        val nodeB = GraphNode(id = "tgt", label = "Target", type = "class")
        val edgeToTarget = GraphEdge(source = "src", target = "tgt", type = "depends", label = null)

        val model = GraphModel(nodes = listOf(nodeA, nodeB), edges = listOf(edgeToTarget))
        val result = GraphContextBuilder.neighborhood(model, "tgt", maxDepth = 1)

        assertTrue(result.contains("Target node: Target (class) [tgt]"))
        assertTrue(result.contains("Source"))
        assertTrue(result.contains("←"))
    }

    @Test
    fun `Jackson round-trip serialize deserialize GraphModel`() {
        val mapper = ObjectMapper().registerKotlinModule()
        val model = buildThreeNodeTwoEdgeModel()

        val json = mapper.writeValueAsString(model)
        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("\"id\""))

        val deserialized = mapper.readValue<GraphModel>(json)
        assertNotNull(deserialized)
        assertEquals(3, deserialized.nodes.size)
        assertEquals(2, deserialized.edges.size)

        val nodeA = deserialized.nodes.find { it.id == "A" }
        assertNotNull(nodeA)
        assertEquals("NodeA", nodeA.label)
        assertEquals("file", nodeA.type)
        assertEquals("core", nodeA.community)

        val edgeAB = deserialized.edges.find { it.source == "A" && it.target == "B" }
        assertNotNull(edgeAB)
        assertEquals("imports", edgeAB.type)
        assertEquals("uses", edgeAB.label)
    }

    @Test
    fun `Jackson round-trip with metadata Map`() {
        val mapper = ObjectMapper().registerKotlinModule()
        val node = GraphNode(
            id = "M1",
            label = "NodeWithMeta",
            type = "document",
            community = "docs",
            metadata = mapOf("key1" to "value1", "key2" to 42)
        )
        val model = GraphModel(nodes = listOf(node), edges = emptyList())

        val json = mapper.writeValueAsString(model)
        val deserialized = mapper.readValue<GraphModel>(json)

        assertEquals(1, deserialized.nodes.size)
        assertNotNull(deserialized.nodes[0].metadata["key1"])
    }

    @Test
    fun `load from existing file returns GraphModel`(@TempDir tempDir: File) {
        val graphFile = File(tempDir, "graph.json")
        graphFile.writeText("""
            {
                "nodes": [
                    {"id": "N1", "label": "Node1", "type": "file", "community": "main"}
                ],
                "edges": [
                    {"source": "N1", "target": "N2", "type": "link"}
                ]
            }
        """.trimIndent())

        val model = GraphContextBuilder.load(graphFile)
        assertEquals(1, model.nodes.size)
        assertEquals("Node1", model.nodes[0].label)
        assertEquals(1, model.edges.size)
        assertEquals("link", model.edges[0].type)
    }

    @Test
    fun `load from non-existent file returns empty GraphModel`() {
        val model = GraphContextBuilder.load(File("/nonexistent/graph.json"))

        assertEquals(0, model.nodes.size)
        assertEquals(0, model.edges.size)
    }

    @Test
    fun `neighborhood with label on edges renders label in output`() {
        val nodeA = GraphNode(id = "A", label = "A", type = "file")
        val nodeB = GraphNode(id = "B", label = "B", type = "class")
        val edge = GraphEdge(source = "A", target = "B", type = "uses", label = "dependency")

        val model = GraphModel(nodes = listOf(nodeA, nodeB), edges = listOf(edge))
        val result = GraphContextBuilder.neighborhood(model, "A", maxDepth = 1)

        assertTrue(result.contains("(dependency)"))
    }

    @Test
    fun `neighborhood with unknown community shows unknown`() {
        val node = GraphNode(id = "U", label = "Unknown", type = "file", community = null)
        val model = GraphModel(nodes = listOf(node), edges = emptyList())

        val result = GraphContextBuilder.neighborhood(model, "U", maxDepth = 1)

        assertTrue(result.contains("Community: unknown"))
    }
}
