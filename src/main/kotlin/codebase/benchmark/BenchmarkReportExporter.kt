package codebase.benchmark

import java.time.Instant

object BenchmarkReportExporter {

    data class Crossing(val documentId: String, val expectedCircle: Int, val actualCircle: Int, val confidenceScore: Double)
    data class ThresholdResult(val threshold: String, val totalSamples: Int, val errorRate: Double, val crossings: List<Crossing>)

    fun exportAsciiDoc(jsonReport: String, scenarioId: String): String {
        val root = parseObject(jsonReport.trim())

        val scenarioName = root["scenario"] as? String ?: scenarioId
        val channels = (root["channels"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val summary = root["summary"] as? String ?: ""
        val rawResults = root["results"] as? List<*> ?: emptyList<Nothing>()

        val results = rawResults.mapNotNull { elem ->
            val obj = elem as? Map<*, *> ?: return@mapNotNull null
            val threshold = obj["threshold"] as? String ?: "?"
            val totalSamples = (obj["totalSamples"] as? Number)?.toInt() ?: 0
            val errorRate = (obj["errorRate"] as? Number)?.toDouble() ?: 0.0
            val rawCrossings = obj["boundaryCrossings"] as? List<*> ?: emptyList<Nothing>()
            val crossings = rawCrossings.mapNotNull { c ->
                val co = c as? Map<*, *> ?: return@mapNotNull null
                Crossing(
                    documentId = co["documentId"] as? String ?: "?",
                    expectedCircle = (co["expectedCircle"] as? Number)?.toInt() ?: 0,
                    actualCircle = (co["actualCircle"] as? Number)?.toInt() ?: 0,
                    confidenceScore = (co["confidenceScore"] as? Number)?.toDouble() ?: 0.0
                )
            }
            ThresholdResult(threshold, totalSamples, errorRate, crossings)
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
        sb.appendLine(summary)
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

    // ── Minimal JSON parser (no external dependencies) ──

    private fun parseObject(json: String): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        var pos = 1
        while (pos < json.length && json[pos] != '}') {
            pos = skipWhitespace(json, pos)
            if (pos >= json.length || json[pos] == '}') break
            val key = parseString(json, pos)
            pos = skipWhitespace(json, key.second)
            check(json[pos] == ':') { "Expected ':' at $pos" }
            pos = skipWhitespace(json, pos + 1)
            val value = parseValue(json, pos)
            map[key.first] = value.first
            pos = skipWhitespace(json, value.second)
            if (pos < json.length && json[pos] == ',') pos++
        }
        return map
    }

    private fun parseString(json: String, start: Int): Pair<String, Int> {
        check(json[start] == '"') { "Expected '\"' at $start" }
        val sb = StringBuilder()
        var pos = start + 1
        while (pos < json.length && json[pos] != '"') {
            if (json[pos] == '\\') {
                pos++
                if (pos < json.length) {
                    when (json[pos]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        else -> sb.append(json[pos])
                    }
                    pos++
                }
            } else {
                sb.append(json[pos])
                pos++
            }
        }
        check(pos < json.length && json[pos] == '"') { "Unterminated string at $pos" }
        return Pair(sb.toString(), pos + 1)
    }

    private fun parseValue(json: String, start: Int): Pair<Any, Int> {
        val pos = skipWhitespace(json, start)
        return when {
            json[pos] == '"' -> parseString(json, pos)
            json[pos] == '{' -> {
                var depth = 1
                var end = pos + 1
                while (end < json.length && depth > 0) {
                    when (json[end]) {
                        '{' -> depth++
                        '}' -> depth--
                    }
                    end++
                }
                val inner = json.substring(pos, end)
                Pair(parseObject(inner), end)
            }
            json[pos] == '[' -> {
                val list = mutableListOf<Any>()
                var end = pos + 1
                end = skipWhitespace(json, end)
                if (json[end] == ']') {
                    Pair(list, end + 1)
                } else {
                    while (end < json.length && json[end] != ']') {
                        val elem = parseValue(json, end)
                        list.add(elem.first)
                        end = skipWhitespace(json, elem.second)
                        if (end < json.length && json[end] == ',') end = skipWhitespace(json, end + 1)
                    }
                    Pair(list, end + 1)
                }
            }
            else -> {
                val sb = StringBuilder()
                var end = pos
                while (end < json.length && json[end] !in ",}] \t\n\r") {
                    sb.append(json[end])
                    end++
                }
                val token = sb.toString()
                when {
                    token == "null" -> Pair("", end)
                    token == "true" -> Pair(true, end)
                    token == "false" -> Pair(false, end)
                    token.contains('.') || token.contains('E') || token.contains('e') -> Pair(token.toDouble(), end)
                    token.isEmpty() -> Pair(0, end)
                    else -> Pair(token.toIntOrNull() ?: token, end)
                }
            }
        }
    }

    private fun skipWhitespace(json: String, start: Int): Int {
        var pos = start
        while (pos < json.length && json[pos] in " \t\n\r") pos++
        return pos
    }
}
