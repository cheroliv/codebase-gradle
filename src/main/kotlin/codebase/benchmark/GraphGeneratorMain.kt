package codebase.benchmark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

object GraphGeneratorMain {
    private val log = LoggerFactory.getLogger(GraphGeneratorMain::class.java)
    private val mapper = ObjectMapper().registerKotlinModule()

    @JvmStatic
    fun main(args: Array<String>) {
        val rootDir = File(System.getenv("CODEBASE_ROOT_DIR") ?: ".")
        val outputFile = File(args.getOrNull(0) ?: "build/graph.json")

        val excludeNames = setOf(".git", "build", ".gradle", "node_modules", ".kotlin", "target", ".idea", "__pycache__")

        val allPaths = mutableListOf<Path>()
        Files.walkFileTree(rootDir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir.fileName.toString() in excludeNames) return FileVisitResult.SKIP_SUBTREE
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                allPaths.add(file)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                return FileVisitResult.SKIP_SUBTREE
            }
        })

        val root = rootDir.toPath()

        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        val dirNodes = mutableSetOf<String>()

        for (filePath in allPaths) {
            val relative = root.relativize(filePath).toString()
            val parentRelative = root.relativize(filePath.parent).toString()

            val community = findRepoName(filePath, root)

            nodes.add(
                GraphNode(
                    id = relative,
                    label = filePath.fileName.toString(),
                    type = "file",
                    community = community,
                    metadata = mapOf(
                        "extension" to (filePath.extension ?: ""),
                        "size" to Files.size(filePath)
                    )
                )
            )

            if (parentRelative !in dirNodes && parentRelative.isNotEmpty()) {
                dirNodes.add(parentRelative)
                nodes.add(
                    GraphNode(
                        id = parentRelative,
                        label = filePath.parent.fileName.toString(),
                        type = "directory",
                        community = community
                    )
                )
            }

            if (parentRelative.isNotEmpty()) {
                edges.add(GraphEdge(source = parentRelative, target = relative, type = "contains"))
            }

            if (filePath.extension == "kt" || filePath.extension == "kts") {
                try {
                    val content = Files.readString(filePath)
                    val importRegex = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE)
                    val imports = importRegex.findAll(content).map { it.groupValues[1] }.toList()
                    for (imp in imports) {
                        edges.add(
                            GraphEdge(
                                source = relative,
                                target = "package://$imp",
                                type = "import",
                                label = imp
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        val graph = GraphModel(nodes = nodes, edges = edges)

        outputFile.parentFile.mkdirs()
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, graph)
        log.info("Graph generated: {} nodes, {} edges -> {}", nodes.size, edges.size, outputFile.absolutePath)
    }

    private fun findRepoName(path: Path, root: Path): String? {
        var parent: Path? = path.parent
        while (parent != null && parent != root && parent != root.parent) {
            if (Files.isDirectory(parent.resolve(".git")) || Files.isRegularFile(parent.resolve(".git"))) {
                return parent.fileName.toString()
            }
            parent = parent.parent
        }
        return null
    }
}
