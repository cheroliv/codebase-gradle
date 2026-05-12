package codebase.benchmark

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
data class BenchmarkReport(
    val scenario: String = "",
    val channels: List<String> = emptyList(),
    val summary: String = "",
    val results: List<ThresholdData> = emptyList()
)

@Serializable
data class ThresholdData(
    val threshold: String = "",
    val totalSamples: Int = 0,
    val errorRate: Double = 0.0,
    val boundaryCrossings: List<CrossingData> = emptyList()
)

@Serializable
data class CrossingData(
    val documentId: String = "",
    val expectedCircle: Int = 0,
    val actualCircle: Int = 0,
    val confidenceScore: Double = 0.0,
    val excerpt: String = ""
)

data class Crossing(val documentId: String, val expectedCircle: Int, val actualCircle: Int, val confidenceScore: Double)
data class ThresholdResult(val threshold: String, val totalSamples: Int, val errorRate: Double, val crossings: List<Crossing>)

object BenchmarkReportExporter {

    private val json = Json { ignoreUnknownKeys = true }

    internal fun parseObject(jsonString: String): BenchmarkReport =
        json.decodeFromString<BenchmarkReport>(jsonString)

    fun exportAsciiDoc(jsonReport: String, scenarioId: String): String {
        val root = json.decodeFromString<BenchmarkReport>(jsonReport.trim())

        val scenarioName = root.scenario.ifEmpty { scenarioId }
        val channels = root.channels
        val results = root.results.map { thresholdData ->
            ThresholdResult(
                threshold = thresholdData.threshold,
                totalSamples = thresholdData.totalSamples,
                errorRate = thresholdData.errorRate,
                crossings = thresholdData.boundaryCrossings.map { crossing ->
                    Crossing(
                        documentId = crossing.documentId,
                        expectedCircle = crossing.expectedCircle,
                        actualCircle = crossing.actualCircle,
                        confidenceScore = crossing.confidenceScore
                    )
                }
            )
        }

        val sb = StringBuilder()

        sb.appendLine("= EPIC 4 — Benchmark Perception Spatiale LLM")
        sb.appendLine(":toc: left")
        sb.appendLine(":icons: font")
        sb.appendLine(":sectnums:")
        sb.appendLine(":report-date: ${Instant.now()}")
        sb.appendLine(":model: deepseek-v4-pro:cloud")
        sb.appendLine()
        sb.appendLine("[abstract]")
        sb.appendLine("--")
        sb.appendLine(root.summary)
        sb.appendLine("--")
        sb.appendLine()

        val channelStr = channels.joinToString(", ").ifEmpty { "AUCUN (baseline brute)" }

        sb.appendLine("== Scenario: $scenarioName")
        sb.appendLine()
        sb.appendLine("[cols=\"1,3\"]")
        sb.appendLine("|===")
        sb.appendLine("| ID | $scenarioName")
        sb.appendLine("| Canaux actives | $channelStr")
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Cercles de Confiance")
        sb.appendLine()
        sb.appendLine("[cols=\"1,5\"]")
        sb.appendLine("|===")
        sb.appendLine("| C0 | Workspace racine (hors-CVS) — Vision, strategie, brain dump")
        sb.appendLine("| C1 | configuration/ — Secrets, tokens, credentials (non versionne)")
        sb.appendLine("| C2 | office/ — Donnees privees, cours, datasets (cercle 2)")
        sb.appendLine("| C3 | foundry/CSS/ — Code source closed-source (licence proprietaire)")
        sb.appendLine("| C4 | foundry/OSS/ — Code open source (Apache 2.0, public)")
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Resultats par Seuil de Tokens")
        sb.appendLine()

        for (result in results) {
            val errorPct = String.format("%.1f%%", result.errorRate * 100)
            val correctCount = result.totalSamples - result.crossings.size

            sb.appendLine("=== Seuil ${result.threshold}")
            sb.appendLine()
            sb.appendLine("[cols=\"2,1\"]")
            sb.appendLine("|===")
            sb.appendLine("| Taux d'erreur | *$errorPct*")
            sb.appendLine("| Echantillons corrects | $correctCount / ${result.totalSamples}")
            sb.appendLine("| Franchissements detectes | ${result.crossings.size}")
            sb.appendLine("|===")
            sb.appendLine()

            if (result.crossings.isNotEmpty()) {
                sb.appendLine(".Franchissements de cercle")
                sb.appendLine("[cols=\"2,1,1,2\"]")
                sb.appendLine("|===")
                sb.appendLine("| Document | Cercle attendu | Cercle predit | Confiance")
                for (c in result.crossings) {
                    val confidence = String.format("%.1f%%", c.confidenceScore * 100)
                    sb.appendLine("| ${c.documentId} | C${c.expectedCircle} | C${c.actualCircle} | $confidence")
                }
                sb.appendLine("|===")
                sb.appendLine()
            } else {
                sb.appendLine("*Aucun franchissement de cercle detecte a ce seuil.*")
                sb.appendLine()
            }
        }

        sb.appendLine("== Metrique Cle")
        sb.appendLine()
        sb.appendLine("- *Taux d'erreur de classification* = BoundaryCrossingEvents / TotalSamples")
        sb.appendLine("- *Objectif* : Determiner le seuil de tokens a partir duquel deepseek-v4-pro")
        sb.appendLine("  perd la notion de cercle de confiance.")
        sb.appendLine("- *Methode* : Injection de contexte de remplissage technique a N tokens,")
        sb.appendLine("  classification de 7 documents de test par cercle.")
        sb.appendLine()

        sb.appendLine("== Echantillons de Test")
        sb.appendLine()
        sb.appendLine("[cols=\"1,1,5\"]")
        sb.appendLine("|===")
        sb.appendLine("| ID | Cercle | Extrait (debut)")
        sb.appendLine("| C0-strategie | C0 | WORKSPACE_AS_PRODUCT.adoc")
        sb.appendLine("| C1-tokens | C1 | configuration/codebase.yml")
        sb.appendLine("| C2-pedagogie | C2 | office/metiers/FPA/SPG_A2SP.adoc")
        sb.appendLine("| C2-livre | C2 | office/books-collection/kotlin-in-action.pdf")
        sb.appendLine("| C3-closed | C3 | foundry/CSS/proprietary-algo/")
        sb.appendLine("| C4-plugin | C4 | foundry/OSS/plantuml-gradle/")
        sb.appendLine("| C4-readme | C4 | foundry/OSS/codebase-gradle/build.gradle.kts")
        sb.appendLine("|===")

        return sb.toString()
    }
}
