package codebase.benchmark

import java.io.File

object BenchmarkReportExportMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val scenarioId = if (args.isNotEmpty()) args[0] else "BASELINE"
        val inputPath = if (args.size > 1) args[1] else "build/benchmark-reports/report-$scenarioId.json"

        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            System.err.println("ERREUR: fichier JSON introuvable: ${inputFile.absolutePath}")
            return
        }

        val json = inputFile.readText()
        val adoc = BenchmarkReportExporter.exportAsciiDoc(json, scenarioId)

        val outputDir = inputFile.parentFile
        val adocFile = File(outputDir, "report-$scenarioId.adoc")
        adocFile.writeText(adoc)

        println(adocFile.absolutePath)
    }
}
