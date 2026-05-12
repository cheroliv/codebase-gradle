package codebase.benchmark

import codebase.benchmark.BenchmarkRunnerMain.ChannelConfig
import codebase.rag.PgVectorConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

object BenchmarkComparisonMain {
    private val log = LoggerFactory.getLogger(BenchmarkComparisonMain::class.java)

    private val scenarioIds = listOf("BASELINE", "RAG_ONLY", "RAG_GRAPHIFY_LOCAL", "RAG_GRAPHIFY_WORKSPACE", "FOUR_CHANNELS")

    data class ScenarioRecord(
        val id: String,
        val channels: List<String>,
        val results: List<ThresholdRecord>
    )

    data class ThresholdRecord(
        val threshold: String,
        val errorRate: Double,
        val totalSamples: Int,
        val errorCount: Int
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val projectRoot = System.getenv("CODEBASE_PROJECT_ROOT") ?: System.getProperty("user.dir")
        val pgCfg = PgVectorConfig.fromEnv()
        val graphJsonPath = System.getenv("GRAPH_JSON_PATH") ?: "build/graph.json"
        val baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
        val modelName = System.getenv("OLLAMA_MODEL") ?: "deepseek-v4-pro:cloud"

        val outputDir = File("build/benchmark-reports")
        outputDir.mkdirs()

        val records = mutableListOf<ScenarioRecord>()

        for (scenarioId in scenarioIds) {
            val channelConfig = BenchmarkRunnerMain.resolveChannels(scenarioId, projectRoot)

            log.info("Running scenario: {} (channels: {})", scenarioId, channelConfig.channels)

            val runner = BenchmarkRunner(
                baseUrl = baseUrl, modelName = modelName,
                pgJdbcUrl = pgCfg.jdbcUrl, pgUser = pgCfg.user, pgPassword = pgCfg.password,
                graphJsonPath = channelConfig.graphPath ?: graphJsonPath,
                scopeFilter = channelConfig.scopeFilter
            )
            val reportJson = runner.run(scenarioId, channelConfig.channels)

            val reportFile = File(outputDir, "report-${scenarioId}.json")
            reportFile.writeText(reportJson)
            log.info("  -> {}", reportFile.name)

            val thresholdResults = parseThresholdResults(reportJson)
            records.add(
                ScenarioRecord(
                    id = scenarioId,
                    channels = channelConfig.channels,
                    results = thresholdResults
                )
            )
        }

        val comparisonAdoc = generateComparisonAsciiDoc(records, modelName)
        val adocFile = File(outputDir, "comparison-report.adoc")
        adocFile.writeText(comparisonAdoc)
        log.info("Comparison report: {}", adocFile.absolutePath)

        val gateStatus = evaluateGate(records)
        val gateFile = File(outputDir, "gate-evaluation.adoc")
        gateFile.writeText(gateStatus)
        log.info("Gate evaluation: {}", gateFile.absolutePath)
    }

    private fun parseThresholdResults(reportJson: String): List<ThresholdRecord> {
        val report = BenchmarkReportExporter.parseObject(reportJson.trim())
        return report.results.map { thresholdData ->
            ThresholdRecord(
                threshold = thresholdData.threshold,
                totalSamples = thresholdData.totalSamples,
                errorRate = thresholdData.errorRate,
                errorCount = thresholdData.boundaryCrossings.size
            )
        }
    }

