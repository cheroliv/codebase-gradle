package codebase.rag

object PertinenceReportExporter {

    fun exportJson(report: PertinenceBenchmarkReport): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"executionTimestamp\": \"${report.executionTimestamp}\",")
        sb.appendLine("  \"modelName\": \"${report.modelName}\",")
        sb.appendLine("  \"totalQuestions\": ${report.totalQuestions},")
        sb.appendLine("  \"improvedCount\": ${report.improvedCount},")
        sb.appendLine("  \"degradedCount\": ${report.degradedCount},")
        sb.appendLine("  \"unchangedCount\": ${report.unchangedCount},")
        sb.appendLine("  \"improvementRate\": ${report.improvementRate},")
        sb.appendLine("  \"mvp0Validated\": ${report.mvp0Validated},")
        sb.appendLine("  \"pairs\": [")
        report.pairs.forEachIndexed { idx, (id, pair) ->
            sb.appendLine("    {")
            sb.appendLine("      \"id\": \"$id\",")
            sb.appendLine("      \"domain\": \"${PertinenceQuestions.all.first { it.id == id }.domain}\",")
            sb.appendLine("      \"question\": \"${pair.baseline.question.replace("\"", "\\\"")}\",")
            sb.appendLine("      \"baseline\": {")
            sb.appendLine("        \"keywordHits\": ${pair.baseline.keywordHits},")
            sb.appendLine("        \"totalKeywords\": ${pair.baseline.totalKeywords},")
            sb.appendLine("        \"answerLength\": ${pair.baseline.answerLength},")
            sb.appendLine("        \"isRelevant\": ${pair.baseline.isRelevant},")
            sb.appendLine("        \"answer\": \"${pair.baseline.answer.take(200).replace("\n", " ").replace("\"", "\\\"")}\"")
            sb.appendLine("      },")
            sb.appendLine("      \"augmented\": {")
            sb.appendLine("        \"keywordHits\": ${pair.augmented.keywordHits},")
            sb.appendLine("        \"totalKeywords\": ${pair.augmented.totalKeywords},")
            sb.appendLine("        \"answerLength\": ${pair.augmented.answerLength},")
            sb.appendLine("        \"isRelevant\": ${pair.augmented.isRelevant},")
            sb.appendLine("        \"answer\": \"${pair.augmented.answer.take(200).replace("\n", " ").replace("\"", "\\\"")}\"")
            sb.appendLine("      },")
            sb.appendLine("      \"deltaKeywords\": ${pair.deltaKeywords},")
            sb.appendLine("      \"deltaLength\": ${pair.deltaLength},")
            sb.appendLine("      \"improvement\": ${pair.improvement}")
            val comma = if (idx < report.pairs.size - 1) "," else ""
            sb.appendLine("    }$comma")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    fun exportAsciiDoc(report: PertinenceBenchmarkReport): String {
        val sb = StringBuilder()
        sb.appendLine("= EPIC 9 (US-9.13) — Pertinence Benchmark (gate MVP0)")
        sb.appendLine(":toc: left")
        sb.appendLine(":icons: font")
        sb.appendLine(":sectnums:")
        sb.appendLine(":report-date: ${report.executionTimestamp.take(19)}")
        sb.appendLine(":model: ${report.modelName}")
        sb.appendLine(":questions: ${report.totalQuestions}")
        sb.appendLine()
        sb.appendLine("[abstract]")
        sb.appendLine("--")
        sb.appendLine("Benchmark comparatif de pertinence des reponses LLM avec et sans le vecteur composite")
        sb.appendLine("de contexte (EAGER/LAZY + RAG pgvector + Graphify) injecte dans le prompt systeme opencode.")
        sb.appendLine("")
        sb.appendLine("Metrique : taux de mots-cles attendus presents dans la reponse + longueur de reponse.")
        sb.appendLine("Gate MVP0 : amelioration sur >70%% des 10 questions metier.")
        sb.appendLine("--")
        sb.appendLine()

        sb.appendLine("== Synthese Globale")
        sb.appendLine()
        sb.appendLine("[cols=\"2,1,2\"]")
        sb.appendLine("|===")
        sb.appendLine("| Modele | ${report.modelName}")
        sb.appendLine("| Total questions | ${report.totalQuestions}")
        sb.appendLine("| Ameliorees (avec contexte) | ${report.improvedCount} / ${report.totalQuestions}")
        sb.appendLine("| Degradees | ${report.degradedCount} / ${report.totalQuestions}")
        sb.appendLine("| Inchangees | ${report.unchangedCount} / ${report.totalQuestions}")
        val ratePct = "%.1f".format(report.improvementRate * 100)
        val mvp0Icon = if (report.mvp0Validated) "✅" else "❌"
        val mvp0Label = if (report.mvp0Validated) "VALIDE" else "NON ATTEINT"
        sb.appendLine("| Taux d'amelioration | *$ratePct%*")
        sb.appendLine("| MVP0 (>70%) | $mvp0Icon *$mvp0Label*")
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Questions Metier")
        sb.appendLine()
        val domains = PertinenceQuestions.all.map { it.domain }.distinct()
        sb.appendLine("[cols=\"1,2,4\"]")
        sb.appendLine("|===")
        sb.appendLine("| ID | Domaine | Question")
        for (q in PertinenceQuestions.all) {
            sb.appendLine("| ${q.id} | ${q.domain} | ${q.question}")
        }
        sb.appendLine("|===")
        sb.appendLine()

        sb.appendLine("== Resultats par Question")
        sb.appendLine()

        for ((id, pair) in report.pairs) {
            val q = PertinenceQuestions.all.first { it.id == id }
            val baseRelevant = if (pair.baseline.isRelevant) "[.text-success]#OUI#" else "[.text-danger]#NON#"
            val augRelevant = if (pair.augmented.isRelevant) "[.text-success]#OUI#" else "[.text-danger]#NON#"
            val deltaIcon = if (pair.improvement) "📈" else if (pair.deltaKeywords < 0) "📉" else "➡️"
            val deltaHits = if (pair.deltaKeywords > 0) "+${pair.deltaKeywords}" else "${pair.deltaKeywords}"
            val deltaLen = if (pair.deltaLength > 0) "+${pair.deltaLength}" else "${pair.deltaLength}"

            sb.appendLine("=== ${q.id} — ${q.domain}")
            sb.appendLine()
            sb.appendLine("_${q.question}_")
            sb.appendLine()
            sb.appendLine(".Comparaison Baseline vs Augmente")
            sb.appendLine("[cols=\"2,1,1,1,1\"]")
            sb.appendLine("|===")
            sb.appendLine("| Scenario | Pertinent | Mots-cles | Longueur | $deltaIcon Delta")
            sb.appendLine("| Baseline | $baseRelevant | ${pair.baseline.keywordHits}/${pair.baseline.totalKeywords} | ${pair.baseline.answerLength} chars | —")
            sb.appendLine("| Augmente | $augRelevant | ${pair.augmented.keywordHits}/${pair.augmented.totalKeywords} | ${pair.augmented.answerLength} chars | $deltaHits hits / $deltaLen chars")
            sb.appendLine("|===")
            sb.appendLine()

            sb.appendLine("==== Mots-cles attendus : ${q.expectedKeywords.joinToString(", ")}")
            sb.appendLine()
        }

        sb.appendLine("== Decision MVP0")
        sb.appendLine()
        if (report.mvp0Validated) {
            sb.appendLine("[TIP]")
            sb.appendLine("====")
            sb.appendLine("*MVP0 EPIC 9 VALIDE*. Le vecteur composite de contexte ameliore significativement")
            sb.appendLine("la qualite des reponses LLM sur ${ratePct}% des questions metier (seuil 70%).")
            sb.appendLine("")
            sb.appendLine("La gate est franchie. EPIC 9 est operationnel : le pipeline `augmentOpencode`")
            sb.appendLine("peut etre active en permanence dans les sessions opencode.")
            sb.appendLine("====")
        } else {
            sb.appendLine("[WARNING]")
            sb.appendLine("====")
            sb.appendLine("*MVP0 EPIC 9 NON ATTEINT*. Le taux d'amelioration est de ${ratePct}% (seuil 70%).")
            sb.appendLine("")
            sb.appendLine("Prochaines actions : reexaminer la qualite du contexte injecte, ajuster les mots-cles,")
            sb.appendLine("ou affiner le prompt systeme d'injection.")
            sb.appendLine("====")
        }

        return sb.toString()
    }
}
