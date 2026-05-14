package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File

object StimulusDetectorMain {
    private val log = LoggerFactory.getLogger(StimulusDetectorMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val workspaceRoot = args.getOrNull(0)
            ?: System.getenv("WORKSPACE_ROOT")
            ?: System.getProperty("user.dir")
        val outputDirPath = args.getOrNull(1) ?: "build/stimulus-reports"
        val staleOnly = args.any { it == "--stale-only" }

        StdoutFormatter.banner("EPIC 10 — StimulusDetector : Détection stimuli actifs")
        StdoutFormatter.ctx("Workspace     : $workspaceRoot")
        StdoutFormatter.ctx("Output        : $outputDirPath")
        StdoutFormatter.ctx("Mode          : ${if (staleOnly) "STALE ONLY" else "ALL ACTIVE"}")
        StdoutFormatter.separator()

        val detector = StimulusDetector(workspaceRoot)

        val allFiles = detector.scan()
        StdoutFormatter.ctx("Fichiers .adoc bruts scannés : ${allFiles.size}")
        allFiles.forEach { f ->
            StdoutFormatter.ctx("  ${f.name} (modifié ${f.lastModified}, age ${f.estimatedAgeDays}j)")
        }
        StdoutFormatter.separator()

        val active = if (staleOnly) detector.detectStale() else detector.detectActive()

        StdoutFormatter.result("Stimuli actifs détectés : ${active.size}")
        active.forEach { s ->
            val status = if (s.stale) "⚠️ STALE" else "✅"
            StdoutFormatter.ctx("  $status ${s.file.name} (${s.ageDays}j)")
        }
        StdoutFormatter.separator()

        if (staleOnly && active.isNotEmpty()) {
            StdoutFormatter.error("⚠️ ${active.size} stimuli STALE (>${StimulusDetector.STALE_THRESHOLD_DAYS}j) — nécessitent dilution ou suppression")
        }

        val outputDir = File(outputDirPath)
        outputDir.mkdirs()

        val adocReport = detector.reportAsciiDoc(active)
        val adocFile = File(outputDir, "stimulus-detection-report.adoc")
        adocFile.writeText(adocReport)
        log.info("AsciiDoc report: {}", adocFile.absolutePath)

        val jsonReport = detector.exportStimuliJson(active)
        val jsonFile = File(outputDir, "stimulus-detection-report.json")
        jsonFile.writeText(jsonReport)
        log.info("JSON report: {}", jsonFile.absolutePath)

        StdoutFormatter.result("Rapports :")
        StdoutFormatter.ctx("  AsciiDoc: ${adocFile.absolutePath}")
        StdoutFormatter.ctx("  JSON    : ${jsonFile.absolutePath}")

        if (staleOnly && active.isNotEmpty()) {
            System.exit(1)
        }
    }
}