    private fun generateComparisonAsciiDoc(records: List<ScenarioRecord>, modelName: String): String {
        val sb = StringBuilder()

        sb.appendLine("= EPIC 4 — Comparaison Multi-Canal Benchmark Perception Spatiale")
        sb.appendLine(":toc: left")
        sb.appendLine(":icons: font")
        sb.appendLine(":sectnums:")
        sb.appendLine(":report-date: ${Instant.now()}")
        sb.appendLine(":model: $modelName")
        sb.appendLine()

        sb.appendLine("[abstract]")
        sb.appendLine("--")
        sb.appendLine("Comparaison des 5 scénarios (BASELINE → FOUR_CHANNELS) sur 5 seuils de tokens (10K → 128K).")
        sb.appendLine("Objectif : mesurer la dégradation de la perception spatiale du LLM et l'apport incrémental")
        sb.appendLine("de chaque canal convergent (RAG pgvector, Graphify knowledge graph, EAGER/LAZY, Ressources).")
        sb.appendLine("--")
        sb.appendLine()

        sb.appendLine("== Matrice de Comparaison (Error Rate %)")
        sb.appendLine()

        val thresholds = listOf("10K", "30K", "60K", "100K", "128K")

        sb.append("[cols=\"1,${records.size * 2}\",options=\"header\"]")
        sb.appendLine()
        sb.append("|===")
        sb.appendLine()
        sb.append("| Seuil")
        for (r in records) {
            sb.append(" | ${r.id} Err% | ${r.id} Err#")
        }
        sb.appendLine()

        for (threshold in thresholds) {
            sb.append("| $threshold")
            for (record in records) {
                val tr = record.results.find { it.threshold == threshold }
                if (tr != null) {
                    val pct = String.format("%.1f%%", tr.errorRate * 100)
                    sb.append(" | $pct | ${tr.errorCount}/${tr.totalSamples}")
                } else {
                    sb.append(" | N/A | N/A")
                }
            }
            sb.appendLine()
        }
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Scénarios Exécutés")
        sb.appendLine()
        sb.appendLine("[cols=\"1,3,5\"]")
        sb.appendLine("|===")
        sb.appendLine("| Scénario | Canaux | Description")
        sb.appendLine("| BASELINE | AUCUN | Baseline brute, contexte de remplissage technique uniquement")
        sb.appendLine("| RAG_ONLY | RAG | Vectorisation + similarité cosinus pgvector sur le corpus workspace")
        sb.appendLine("| RAG_GRAPHIFY_LOCAL | RAG + Graphify | RAG + knowledge graph scope projet codebase-gradle")
        sb.appendLine("| RAG_GRAPHIFY_WORKSPACE | RAG + Graphify | RAG + knowledge graph scope workspace complet")
        sb.appendLine("| FOUR_CHANNELS | EAGER/LAZY + RAG + Graphify + Ressources | 4 canaux convergents complet")
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Analyse par Scénario")
        sb.appendLine()

        for (record in records) {
            val channelStr = record.channels.ifEmpty { listOf("AUCUN") }.joinToString(" + ")
            sb.appendLine("=== ${record.id}")
            sb.appendLine()
            sb.appendLine("Canaux : $channelStr")
            sb.appendLine()
            val hasErrors = record.results.any { it.errorCount > 0 }
            if (hasErrors) {
                sb.appendLine("[cols=\"1,2,1\"]")
                sb.appendLine("|===")
                sb.appendLine("| Seuil | Error Rate | Errors")
                for (tr in record.results.filter { it.errorCount > 0 }) {
                    sb.appendLine("| ${tr.threshold} | ${String.format("%.1f%%", tr.errorRate * 100)} | ${tr.errorCount}/${tr.totalSamples}")
                }
                sb.appendLine("|===")
            } else {
                sb.appendLine("Aucune erreur de classification détectée sur tous les seuils (0.0%).")
            }
            sb.appendLine()
        }

        sb.appendLine("== Évolution du Taux d'Erreur par Seuil (graphique ASCII)")
        sb.appendLine()

        for (threshold in thresholds) {
            sb.appendLine("=== Seuil $threshold")
            sb.appendLine()
            sb.appendLine("----")
            for (record in records) {
                val tr = record.results.find { it.threshold == threshold }
                val pct = if (tr != null) tr.errorRate * 100 else 0.0
                val bar = "█".repeat((pct * 2).toInt().coerceAtMost(20)) + "░".repeat(20 - (pct * 2).toInt().coerceAtMost(20))
                sb.appendLine("${record.id.padEnd(24)} $bar ${String.format("%.1f%%", pct)}")
            }
            sb.appendLine("----")
            sb.appendLine()
        }

        sb.appendLine("== Métrique Clé")
        sb.appendLine()
        sb.appendLine("- *Taux d'erreur de classification* = BoundaryCrossingEvents / TotalSamples")
        sb.appendLine("- *Gate MVP1* : RAG_GRAPHIFY_WORKSPACE < 5% erreur à 128K tokens")

        return sb.toString()
    }

    private fun evaluateGate(records: List<ScenarioRecord>): String {
        val workspaceRecord = records.find { it.id == "RAG_GRAPHIFY_WORKSPACE" }
        val result128k = workspaceRecord?.results?.find { it.threshold == "128K" }

        val sb = StringBuilder()
        sb.appendLine("= EPIC 4 — Gate MVP1 Evaluation")
        sb.appendLine(":report-date: ${Instant.now()}")
        sb.appendLine()

        sb.appendLine("== Gate : RAG_GRAPHIFY_WORKSPACE < 5% erreur à 128K tokens")
        sb.appendLine()

        if (result128k != null) {
            val errorPct = result128k.errorRate * 100
            val passed = errorPct < 5.0
            val status = if (passed) "✅ PASS" else "❌ FAIL"
            sb.appendLine("[cols=\"2,1,4\"]")
            sb.appendLine("|===")
            sb.appendLine("| Métrique | Valeur | Statut")
            sb.appendLine("| Error rate @ 128K | ${String.format("%.1f%%", errorPct)} | $status")
            sb.appendLine("| Errors | ${result128k.errorCount}/${result128k.totalSamples} |")
            sb.appendLine("| Gate threshold | < 5.0% |")
            sb.appendLine("|===")
            sb.appendLine()

            sb.appendLine("== Synthèse Tous Scénarios")
            sb.appendLine()
            sb.appendLine("[cols=\"1,1,1\"]")
            sb.appendLine("|===")
            sb.appendLine("| Scénario | Max Error Rate | Seuil Max")
            for (record in records) {
                val max = record.results.maxByOrNull { it.errorRate }
                if (max != null) {
                    sb.appendLine("| ${record.id} | ${String.format("%.1f%%", max.errorRate * 100)} | ${max.threshold}")
                } else {
                    sb.appendLine("| ${record.id} | N/A | N/A")
                }
            }
            sb.appendLine("|===")
        } else {
            sb.appendLine("WARNING: RAG_GRAPHIFY_WORKSPACE n'a pas été exécuté (aucun résultat 128K).")
        }

        return sb.toString()
    }
}
