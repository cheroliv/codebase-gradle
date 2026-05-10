package codebase.benchmark

import org.slf4j.LoggerFactory
import java.io.File

object BenchmarkRunnerMain {
    private val log = LoggerFactory.getLogger(BenchmarkRunnerMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val scenarioId = if (args.isNotEmpty()) args[0] else "BASELINE"

        val channels = when (scenarioId) {
            "BASELINE" -> emptyList()
            "RAG_ONLY" -> listOf("RAG")
            "RAG_GRAPHIFY_LOCAL" -> listOf("RAG", "Graphify")
            "RAG_GRAPHIFY_WORKSPACE" -> listOf("RAG", "Graphify")
            "FOUR_CHANNELS" -> listOf("EAGER/LAZY", "RAG", "Graphify", "Ressources")
            else -> emptyList()
        }

        val pgJdbcUrl = System.getenv("PGVECTOR_JDBC_URL") ?: "jdbc:postgresql://localhost:5432/codebase_rag"
        val pgUser = System.getenv("PGVECTOR_USER") ?: "codebase"
        val pgPassword = System.getenv("PGVECTOR_PASSWORD") ?: "codebase"
        val graphJsonPath = System.getenv("GRAPH_JSON_PATH") ?: "build/graph.json"

        val outputDir = File("build/benchmark-reports")
        outputDir.mkdirs()

        val runner = BenchmarkRunner(pgJdbcUrl = pgJdbcUrl, pgUser = pgUser, pgPassword = pgPassword, graphJsonPath = graphJsonPath)
        val report = runner.run(scenarioId, channels)

        val reportFile = File(outputDir, "report-${scenarioId}.json")
        reportFile.writeText(report)

        val adoc = BenchmarkReportExporter.exportAsciiDoc(report, scenarioId)
        val adocFile = File(outputDir, "report-${scenarioId}.adoc")
        adocFile.writeText(adoc)

        log.info("Report written: {}", reportFile.absolutePath)
        log.info("AsciiDoc written: {}", adocFile.absolutePath)
    }
}
