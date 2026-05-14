package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File

object VisionOpinionClassifierMain {
    private val log = LoggerFactory.getLogger(VisionOpinionClassifierMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
        val modelName = System.getenv("PERTINENCE_MODEL") ?: "deepseek-v4-pro:cloud"
        val outputDirPath = args.getOrNull(0) ?: "build/vision-opinion-reports"

        StdoutFormatter.banner("US-9.14 — Segregation Vision/Opinion")
        StdoutFormatter.ctx("Model    : $modelName ($baseUrl)")
        StdoutFormatter.ctx("Output   : $outputDirPath")
        StdoutFormatter.ctx("Sections : ${TestSections.all.size} (5 VISION + 5 OPINION)")

        StdoutFormatter.plan("Classification des sections par le LLM...")

        val classifier = VisionOpinionClassifier(baseUrl = baseUrl, modelName = modelName)
        val report = classifier.classifyAll(TestSections.all)

        StdoutFormatter.separator()

        StdoutFormatter.result("Total sections       : ${report.sections.size}")
        StdoutFormatter.result("Classifiees VISION   : ${report.visionCount}")
        StdoutFormatter.result("Classifiees OPINION  : ${report.opinionCount}")
        StdoutFormatter.result("Erreurs              : ${report.errors}")
        StdoutFormatter.result("Confiance moyenne    : ${"%.2f".format(report.averageConfidence)}")

        val accuracy = if (report.sections.isNotEmpty())
            (report.sections.size - report.errors).toDouble() / report.sections.size
        else 0.0
        StdoutFormatter.result("Precision            : ${"%.1f".format(accuracy * 100)}%")

        StdoutFormatter.separator()

        for (sc in report.sections) {
            val status = if (TestSections.all.first { it.id == sc.sectionId }.expectedClassification == sc.classification) "OK" else "ERR"
            StdoutFormatter.ctx("$status | ${sc.sectionId} → ${sc.classification} (${"%.2f".format(sc.confidence)}) | ${sc.rationale}")
        }

        val outputDir = File(outputDirPath)
        outputDir.mkdirs()

        val json = exportJson(report)
        val jsonFile = File(outputDir, "vision-opinion-classification.json")
        jsonFile.writeText(json)
        log.info("JSON written: {}", jsonFile.absolutePath)

        val adoc = exportAsciiDoc(report)
        val adocFile = File(outputDir, "vision-opinion-classification.adoc")
        adocFile.writeText(adoc)
        log.info("AsciiDoc written: {}", adocFile.absolutePath)

        StdoutFormatter.ctx("Rapports generes :")
        StdoutFormatter.ctx("  JSON    : ${jsonFile.absolutePath}")
        StdoutFormatter.ctx("  AsciiDoc: ${adocFile.absolutePath}")

        if (report.errors == 0) {
            StdoutFormatter.result("Segregation Vision/Opinion TERMINEE — 0 erreur sur ${report.sections.size} sections ✅")
        } else {
            StdoutFormatter.result("Segregation Vision/Opinion : ${report.errors} erreurs sur ${report.sections.size} sections")
        }
    }

    private fun exportJson(report: ClassificationReport): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"totalSections\": ${report.sections.size},")
        sb.appendLine("  \"visionCount\": ${report.visionCount},")
        sb.appendLine("  \"opinionCount\": ${report.opinionCount},")
        sb.appendLine("  \"averageConfidence\": ${report.averageConfidence},")
        sb.appendLine("  \"errors\": ${report.errors},")
        sb.appendLine("  \"sections\": [")
        report.sections.forEachIndexed { idx, sc ->
            sb.appendLine("    {")
            sb.appendLine("      \"sectionId\": \"${sc.sectionId}\",")
            sb.appendLine("      \"classification\": \"${sc.classification}\",")
            sb.appendLine("      \"confidence\": ${sc.confidence},")
            sb.appendLine("      \"rationale\": \"${sc.rationale}\"")
            val comma = if (idx < report.sections.size - 1) "," else ""
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun exportAsciiDoc(report: ClassificationReport): String {
        val sb = StringBuilder()
        sb.appendLine("= EPIC 9 (US-9.14) — Segregation Vision/Opinion")
        sb.appendLine(":toc: left")
        sb.appendLine(":icons: font")
        sb.appendLine(":sectnums:")
        sb.appendLine()
        sb.appendLine("[abstract]")
        sb.appendLine("--")
        sb.appendLine("Classification automatique VISION vs OPINION par le LLM via prompt engineering.")
        sb.appendLine("Le LLM assume la responsabilite de classifier le contenu avant dilution et publication.")
        sb.appendLine("--")
        sb.appendLine()

        val accuracy = if (report.sections.isNotEmpty())
            (report.sections.size - report.errors).toDouble() / report.sections.size * 100
        else 0.0

        sb.appendLine("== Synthese")
        sb.appendLine()
        sb.appendLine("[cols=\"2,1\"]")
        sb.appendLine("|===")
        sb.appendLine("| Total sections | ${report.sections.size}")
        sb.appendLine("| VISION | ${report.visionCount}")
        sb.appendLine("| OPINION | ${report.opinionCount}")
        sb.appendLine("| Confiance moyenne | ${"%.2f".format(report.averageConfidence)}")
        sb.appendLine("| Erreurs | ${report.errors}")
        sb.appendLine("| Precision | ${"%.1f".format(accuracy)}%")
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Resultats par Section")
        sb.appendLine()
        sb.appendLine("[cols=\"1,2,2,3\"]")
        sb.appendLine("|===")
        sb.appendLine("| ID | Classification | Confiance | Justification")
        for (sc in report.sections) {
            val expected = TestSections.all.first { it.id == sc.sectionId }.expectedClassification
            val status = if (expected == sc.classification) "[.text-success]#OK#" else "[.text-danger]#ERR#"
            sb.appendLine("| ${sc.sectionId} $status | ${sc.classification} | ${"%.2f".format(sc.confidence)} | ${sc.rationale}")
        }
        sb.appendLine("|===")
        sb.appendLine()

        val gateThreshold = 80.0
        val gateOk = accuracy >= gateThreshold
        sb.appendLine("== Decision US-9.14")
        sb.appendLine()
        if (gateOk) {
            sb.appendLine("[TIP]")
            sb.appendLine("====")
            sb.appendLine("*US-9.14 VALIDE*. Le LLM classifie correctement VISION vs OPINION avec ${"%.1f".format(accuracy)}% de precision")
            sb.appendLine("(seuil ${"%.0f".format(gateThreshold)}%). EPIC 9 est desormais TERMINE (14/14 US).")
            sb.appendLine("====")
        } else {
            sb.appendLine("[WARNING]")
            sb.appendLine("====")
            sb.appendLine("*US-9.14 NON VALIDEE*. Precision = ${"%.1f".format(accuracy)}%, seuil = ${"%.0f".format(gateThreshold)}%.")
            sb.appendLine("Reexaminer le prompt systeme ou les sections de test.")
            sb.appendLine("====")
        }

        return sb.toString()
    }
}
