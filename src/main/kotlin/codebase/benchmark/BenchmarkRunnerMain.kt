package codebase.benchmark

import org.slf4j.LoggerFactory
import java.io.File

object BenchmarkRunnerMain {
    private val log = LoggerFactory.getLogger(BenchmarkRunnerMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val scenarioId = if (args.isNotEmpty()) args[0] else "BASELINE"

        val pgJdbcUrl = System.getenv("PGVECTOR_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/codebase_rag"
        val pgUser = System.getenv("PGVECTOR_USER") ?: "codebase"
        val pgPassword = System.getenv("PGVECTOR_PASSWORD") ?: "codebase"
        val graphJsonPath = System.getenv("GRAPH_JSON_PATH") ?: "build/graph.json"
        val projectRoot = System.getenv("CODEBASE_PROJECT_ROOT") ?: System.getProperty("user.dir")

        val channelConfig = resolveChannels(scenarioId, projectRoot)

        val outputDir = File("build/benchmark-reports")
        outputDir.mkdirs()

        val runner = BenchmarkRunner(
            pgJdbcUrl = pgJdbcUrl, pgUser = pgUser, pgPassword = pgPassword,
            graphJsonPath = channelConfig.graphPath ?: graphJsonPath,
            scopeFilter = channelConfig.scopeFilter
        )
        val report = runner.run(scenarioId, channelConfig.channels)

        val reportFile = File(outputDir, "report-${scenarioId}.json")
        reportFile.writeText(report)

        val adoc = BenchmarkReportExporter.exportAsciiDoc(report, scenarioId)
        val adocFile = File(outputDir, "report-${scenarioId}.adoc")
        adocFile.writeText(adoc)

        log.info("Report written: {}", reportFile.absolutePath)
        log.info("AsciiDoc written: {}", adocFile.absolutePath)
    }

    data class ChannelConfig(
        val channels: List<String>,
        val graphPath: String? = null,
        val scopeFilter: String? = null
    )

    internal fun resolveChannels(scenarioId: String, projectRoot: String): ChannelConfig = when (scenarioId) {
        "BASELINE" -> ChannelConfig(emptyList())
        "RAG_ONLY" -> ChannelConfig(listOf("RAG"))
        "RAG_GRAPHIFY_LOCAL" -> ChannelConfig(
            channels = listOf("RAG", "Graphify"),
            graphPath = "$projectRoot/build/graph-local.json",
            scopeFilter = "project"
        )
        "RAG_GRAPHIFY_WORKSPACE" -> ChannelConfig(
            channels = listOf("RAG", "Graphify"),
            graphPath = null,
            scopeFilter = "workspace"
        )
        "FOUR_CHANNELS" -> ChannelConfig(
            channels = listOf("EAGER/LAZY", "RAG", "Graphify", "Ressources")
        )
        else -> ChannelConfig(emptyList())
    }
}
