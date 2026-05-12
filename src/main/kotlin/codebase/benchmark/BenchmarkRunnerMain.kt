package codebase.benchmark

import codebase.rag.PgVectorConfig
import org.slf4j.LoggerFactory
import java.io.File

object BenchmarkRunnerMain {
    private val log = LoggerFactory.getLogger(BenchmarkRunnerMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val scenarioId = if (args.isNotEmpty()) args[0] else "BASELINE"

        val pgCfg = PgVectorConfig.fromEnv()
        val graphJsonPath = System.getenv("GRAPH_JSON_PATH") ?: "build/graph.json"
        val projectRoot = System.getenv("CODEBASE_PROJECT_ROOT") ?: System.getProperty("user.dir")

        val channelConfig = resolveChannels(scenarioId, projectRoot)

        val outputDir = File("build/benchmark-reports")
        outputDir.mkdirs()

        val runner = BenchmarkRunner(
            pgJdbcUrl = pgCfg.jdbcUrl, pgUser = pgCfg.user, pgPassword = pgCfg.password,
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
