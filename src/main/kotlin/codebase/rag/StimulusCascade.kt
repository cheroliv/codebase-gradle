package codebase.rag

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.ollama.OllamaChatModel
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class TargetDocument(val path: String, val label: String) {
    WORKSPACE_VISION("WORKSPACE_VISION.adoc", "Vision & Architecture"),
    WORKSPACE_AS_PRODUCT("WORKSPACE_AS_PRODUCT.adoc", "Produit & Business Model"),
    WHAT_THE_GAMES_BEEN_MISSING("WHAT_THE_GAMES_BEEN_MISSING.adoc", "Brainstorming & Idees"),
    WORKSPACE_ORGANIZATION("WORKSPACE_ORGANIZATION.adoc", "Organisation & Structure")
}

data class DilutionTarget(
    val targetDocument: TargetDocument,
    val suggestedSection: String,
    val rationale: String
)

data class DilutionRecord(
    val sectionId: String,
    val sectionTitle: String,
    val content: String,
    val classification: ContentClassification,
    val confidence: Double,
    val dilutionTarget: DilutionTarget?,
    val classificationRationale: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class StimulusCascadeReport(
    val source: String,
    val sourceHash: String,
    val sections: List<DilutionRecord> = emptyList(),
    val visionCount: Int = 0,
    val opinionCount: Int = 0,
    val dilutedCount: Int = 0,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

class StimulusCascade(
    private val baseUrl: String = "http://localhost:11434",
    private val modelName: String = "deepseek-v4-pro:cloud",
    private val timeoutSeconds: Long = 120,
    workspaceRoot: String = "",
    private val visionArchiveDir: String = "configuration/vision-archive",
    private val dryRun: Boolean = false
) {
    private val log = LoggerFactory.getLogger(StimulusCascade::class.java)
    private val classifier: VisionOpinionClassifier = VisionOpinionClassifier(baseUrl, modelName, timeoutSeconds)
    private val workspaceDir: File = if (workspaceRoot.isBlank()) File(".") else File(workspaceRoot)
    private val tsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

    private val routingSystemPrompt = """
Tu es un routeur de contenu du workspace. Ta mission est de determiner dans quel document racine du workspace une section de contenu doit etre integree.

Les 4 documents racines :

1. WORKSPACE_VISION.adoc — contient la vision, l'architecture (DAG 4 niveaux N0/N1/N2/N3), les cercles de confiance, les regles de gouvernance agent, les patterns EAGER/LAZY, Hot/Warm/Cold.

2. WORKSPACE_AS_PRODUCT.adoc — contient le positionnement produit, le business model, la strategie SaaS Edster, le pricing, les cibles commerciales, la roadmap produit publique.

3. WHAT_THE_GAMES_BEEN_MISSING.adoc — contient la pensee exploratoire, le brainstorming, les idees radicales, les reflexions sur l'avenir du workspace, les speculations techniques.

4. WORKSPACE_ORGANIZATION.adoc — contient l'organisation physique du workspace, la structure des dossiers, les regles de nommage, l'inventaire des fichiers par cercle.

Pour le contenu fourni, reponds UNIQUEMENT au format JSON (sans texte avant ni apres) :
{
  "targetDocument": "WORKSPACE_VISION" / "WORKSPACE_AS_PRODUCT" / "WHAT_THE_GAMES_BEEN_MISSING" / "WORKSPACE_ORGANIZATION",
  "suggestedSection": "titre de la section ou inserer (ex: == Regles de Gouvernance)",
  "rationale": "explication en 30 mots maximum"
}
""".trimIndent()

    data class ParsedSection(val id: String, val title: String, val content: String)

    fun execute(brainDump: String): StimulusCascadeReport {
        val sourceHash = sha256(brainDump)
        val timestamp = LocalDateTime.now()

        StdoutFormatter.plan("Parsing des sections du brain dump...")
        val rawSections = parseSections(brainDump)

        StdoutFormatter.ctx("${rawSections.size} sections identifiees")
        for (s in rawSections) {
            StdoutFormatter.ctx("  [${s.id}] ${s.title}")
        }
        StdoutFormatter.separator()

        StdoutFormatter.plan("Classification VISION/OPINION via LLM...")
        val classifiedSections = mutableListOf<DilutionRecord>()
        var visionCount = 0
        var opinionCount = 0
        var dilutedCount = 0

        for (section in rawSections) {
            val cs = ContentSection(
                id = section.id,
                content = section.content,
                expectedClassification = ContentClassification.VISION
            )
            val result = classifier.classify(cs)

            val record = when (result.classification) {
                ContentClassification.VISION -> {
                    visionCount++
                    StdoutFormatter.ctx("[${section.id}] VISION (${"%.2f".format(result.confidence)}) → routage vers document cible...")

                    val target = routeSection(section)
                    dilutedCount++

                    StdoutFormatter.ctx("  Cible : ${target.targetDocument.path} → ${target.suggestedSection}")

                    DilutionRecord(
                        sectionId = section.id,
                        sectionTitle = section.title,
                        content = section.content,
                        classification = ContentClassification.VISION,
                        confidence = result.confidence,
                        dilutionTarget = target,
                        classificationRationale = result.rationale,
                        timestamp = timestamp
                    )
                }
                ContentClassification.OPINION -> {
                    opinionCount++
                    StdoutFormatter.ctx("[${section.id}] OPINION (${"%.2f".format(result.confidence)}) → confinement (pas de dilution)")

                    DilutionRecord(
                        sectionId = section.id,
                        sectionTitle = section.title,
                        content = section.content,
                        classification = ContentClassification.OPINION,
                        confidence = result.confidence,
                        dilutionTarget = null,
                        classificationRationale = result.rationale,
                        timestamp = timestamp
                    )
                }
            }
            classifiedSections.add(record)
        }

        StdoutFormatter.separator()
        StdoutFormatter.result("Sections totales     : ${classifiedSections.size}")
        StdoutFormatter.result("Classees VISION      : $visionCount (diluees)")
        StdoutFormatter.result("Classees OPINION     : $opinionCount (confinees)")
        StdoutFormatter.result("Documents cibles     : ${classifiedSections.filter { it.dilutionTarget != null }.map { it.dilutionTarget!!.targetDocument.path }.distinct().joinToString(", ")}")

        val report = StimulusCascadeReport(
            source = brainDump,
            sourceHash = sourceHash,
            sections = classifiedSections,
            visionCount = visionCount,
            opinionCount = opinionCount,
            dilutedCount = dilutedCount,
            timestamp = timestamp
        )

        if (!dryRun) {
            StdoutFormatter.plan("Archivage horodate dans vision-archive/...")
            val archivePath = archiveReport(report)
            StdoutFormatter.result("Archive : $archivePath")
        } else {
            StdoutFormatter.ctx("DRY RUN — pas d'archivage effectif")
        }

        return report
    }

    private fun parseSections(text: String): List<ParsedSection> {
        val headingRegex = Regex("(?m)^== (.+)$")
        val matches = headingRegex.findAll(text).toList()

        if (matches.isEmpty()) {
            val cleanText = text.trim()
            val firstLine = cleanText.lines().firstOrNull()?.trim()?.removePrefix("= ") ?: "Brain dump"
            return listOf(ParsedSection("S1", firstLine, cleanText))
        }

        val sections = mutableListOf<ParsedSection>()
        for ((idx, match) in matches.withIndex()) {
            val heading = match.groupValues[1]
            val sectionStart = match.range.last + 1
            val sectionEnd = if (idx + 1 < matches.size) matches[idx + 1].range.first else text.length
            val content = text.substring(sectionStart, sectionEnd).trim()
            val id = "S${idx + 1}"
            sections.add(ParsedSection(id, heading, content))
        }
        return sections
    }

    private fun routeSection(section: ParsedSection): DilutionTarget {
        val model = OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(modelName)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        val userContent = """
Titre : ${section.title}
Contenu : ${section.content.take(2000)}
""".trimIndent()

        val messages = listOf(
            SystemMessage.from(routingSystemPrompt),
            UserMessage.from(userContent)
        )

        return try {
            val request = ChatRequest.builder().messages(messages).build()
            val response = model.chat(request)
            val raw = response.aiMessage().text().trim()
            parseRoutingResponse(section, raw)
        } catch (e: Exception) {
            log.error("Routing LLM call failed for section {}: {}", section.id, e.message)
            DilutionTarget(
                targetDocument = TargetDocument.WHAT_THE_GAMES_BEEN_MISSING,
                suggestedSection = "== Stimuli non routes",
                rationale = "Fallback: erreur LLM — ${e.message}"
            )
        }
    }

    private fun parseRoutingResponse(section: ParsedSection, raw: String): DilutionTarget {
        val json = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val targetDoc = when {
            json.contains("WORKSPACE_AS_PRODUCT") -> TargetDocument.WORKSPACE_AS_PRODUCT
            json.contains("WORKSPACE_ORGANIZATION") -> TargetDocument.WORKSPACE_ORGANIZATION
            json.contains("WORKSPACE_VISION") -> TargetDocument.WORKSPACE_VISION
            json.contains("WHAT_THE_GAMES_BEEN_MISSING") -> TargetDocument.WHAT_THE_GAMES_BEEN_MISSING
            else -> TargetDocument.WORKSPACE_VISION
        }

        val sectionMatch = Regex("\"suggestedSection\"\\s*:\\s*\"([^\"]+)\"").find(json)
        val suggestedSection = sectionMatch?.groupValues?.get(1) ?: "== Nouveau contenu (${section.id})"

        val rationaleMatch = Regex("\"rationale\"\\s*:\\s*\"([^\"]+)\"").find(json)
        val rationale = rationaleMatch?.groupValues?.get(1) ?: "routage automatique"

        return DilutionTarget(
            targetDocument = targetDoc,
            suggestedSection = suggestedSection,
            rationale = rationale
        )
    }

    private fun archiveReport(report: StimulusCascadeReport): String {
        val dirName = "${report.timestamp.format(tsFormat)}_stimulus-dilution"
        val archiveDir = File(workspaceDir, "$visionArchiveDir/$dirName")
        archiveDir.mkdirs()

        val brainDumpFile = File(archiveDir, "brain-dump-original.adoc")
        brainDumpFile.writeText(report.source)

        val classificationFile = File(archiveDir, "cascade-report.json")
        classificationFile.writeText(exportCascadeJson(report))

        val adocFile = File(archiveDir, "cascade-report.adoc")
        adocFile.writeText(exportCascadeAsciiDoc(report))

        log.info("Archive created: {}", archiveDir.absolutePath)
        return archiveDir.absolutePath
    }

    fun exportCascadeJson(report: StimulusCascadeReport): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"timestamp\": \"${report.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\",")
        sb.appendLine("  \"sourceHash\": \"${report.sourceHash}\",")
        sb.appendLine("  \"totalSections\": ${report.sections.size},")
        sb.appendLine("  \"visionCount\": ${report.visionCount},")
        sb.appendLine("  \"opinionCount\": ${report.opinionCount},")
        sb.appendLine("  \"dilutedCount\": ${report.dilutedCount},")
        sb.appendLine("  \"sections\": [")
        report.sections.forEachIndexed { idx, rec ->
            sb.appendLine("    {")
            sb.appendLine("      \"sectionId\": \"${rec.sectionId}\",")
            sb.appendLine("      \"sectionTitle\": \"${rec.sectionTitle}\",")
            sb.appendLine("      \"classification\": \"${rec.classification}\",")
            sb.appendLine("      \"confidence\": ${rec.confidence},")
            sb.appendLine("      \"classificationRationale\": \"${rec.classificationRationale}\",")
            if (rec.dilutionTarget != null) {
                sb.appendLine("      \"dilutionTarget\": {")
                sb.appendLine("        \"document\": \"${rec.dilutionTarget.targetDocument.path}\",")
                sb.appendLine("        \"section\": \"${rec.dilutionTarget.suggestedSection}\",")
                sb.appendLine("        \"rationale\": \"${rec.dilutionTarget.rationale}\"")
                sb.appendLine("      }")
            } else {
                sb.appendLine("      \"dilutionTarget\": null")
            }
            val comma = if (idx < report.sections.size - 1) "," else ""
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    fun exportCascadeAsciiDoc(report: StimulusCascadeReport): String {
        val sb = StringBuilder()
        sb.appendLine("= EPIC 10 — STIMULUS Cascade Report")
        sb.appendLine(":toc: left")
        sb.appendLine(":icons: font")
        sb.appendLine(":sectnums:")
        sb.appendLine()
        sb.appendLine("[abstract]")
        sb.appendLine("--")
        sb.appendLine("Pipeline STIMULUS : brain dump → classification VISION/OPINION → routing vers documents racine")
        sb.appendLine("→ archivage horodate. Source hash : `${report.sourceHash.take(12)}...`")
        sb.appendLine("--")
        sb.appendLine()

        sb.appendLine("== Synthese du Pipeline")
        sb.appendLine()
        sb.appendLine(".Metadonnees")
        sb.appendLine("[cols=\"2,3\"]")
        sb.appendLine("|===")
        sb.appendLine("| Timestamp | ${report.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        sb.appendLine("| Source hash | ${report.sourceHash.take(16)}")
        sb.appendLine("| Sections totales | ${report.sections.size}")
        sb.appendLine("| VISION (diluees) | ${report.visionCount}")
        sb.appendLine("| OPINION (confinees) | ${report.opinionCount}")
        sb.appendLine("|===")
        sb.appendLine()

        val targetDocs = report.sections
            .filter { it.dilutionTarget != null }
            .map { it.dilutionTarget!!.targetDocument }
            .distinct()
        if (targetDocs.isNotEmpty()) {
            sb.appendLine(".Documents cibles impactes")
            sb.appendLine("[cols=\"2,3\"]")
            sb.appendLine("|===")
            sb.appendLine("| Document | Sections")
            for (doc in targetDocs) {
                val count = report.sections.count { it.dilutionTarget?.targetDocument == doc }
                sb.appendLine("| ${doc.path} (${doc.label}) | $count")
            }
            sb.appendLine("|===")
            sb.appendLine()
        }

        sb.appendLine("== Classification par Section")
        sb.appendLine()
        sb.appendLine("[cols=\"1,3,1,1,2,3\"]")
        sb.appendLine("|===")
        sb.appendLine("| ID | Titre | Statut | Confiance | Document Cible (si VISION) | Justification")
        for (rec in report.sections) {
            val target = if (rec.dilutionTarget != null) "${rec.dilutionTarget.targetDocument.path} / ${rec.dilutionTarget.suggestedSection}" else "—"
            sb.appendLine("| ${rec.sectionId} | ${rec.sectionTitle} | ${rec.classification} | ${"%.2f".format(rec.confidence)} | $target | ${rec.classificationRationale}")
        }
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Decisions de Routage (VISION uniquement)")
        sb.appendLine()
        report.sections.filter { it.classification == ContentClassification.VISION && it.dilutionTarget != null }.forEachIndexed { idx, rec ->
            val t = rec.dilutionTarget!!
            sb.appendLine("=== ${rec.sectionId} — ${rec.sectionTitle}")
            sb.appendLine()
            sb.appendLine("[cols=\"2,3\"]")
            sb.appendLine("|===")
            sb.appendLine("| Document cible | ${t.targetDocument.path}")
            sb.appendLine("| Section suggeree | ${t.suggestedSection}")
            sb.appendLine("| Confiance classification | ${"%.2f".format(rec.confidence)}")
            sb.appendLine("| Rationale routage | ${t.rationale}")
            sb.appendLine("|===")
            sb.appendLine()
            sb.appendLine("[source,asciidoc]")
            sb.appendLine("----")
            sb.appendLine(rec.content.take(500))
            sb.appendLine("----")
            sb.appendLine()
        }

        sb.appendLine("== Sections Confinees (OPINION)")
        sb.appendLine()
        val opinions = report.sections.filter { it.classification == ContentClassification.OPINION }
        if (opinions.isEmpty()) {
            sb.appendLine("_Aucune section OPINION dans ce brain dump._")
        } else {
            for (op in opinions) {
                sb.appendLine("=== ${op.sectionId} — ${op.sectionTitle}")
                sb.appendLine()
                sb.appendLine("[quote]")
                sb.appendLine("____")
                sb.appendLine(op.content.take(400))
                sb.appendLine("____")
                sb.appendLine()
                sb.appendLine("Confiance classification : **${"%.2f".format(op.confidence)}** — _${op.classificationRationale}_")
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return BigInteger(1, digest).toString(16).padStart(64, '0')
    }
}
