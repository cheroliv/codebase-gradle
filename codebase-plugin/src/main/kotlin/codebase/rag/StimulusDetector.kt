package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class StimulusFile(
    val name: String,
    val path: String,
    val lastModified: LocalDateTime,
    val size: Long,
    val estimatedAgeDays: Long
)

data class ActiveStimulus(
    val file: StimulusFile,
    val ageDays: Long,
    val stale: Boolean
)

class StimulusDetector(private val workspaceRoot: String) {
    private val log = LoggerFactory.getLogger(StimulusDetector::class.java)

    companion object {
        val EXCLUDED_FILES = setOf(
            "WORKSPACE_VISION.adoc",
            "WORKSPACE_AS_PRODUCT.adoc",
            "WHAT_THE_GAMES_BEEN_MISSING.adoc",
            "WORKSPACE_ORGANIZATION.adoc",
            "AGENTS.adoc",
            "BOOM_BAP.adoc",
            "PROMPT_REPRISE.adoc",
            "AUGMENTED_CONTEXT_PIPELINE.adoc"
        )

        val STALE_THRESHOLD_DAYS = 2L
    }

    fun scan(): List<StimulusFile> {
        val wsDir = File(workspaceRoot)

        val adocFiles = wsDir.listFiles { file ->
            file.isFile && file.name.endsWith(".adoc") && file.name !in EXCLUDED_FILES
        } ?: emptyArray()

        return adocFiles.map { file ->
            val lastModified = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(file.lastModified()),
                java.time.ZoneId.systemDefault()
            )
            StimulusFile(
                name = file.name,
                path = file.absolutePath,
                lastModified = lastModified,
                size = file.length(),
                estimatedAgeDays = java.time.Duration.between(lastModified, LocalDateTime.now()).toDays()
            )
        }.sortedByDescending { it.lastModified }
    }

    fun detectActive(): List<ActiveStimulus> {
        val files = scan()
        if (files.isEmpty()) return emptyList()

        val dilutedNames = collectDilutedStimulusNames()

        val active = files.filter { sf ->
            val baseName = sf.name.removeSuffix(".adoc")
            !dilutedNames.any { dilutedName ->
                dilutedName.contains(baseName, ignoreCase = true) ||
                    baseName.contains(dilutedName, ignoreCase = true)
            }
        }

        return active.map { sf ->
            ActiveStimulus(
                file = sf,
                ageDays = sf.estimatedAgeDays,
                stale = sf.estimatedAgeDays > STALE_THRESHOLD_DAYS
            )
        }
    }

    fun detectStale(): List<ActiveStimulus> = detectActive().filter { it.stale }

    fun reportAsciiDoc(stimuli: List<ActiveStimulus>): String {
        val sb = StringBuilder()

        sb.appendLine("= Détection de Stimuli Actifs — EPIC 10")
        sb.appendLine(":icons: font")
        sb.appendLine(":report-date: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")
        sb.appendLine()

        sb.appendLine("[abstract]")
        sb.appendLine("--")
        sb.appendLine("Scan du répertoire `workspace/` — fichiers `.adoc` candidats non encore dilués.")
        sb.appendLine("--")
        sb.appendLine()

        sb.appendLine("== Stimuli actifs (non dilués)")
        sb.appendLine()

        val staleCount = stimuli.count { it.stale }

        sb.appendLine(".Vue d'ensemble")
        sb.appendLine("[cols=\"2,3\"]")
        sb.appendLine("|===")
        sb.appendLine("| Total stimuli actifs | ${stimuli.size}")
        sb.appendLine("| Stale (> $STALE_THRESHOLD_DAYS jours) | $staleCount")
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Détail par stimulus")
        sb.appendLine()
        sb.appendLine("[cols=\"1,2,1,1\"]")
        sb.appendLine("|===")
        sb.appendLine("| Fichier | Dernière modification | Age (jours) | Statut")
        for (s in stimuli) {
            val status = if (s.stale) "⚠️ STALE" else "✅ Récent"
            val modified = s.file.lastModified.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            sb.appendLine("| ${s.file.name} | $modified | ${s.ageDays}j | $status")
        }
        sb.appendLine("|===")
        sb.appendLine()

        if (staleCount > 0) {
            sb.appendLine("== ⚠️ Stimuli en retard de dilution")
            sb.appendLine()
            sb.appendLine("[IMPORTANT]")
            sb.appendLine("====")
            sb.appendLine("$staleCount stimulus sont en retard de dilution (> $STALE_THRESHOLD_DAYS sessions sans intégration).")
            sb.appendLine("Voir `AGENT_GOVERNANCE.adoc` — règle de mortalité STIMULUS.")
            sb.appendLine("====")
            sb.appendLine()
            sb.appendLine("Action recommandée : `./gradlew diluteBrainDump -Pdump=`")
        }

        return sb.toString()
    }

    fun exportStimuliJson(stimuli: List<ActiveStimulus>): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"timestamp\": \"${LocalDateTime.now()}\",")
        sb.appendLine("  \"totalActive\": ${stimuli.size},")
        sb.appendLine("  \"staleCount\": ${stimuli.count { it.stale }},")
        sb.appendLine("  \"stimuli\": [")
        stimuli.forEachIndexed { idx, s ->
            sb.appendLine("    {")
            sb.appendLine("      \"name\": \"${s.file.name}\",")
            sb.appendLine("      \"path\": \"${s.file.path}\",")
            sb.appendLine("      \"lastModified\": \"${s.file.lastModified}\",")
            sb.appendLine("      \"sizeBytes\": ${s.file.size},")
            sb.appendLine("      \"ageDays\": ${s.ageDays},")
            sb.appendLine("      \"stale\": ${s.stale}")
            val comma = if (idx < stimuli.size - 1) "," else ""
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun collectDilutedStimulusNames(): Set<String> {
        val dilutedNames = mutableSetOf<String>()

        for (docFile in listOf(
            "WORKSPACE_VISION.adoc",
            "WORKSPACE_AS_PRODUCT.adoc",
            "WHAT_THE_GAMES_BEEN_MISSING.adoc",
            "WORKSPACE_ORGANIZATION.adoc"
        )) {
            val file = File(workspaceRoot, docFile)
            if (!file.exists()) continue

            val content = file.readText(Charsets.UTF_8)
            val tableSection = content.indexOf("=== Stimuli dilués dans ce document")
            if (tableSection < 0) continue

            val afterHeading = content.indexOf("\n", tableSection)
            if (afterHeading < 0) continue

            val tableStart = content.indexOf("|===", afterHeading)
            if (tableStart < 0) continue

            var tableEnd = content.indexOf("|===", tableStart + 4)
            if (tableEnd < 0) tableEnd = content.length else tableEnd += 4

            val tableContent = content.substring(tableStart, tableEnd)
            val rowRegex = Regex("""^\|\s*(.+?)\s*\|""", RegexOption.MULTILINE)
            tableContent.lines().forEach { line ->
                val match = rowRegex.find(line) ?: return@forEach
                val stimulusName = match.groupValues[1].trim()
                if (stimulusName.isNotEmpty() && stimulusName != "Stimulus") {
                    dilutedNames.add(stimulusName)
                }
            }
        }

        return dilutedNames
    }
}
