package codebase.rag

import codebase.rag.StdoutFormatter.banner
import codebase.rag.StdoutFormatter.ctx
import codebase.rag.StdoutFormatter.plan
import codebase.rag.StdoutFormatter.result
import codebase.rag.StdoutFormatter.separator
import org.slf4j.LoggerFactory
import java.io.File

object PertinenceBenchmarkMain {
    private val log = LoggerFactory.getLogger(PertinenceBenchmarkMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
        val modelName = System.getenv("PERTINENCE_MODEL") ?: "deepseek-v4-pro:cloud"
        val contextFilePath = System.getenv("OPENDODE_CONTEXT_FILE") ?: "/tmp/opencode-context.txt"
        val outputDirPath = args.getOrNull(0) ?: "build/pertinence-reports"

        banner("US-9.13 — Pertinence Benchmark (gate MVP0)")
        ctx("Model    : $modelName ($baseUrl)")
        ctx("Context  : $contextFilePath")
        ctx("Output   : $outputDirPath")

        val contextExists = File(contextFilePath).isFile
        if (!contextExists) {
            plan("Fichier de contexte absent — execution du pipeline augmentOpencode...")
            plan("[ASTUCE] Lancer './gradlew augmentOpencode -PragQuestion=\"architecture du workspace\"' avant ce benchmark")
        }

        plan("Lancement du benchmark sur ${PertinenceQuestions.all.size} questions metier...")

        val runner = PertinenceBenchmarkRunner(
            baseUrl = baseUrl,
            modelName = modelName,
            contextFilePath = contextFilePath
        )
        val report = runner.run()

        separator()

        result("Nombre total de questions          : ${report.totalQuestions}")
        result("Questions ameliorees avec contexte : ${report.improvedCount}")
        result("Questions degradees                : ${report.degradedCount}")
        result("Questions inchangees               : ${report.unchangedCount}")
        result("Taux d'amelioration                : ${"%.1f".format(report.improvementRate * 100)}%")
        result("Seuil MVP0 (>70%)                  : ${if (report.mvp0Validated) "✅ VALIDE" else "❌ NON ATTEINT"}")
        separator()

        val outputDir = File(outputDirPath)
        outputDir.mkdirs()

        val json = PertinenceReportExporter.exportJson(report)
        val jsonFile = File(outputDir, "pertinence-benchmark.json")
        jsonFile.writeText(json)
        log.info("JSON written: {}", jsonFile.absolutePath)

        val adoc = PertinenceReportExporter.exportAsciiDoc(report)
        val adocFile = File(outputDir, "pertinence-benchmark.adoc")
        adocFile.writeText(adoc)
        log.info("AsciiDoc written: {}", adocFile.absolutePath)

        ctx("Rapports generes :")
        ctx("  JSON    : ${jsonFile.absolutePath}")
        ctx("  AsciiDoc: ${adocFile.absolutePath}")

        if (report.mvp0Validated) {
            result("MVP0 EPIC 9 VALIDE ! Amelioration significative sur ${"%.1f".format(report.improvementRate * 100)}% des questions (>70%).")
        } else {
            result("MVP0 EPIC 9 non atteint. Taux = ${"%.1f".format(report.improvementRate * 100)}% (seuil 70%).")
        }
    }
}
