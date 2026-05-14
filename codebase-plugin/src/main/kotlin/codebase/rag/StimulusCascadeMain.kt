package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File

object StimulusCascadeMain {
    private val log = LoggerFactory.getLogger(StimulusCascadeMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
        val modelName = System.getenv("PERTINENCE_MODEL") ?: "deepseek-v4-pro:cloud"
        val workspaceRoot = args.getOrNull(1) ?: System.getenv("WORKSPACE_ROOT") ?: ""
        val outputDirPath = args.getOrNull(2) ?: "build/stimulus-reports"
        val dryRun = args.any { it == "--dry-run" }

        val dumpContent: String = if (args.isNotEmpty()) {
            val dumpArg = args[0]
            if (File(dumpArg).exists()) {
                File(dumpArg).readText(Charsets.UTF_8)
            } else {
                dumpArg
            }
        } else {
            System.err.println("Usage: diluteBrainDump <brain-dump-text-or-file> [workspaceRoot] [outputDir] [--dry-run]")
            System.err.println("       Passe le brain dump en argument (chaine de caracteres ou chemin de fichier)")
            return
        }

        StdoutFormatter.banner("EPIC 10 — STIMULUS Cascade : Brain Dump → Classification → Routing → Archivage")
        StdoutFormatter.ctx("Model         : $modelName ($baseUrl)")
        StdoutFormatter.ctx("Workspace     : ${workspaceRoot.ifBlank { "(cwd)" }}")
        StdoutFormatter.ctx("Output        : $outputDirPath")
        StdoutFormatter.ctx("Mode          : ${if (dryRun) "DRY RUN (pas d'archivage)" else "EXECUTION REELLE"}")
        StdoutFormatter.ctx("Source size   : ${dumpContent.length} caracteres")
        StdoutFormatter.separator()

        val cascade = StimulusCascade(
            baseUrl = baseUrl,
            modelName = modelName,
            workspaceRoot = workspaceRoot,
            dryRun = dryRun
        )

        val report = cascade.execute(dumpContent)

        val outputDir = File(outputDirPath)
        outputDir.mkdirs()

        val jsonReport = cascade.exportCascadeJson(report)
        val jsonFile = File(outputDir, "stimulus-cascade.json")
        jsonFile.writeText(jsonReport)
        log.info("JSON report written: {}", jsonFile.absolutePath)

        val adocReport = cascade.exportCascadeAsciiDoc(report)
        val adocFile = File(outputDir, "stimulus-cascade.adoc")
        adocFile.writeText(adocReport)
        log.info("AsciiDoc report written: {}", adocFile.absolutePath)

        StdoutFormatter.separator()
        StdoutFormatter.ctx("Rapports generes :")
        StdoutFormatter.ctx("  JSON    : ${jsonFile.absolutePath}")
        StdoutFormatter.ctx("  AsciiDoc: ${adocFile.absolutePath}")
        StdoutFormatter.separator()

        if (report.visionCount > 0) {
            StdoutFormatter.result("Pipeline STIMULUS TERMINE — ${report.visionCount} sections VISION routees, ${report.opinionCount} sections OPINION confinees ✅")
        } else {
            StdoutFormatter.result("Pipeline STIMULUS TERMINE — ${report.sections.size} sections (0 VISION, ${report.opinionCount} OPINION confinees)")
        }
    }
}
