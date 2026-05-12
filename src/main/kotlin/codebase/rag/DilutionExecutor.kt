package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class DilutionResult(
    val documentPath: String,
    val sectionInjected: String,
    val success: Boolean,
    val backupPath: String?,
    val error: String?
)

class DilutionExecutor(
    private val workspaceRoot: String,
    private val dryRun: Boolean = false
) {
    private val log = LoggerFactory.getLogger(DilutionExecutor::class.java)
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy")

    fun execute(record: DilutionRecord): DilutionResult {
        val target = record.dilutionTarget
        if (target == null) {
            return DilutionResult(
                documentPath = "",
                sectionInjected = "",
                success = false,
                backupPath = null,
                error = "Aucune cible de dilution (section classifiée OPINION)"
            )
        }

        val docFile = File(workspaceRoot, target.targetDocument.path)
        if (!docFile.exists()) {
            return DilutionResult(
                documentPath = target.targetDocument.path,
                sectionInjected = target.suggestedSection,
                success = false,
                backupPath = null,
                error = "Fichier cible introuvable: ${docFile.absolutePath}"
            )
        }

        return try {
            val originalContent = docFile.readText(Charsets.UTF_8)

            if (dryRun) {
                handleDryRun(docFile, originalContent, target)
            } else {
                handleRealExecution(docFile, originalContent, record, target)
            }
        } catch (e: Exception) {
            log.error("Erreur lors de la dilution de {}: {}", record.sectionId, e.message, e)
            DilutionResult(
                documentPath = target.targetDocument.path,
                sectionInjected = target.suggestedSection,
                success = false,
                backupPath = null,
                error = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun handleDryRun(
        docFile: File,
        originalContent: String,
        target: DilutionTarget
    ): DilutionResult {
        val sectionExists = findSectionStart(originalContent, target.suggestedSection)
        if (sectionExists == null) {
            StdoutFormatter.ctx("  DRY RUN — section '${target.suggestedSection}' serait créée dans ${docFile.name}")
        } else {
            StdoutFormatter.ctx("  DRY RUN — contenu serait injecté après '${target.suggestedSection}' dans ${docFile.name}")
        }
        return DilutionResult(
            documentPath = target.targetDocument.path,
            sectionInjected = target.suggestedSection,
            success = true,
            backupPath = null,
            error = null
        )
    }

    private fun handleRealExecution(
        docFile: File,
        originalContent: String,
        record: DilutionRecord,
        target: DilutionTarget
    ): DilutionResult {
        val backupPath = createBackup(docFile)

        StdoutFormatter.plan("Mise a jour de ${docFile.name}...")

        val formattedSection = formatDilutedSection(record)
        val updatedContent = injectSection(originalContent, formattedSection, target.suggestedSection)
        val finalContent = updateTraceabilityTable(updatedContent, record)

        docFile.writeText(finalContent, Charsets.UTF_8)
        log.info("Document mis a jour: {}", docFile.absolutePath)

        return DilutionResult(
            documentPath = target.targetDocument.path,
            sectionInjected = target.suggestedSection,
            success = true,
            backupPath = backupPath,
            error = null
        )
    }

    private fun createBackup(docFile: File): String {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupFile = File(docFile.parent, "${docFile.name}.bak.$ts")
        docFile.copyTo(backupFile, overwrite = false)
        log.info("Backup créé: {}", backupFile.absolutePath)
        return backupFile.absolutePath
    }

    private fun findSectionStart(content: String, sectionTitle: String): Int? {
        val cleanTitle = sectionTitle.trim().removePrefix("==").trim()
        val patterns = listOf(
            Regex("""^== \Q$cleanTitle\E\s*$""", RegexOption.MULTILINE),
            Regex("""^===\s+\Q$cleanTitle\E\s*$""", RegexOption.MULTILINE),
            Regex("""^====\s+\Q$cleanTitle\E\s*$""", RegexOption.MULTILINE)
        )
        for (pattern in patterns) {
            val match = pattern.find(content) ?: continue
            val endOfLine = content.indexOf('\n', match.range.last)
            return if (endOfLine >= 0) endOfLine else content.length
        }
        return null
    }

    private fun formatDilutedSection(record: DilutionRecord): String {
        val date = record.timestamp.toLocalDate().format(dateFmt)
        val lines = listOf(
            "",
            "=== ${record.sectionTitle}",
            "",
            record.content.trim(),
            "",
            "[discrete]",
            "==== _Métadonnées de dilution_",
            "",
            "[cols=\"2,3\"]",
            "|===",
            "| Date | $date",
            "| Classification | ${record.classification} (confiance: ${"%.2f".format(record.confidence)})",
            "| Rationale classification | ${record.classificationRationale}",
            "| Rationale routage | ${record.dilutionTarget?.rationale ?: "—"}",
            "|===",
            ""
        )
        return lines.joinToString("\n")
    }

    private fun injectSection(
        content: String,
        formattedSection: String,
        suggestedSection: String
    ): String {
        val insertionPoint = findSectionStart(content, suggestedSection)
        return if (insertionPoint != null) {
            val headerEnd = content.indexOf('\n', insertionPoint)
            val afterHeader = if (headerEnd >= 0) headerEnd + 1 else insertionPoint
            content.substring(0, afterHeader) + formattedSection + content.substring(afterHeader)
        } else {
            content.trimEnd() + "\n\n== $suggestedSection\n" + formattedSection
        }
    }

    private fun updateTraceabilityTable(content: String, record: DilutionRecord): String {
        val tableHeader = "=== Stimuli dilués dans ce document"
        val tableMarker = "[cols=\"1,1,3\"]"
        val closingMarker = "|==="

        val headerIdx = content.indexOf(tableHeader)
        if (headerIdx < 0) {
            return injectTraceabilityTable(content, record)
        }

        val afterTableStart = content.indexOf("|===", headerIdx)
        if (afterTableStart < 0) return injectTraceabilityTable(content, record)

        var tableEnd = content.indexOf("|===", afterTableStart + 4)
        if (tableEnd < 0) tableEnd = content.length else tableEnd += 4

        val beforeTable = content.substring(0, tableEnd)
        val afterTable = content.substring(tableEnd)

        val date = record.timestamp.toLocalDate().format(dateFmt)
        val sourceName = record.dilutionTarget?.let {
            it.targetDocument.label.take(20)
        } ?: record.sectionTitle.take(40)
        val newRow = "| ${record.sectionTitle.take(30)} | $date | ${record.sectionTitle}"

        val updatedTable = beforeTable.trimEnd() + "\n$newRow\n" + closingMarker + "\n"
        return updatedTable + afterTable.trimStart()
    }

    private fun injectTraceabilityTable(content: String, record: DilutionRecord): String {
        val lastSectionEnd = findLastSectionEnd(content)
        val before = content.substring(0, lastSectionEnd)
        val after = content.substring(lastSectionEnd)

        val date = record.timestamp.toLocalDate().format(dateFmt)

        val table = """
|
== Traceabilité des Dilutions

=== Stimuli dilués dans ce document

[cols="1,1,3"]
|===
| Stimulus | Date | Sections enrichies
| ${record.sectionTitle.take(40)} | $date | ${record.sectionTitle}
|===
""".trimStart()

        return before.trimEnd() + "\n\n" + table + after
    }

    private fun findLastSectionEnd(content: String): Int {
        val sectionRegex = Regex("""^== .+$""", RegexOption.MULTILINE)
        val matches = sectionRegex.findAll(content).toList()
        if (matches.isEmpty()) return 0

        val lastMatch = matches.last()
        val lastSectionStart = lastMatch.range.last + 1

        val afterSectionContent = content.substring(lastSectionStart)
        val nextHeading = Regex("""^={1,4}\s""", RegexOption.MULTILINE).find(afterSectionContent)
        val nextHeadingIdx = if (nextHeading != null) {
            lastSectionStart + nextHeading.range.first
        } else {
            content.length
        }

        return nextHeadingIdx
    }
}
