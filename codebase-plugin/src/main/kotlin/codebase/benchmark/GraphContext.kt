package codebase.benchmark

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class GraphNode(
    val id: String,
    val label: String,
    val type: String,
    val community: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GraphEdge(
    val source: String,
    val target: String,
    val type: String,
    val label: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GraphModel(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList()
)

object GraphContextBuilder {
    private val mapper = ObjectMapper().registerKotlinModule()

    fun load(graphFile: File): GraphModel {
        if (!graphFile.exists()) return GraphModel()
        return mapper.readValue<GraphModel>(graphFile)
    }

    fun neighborhood(model: GraphModel, nodeId: String, maxDepth: Int = 2): String {
        val targetNode = model.nodes.find { it.id == nodeId } ?: return ""
        val relatedEdges = model.edges.filter { it.source == nodeId || it.target == nodeId }
        val relatedNodeIds = relatedEdges.flatMap { listOf(it.source, it.target) }.toSet() - nodeId
        val relatedNodes = model.nodes.filter { it.id in relatedNodeIds }

        val sb = StringBuilder()
        sb.appendLine("=== Graphify Knowledge Graph Context ===")
        sb.appendLine("Target node: ${targetNode.label} (${targetNode.type}) [${targetNode.id}]")
        sb.appendLine("Community: ${targetNode.community ?: "unknown"}")
        sb.appendLine()
        sb.appendLine("Direct relationships (${relatedEdges.size} edges):")
        for (edge in relatedEdges) {
            val direction = if (edge.source == nodeId) "→" else "←"
            val otherId = if (edge.source == nodeId) edge.target else edge.source
            val otherNode = model.nodes.find { it.id == otherId }
            sb.appendLine("  $direction ${edge.type}: ${otherNode?.label ?: otherId} ${edge.label?.let { "($it)" } ?: ""}")
        }
        sb.appendLine()
        sb.appendLine("Related nodes (${relatedNodes.size}):")
        for (node in relatedNodes) {
            sb.appendLine("  - ${node.label} (${node.type}) [${node.id}]")
        }
        return sb.toString()
    }
}
