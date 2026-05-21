package codebase.benchmark

import codebase.benchmark.BenchmarkReportExporter.exportAsciiDoc
import java.io.File

object BenchmarkReportExportMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val scenarioId = if (args.isNotEmpty()) args[0] else "BASELINE"
        val inputPath = if (args.size > 1) args[1] else "benchmark-output/report-$scenarioId.json"

        val inputFile = File(inputPath)
        if (!inputFile.exists()) {
            System.err.println("ERREUR: fichier JSON introuvable: ${inputFile.absolutePath}")
            return
        }


        File(inputFile.parentFile, "report-$scenarioId.adoc").apply {
            writeText(exportAsciiDoc(inputFile.readText(), scenarioId))
            println(absolutePath)
        }
    }
}
