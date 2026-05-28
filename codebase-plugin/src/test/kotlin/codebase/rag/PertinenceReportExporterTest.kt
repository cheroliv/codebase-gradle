package codebase.rag

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PertinenceReportExporterTest {

    @Test
    fun `exportJson produces valid JSON with report metadata`() {
        val report = PertinenceBenchmarkReport(
            executionTimestamp = "2026-05-18T19:00:00Z",
            modelName = "deepseek-v4-pro:cloud",
            totalQuestions = 2,
            improvedCount = 2,
            degradedCount = 0,
            unchangedCount = 0,
            improvementRate = 1.0,
            pairs = listOf(
                PertinenceQuestions.all[0].id to PertinencePair(
                    baseline = PertinenceAnswer(
                        question = PertinenceQuestions.all[0].question,
                        answer = "Des regles s'appliquent.",
                        keywordHits = 2,
                        totalKeywords = PertinenceQuestions.all[0].expectedKeywords.size,
                        answerLength = 24,
                        isRelevant = false
                    ),
                    augmented = PertinenceAnswer(
                        question = PertinenceQuestions.all[0].question,
                        answer = "Les regles incluent l'interdiction de commit sans permission explicite.",
                        keywordHits = 4,
                        totalKeywords = PertinenceQuestions.all[0].expectedKeywords.size,
                        answerLength = 58,
                        isRelevant = true
                    ),
                    deltaKeywords = 2,
                    deltaLength = 34,
                    improvement = true
                ),
                PertinenceQuestions.all[1].id to PertinencePair(
                    baseline = PertinenceAnswer(
                        question = PertinenceQuestions.all[1].question,
                        answer = "Architecture DAG.",
                        keywordHits = 1,
                        totalKeywords = PertinenceQuestions.all[1].expectedKeywords.size,
                        answerLength = 33,
                        isRelevant = false
                    ),
                    augmented = PertinenceAnswer(
                        question = PertinenceQuestions.all[1].question,
                        answer = "Le DAG N0->N1->N2->N3 avec couplage faible via pgvector.",
                        keywordHits = 3,
                        totalKeywords = PertinenceQuestions.all[1].expectedKeywords.size,
                        answerLength = 60,
                        isRelevant = true
                    ),
                    deltaKeywords = 2,
                    deltaLength = 27,
                    improvement = true
                )
            ),
            mvp0Validated = true
        )

        val json = PertinenceReportExporter.exportJson(report)

        assertTrue(json.contains("\"executionTimestamp\": \"2026-05-18T19:00:00Z\""))
        assertTrue(json.contains("\"modelName\": \"deepseek-v4-pro:cloud\""))
        assertTrue(json.contains("\"totalQuestions\": 2"))
        assertTrue(json.contains("\"improvedCount\": 2"))
        assertTrue(json.contains("\"improvementRate\": 1.0"))
        assertTrue(json.contains("\"mvp0Validated\": true"))
        assertTrue(json.contains("\"pairs\": ["))
        assertTrue(json.contains("\"id\": \"${PertinenceQuestions.all[0].id}\""))
        assertTrue(json.contains("\"id\": \"${PertinenceQuestions.all[1].id}\""))
    }

    @Test
    fun `exportAsciiDoc produces valid AsciiDoc with synthesis table`() {
        val report = PertinenceBenchmarkReport(
            executionTimestamp = "2026-05-18T19:00:00Z",
            modelName = "deepseek-v4-pro:cloud",
            totalQuestions = 1,
            improvedCount = 1,
            degradedCount = 0,
            unchangedCount = 0,
            improvementRate = 1.0,
            pairs = listOf(
                "Q1-gouvernance" to PertinencePair(
                    baseline = PertinenceAnswer(
                        question = PertinenceQuestions.all[0].question,
                        answer = "Des regles s'appliquent.",
                        keywordHits = 1,
                        totalKeywords = PertinenceQuestions.all[0].expectedKeywords.size,
                        answerLength = 25,
                        isRelevant = false
                    ),
                    augmented = PertinenceAnswer(
                        question = PertinenceQuestions.all[0].question,
                        answer = "Les regles de gouvernance incluent l'interdiction de commit sans permission.",
                        keywordHits = 3,
                        totalKeywords = PertinenceQuestions.all[0].expectedKeywords.size,
                        answerLength = 80,
                        isRelevant = true
                    ),
                    deltaKeywords = 2,
                    deltaLength = 55,
                    improvement = true
                )
            ),
            mvp0Validated = true
        )

        val adoc = PertinenceReportExporter.exportAsciiDoc(report)

        assertTrue(adoc.contains("= EPIC 9 (US-9.13) — Pertinence Benchmark (gate MVP0)"))
        assertTrue(adoc.contains(":toc: left"))
        assertTrue(adoc.contains(":report-date: 2026-05-18T19:00:00"))
        assertTrue(adoc.contains(":model: deepseek-v4-pro:cloud"))
        assertTrue(adoc.contains("== Synthese Globale"))
        assertTrue(adoc.contains("| Modele | deepseek-v4-pro:cloud"))
        assertTrue(adoc.contains("| Ameliorees (avec contexte) | 1 / 1"))
        assertTrue(adoc.contains("Taux d'amelioration"))
        assertTrue(adoc.contains("100"))
        assertTrue(adoc.contains("*MVP0 EPIC 9 VALIDE*"))
        assertTrue(adoc.contains("== Resultats par Question"))
        assertTrue(adoc.contains("=== Q1-gouvernance — Gouvernance"))
        assertTrue(adoc.contains("| Augmente | [.text-success]#OUI#"))
    }

    @Test
    fun `exportAsciiDoc shows non-validated warning when improvement below threshold`() {
        val report = PertinenceBenchmarkReport(
            executionTimestamp = "2026-05-18T19:00:00Z",
            modelName = "deepseek-v4-pro:cloud",
            totalQuestions = 1,
            improvedCount = 0,
            degradedCount = 1,
            unchangedCount = 0,
            improvementRate = 0.0,
            pairs = emptyList(),
            mvp0Validated = false
        )

        val adoc = PertinenceReportExporter.exportAsciiDoc(report)

        assertTrue(adoc.contains("*MVP0 EPIC 9 NON ATTEINT*"))
        assertTrue(adoc.contains("| Degradees | 1 / 1"))
        assertTrue(adoc.contains("Taux d'amelioration"))
    }

    @Test
    fun `exportJson handles empty pairs correctly`() {
        val report = PertinenceBenchmarkReport(
            executionTimestamp = "2026-05-18T19:00:00Z",
            modelName = "test-model",
            totalQuestions = 0,
            improvedCount = 0,
            degradedCount = 0,
            unchangedCount = 0,
            improvementRate = 0.0,
            pairs = emptyList(),
            mvp0Validated = false
        )

        val json = PertinenceReportExporter.exportJson(report)

        assertTrue(json.contains("\"pairs\": ["))
        assertTrue(json.contains("]"))
        assertTrue(!json.contains("\"id\""))
    }
}
