package benchmark

// ════════════════════════════════════════════════════════════════════════════
// EPIC 4 — Benchmark de perception spatiale LLM
// ── Protocole de mesure standardisé + data classes ──
// ════════════════════════════════════════════════════════════════════════════

/**
 * Seuil de tokens à tester.
 *
 * - [size]  → taille en tokens
 * - [label] → étiquette humainement lisible (ex: "10K")
 */
data class TokenThreshold(
    val size: Int = 0,
    val label: String = ""
)

/**
 * Un scénario d'exécution du benchmark. Chaque scénario active un
 * sous-ensemble différent des canaux convergents pour mesurer leur
 * impact incrémental sur la perception spatiale du LLM.
 *
 * - [id]          → identifiant unique (ex: "BASELINE", "RAG_ONLY")
 * - [description] → texte descriptif du scénario
 * - [channels]    → canaux activés dans ce scénario
 */
data class BenchmarkScenario(
    val id: String = "",
    val description: String = "",
    val channels: List<String> = emptyList()
)

/**
 * Événement de franchissement de cercle — se produit quand le LLM
 * prédit un cercle de confiance différent du cercle attendu pour un
 * document donné.
 *
 * - [documentId]      → identifiant du document testé
 * - [expectedCircle]  → cercle de confiance attendu (C0 à C4)
 * - [actualCircle]    → cercle prédit par le LLM
 * - [documentExcerpt] → extrait textuel du document (~200 tokens)
 * - [confidenceScore] → score de confiance auto-déclaré par le LLM (0.0→1.0)
 */
data class BoundaryCrossingEvent(
    val documentId: String = "",
    val expectedCircle: Int = 0,
    val actualCircle: Int = 0,
    val documentExcerpt: String = "",
    val confidenceScore: Double = 0.0
)

/**
 * Résultat de perception spatiale pour un seuil de tokens donné.
 * Agrège tous les franchissements de cercle observés à ce seuil.
 *
 * - [threshold]      → seuil de tokens testé
 * - [crossingEvents] → franchissements détectés
 * - [totalSamples]   → nombre total d'échantillons
 * - [errorRate]      → crossings / totalSamples
 * - [timestamp]      → horodatage ISO-8601 de l'exécution
 */
data class SpatialPerceptionResult(
    val threshold: TokenThreshold = TokenThreshold(),
    val crossingEvents: List<BoundaryCrossingEvent> = emptyList(),
    val totalSamples: Int = 0,
    val errorRate: Double = 0.0,
    val timestamp: String = ""
)

/**
 * Rapport global d'un scénario de benchmark.
 *
 * - [scenario] → scénario exécuté
 * - [results]  → résultats par seuil de tokens
 * - [summary]  → synthèse textuelle
 */
data class BenchmarkReport(
    val scenario: BenchmarkScenario = BenchmarkScenario(),
    val results: List<SpatialPerceptionResult> = emptyList(),
    val summary: String = ""
)

/**
 * Configuration du benchmark.
 *
 * - [thresholds] → seuils de tokens à tester
 * - [scenarios]  → scénarios à exécuter
 * - [outputDir]  → dossier de sortie des rapports
 */
data class BenchmarkConfig(
    val thresholds: List<TokenThreshold> = listOf(
        TokenThreshold(10000, "10K"),
        TokenThreshold(30000, "30K"),
        TokenThreshold(60000, "60K"),
        TokenThreshold(100000, "100K"),
        TokenThreshold(128000, "128K")
    ),
    val scenarios: List<BenchmarkScenario> = listOf(
        BenchmarkScenario(
            "BASELINE",
            "Sans RAG ni Graphify — baseline brute",
            emptyList()
        ),
        BenchmarkScenario(
            "RAG_ONLY",
            "Avec RAG pgvector seul (office/ + codebase)",
            listOf("RAG")
        ),
        BenchmarkScenario(
            "RAG_GRAPHIFY_LOCAL",
            "Avec RAG + Graphify scope local projet",
            listOf("RAG", "Graphify")
        ),
        BenchmarkScenario(
            "RAG_GRAPHIFY_WORKSPACE",
            "Avec RAG + Graphify scope workspace complet",
            listOf("RAG", "Graphify")
        ),
        BenchmarkScenario(
            "FOUR_CHANNELS",
            "4 canaux convergents : EAGER/LAZY + RAG + Graphify + Ressources",
            listOf("EAGER/LAZY", "RAG", "Graphify", "Ressources")
        )
    ),
    val outputDir: String = "benchmark-output"
)

/**
 * Protocole de mesure standardisé pour EPIC 4.
 *
 * Définit :
 * - Les 5 cercles de confiance du workspace
 * - La grille des seuils de tokens (10K → 128K)
 * - Les 5 scénarios à couverture incrémentale
 * - La métrique principale : taux d'erreur de classification par cercle
 *
 * Objectif : déterminer le seuil de tokens à partir duquel deeepseek-v4-pro
 * perd la notion de cercle de confiance, et mesurer l'apport de chaque canal
 * convergent sur la résilience du contexte long.
 */
object BenchmarkProtocol {

    val CIRCLES = listOf(0, 1, 2, 3, 4)

    val CIRCLE_LABELS = mapOf(
        0 to "C0 — Racine workspace (hors-CVS)",
        1 to "C1 — configuration/ (secrets, non versionné)",
        2 to "C2 — office/ (données privées, cercle 2)",
        3 to "C3 — foundry/private/ (licence propriétaire)",
        4 to "C4 — foundry/public/ (Apache 2.0, public)"
    )

    fun defaultConfig(): BenchmarkConfig = BenchmarkConfig()

    fun computeErrorRate(crossings: List<BoundaryCrossingEvent>, totalSamples: Int): Double {
        if (totalSamples <= 0) return 0.0
        return crossings.size.toDouble() / totalSamples
    }
}
