package codebase.quality

class DeterministicOffTopicDetector {

    fun check(output: String, domain: Domain, config: QualityGateConfig): QualityCheckResult {
        if (output.isBlank()) {
            return QualityCheckResult(
                checkerName = "off-topic",
                verdict = QualityVerdict.PASS,
                score = 1.0,
                details = "off-topic check skipped (empty output)"
            )
        }

        val lower = output.lowercase()
        val domainKeywords = keywordsFor(domain)
        val oppositeKeywords = keywordsFor(domain.opposite())
        val offTopicKeywords = offTopicKeywords

        val domainScore = domainKeywords.sumOf { kw ->
            kw.toRegex().findAll(lower).count()
        }
        val oppositeScore = oppositeKeywords.sumOf { kw ->
            kw.toRegex().findAll(lower).count()
        }
        val offTopicScore = offTopicKeywords.sumOf { kw ->
            kw.toRegex().findAll(lower).count()
        }

        val totalHits = (domainScore + oppositeScore + offTopicScore).coerceAtLeast(1)
        val score = (domainScore.toDouble() / totalHits).coerceIn(0.0, 1.0)

        val (verdict, details) = when {
            score >= config.minAcceptableScore -> QualityVerdict.PASS to "on-topic for ${domain.name} (${"%.2f".format(score)})"
            score >= config.minAcceptableScore * 0.5 -> QualityVerdict.NEEDS_FIX to "potentially off-topic for ${domain.name} (${"%.2f".format(score)})"
            else -> QualityVerdict.FAIL to "off-topic for ${domain.name} (${"%.2f".format(score)})"
        }

        return QualityCheckResult(
            checkerName = "off-topic",
            verdict = verdict,
            score = score,
            details = details
        )
    }

    private fun keywordsFor(domain: Domain): List<String> = when (domain) {
        Domain.CDA -> listOf(
            "class ", "fun ", "val ", "var ", "interface", "object", "enum class",
            "kotlin", "gradle", "spring", "jhipster", "docker", "postgresql", "pgvector",
            "buildscript", "dependencies", "plugin", "implementation", "testimplementation",
            "restcontroller", "service", "repository", "entity", "data class",
            "application", "autowired", "configuration", "bean", "component",
            "langchain4j", "ollama", "onnx", "embedding", "vectorstore",
            "github actions", "ci/cd", "test", "mock", "junit", "mockk"
        )
        Domain.FPA -> listOf(
            "formation", "pédagogie", "formateur", "apprenant", "stagiaire",
            "compétence", "évaluation", "objectif", "session", "module",
            "qualiopi", "rncp", "afnor", "bloom", "harrow", "krathwohl",
            "taxonomie", "référentiel", "certification", "validation",
            "remédiation", "accompagnement", "tutorat", "ingénierie",
            "scénario", "séquence", "spg", "spd", "livret",
            "professionnel", "fpa", "cda", "référentiel métier",
            "fiche", "épreuve", "jury", "vae", "acquis"
        )
    }

    private fun Domain.opposite(): Domain = when (this) {
        Domain.CDA -> Domain.FPA
        Domain.FPA -> Domain.CDA
    }

    companion object {
        private val offTopicKeywords = listOf(
            "recette", "cuisine", "gâteau", "chocolat", "farine",
            "football", "tennis", "natation", "sport", "match",
            "jardinage", "météo", "climat", "vacances"
        )
    }
}
