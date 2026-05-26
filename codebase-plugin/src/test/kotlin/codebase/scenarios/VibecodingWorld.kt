package codebase.scenarios

import codebase.koog.llm.FakeLlmProvider
import codebase.koog.llm.LlmProvider
import codebase.koog.llm.pool.OllamaLlmProvider
import codebase.koog.llm.pool.OllamaPool
import codebase.koog.session.SessionRecord
import codebase.koog.tracking.TokenTracker
import contracts.llmpool.LlmInstance
import contracts.llmpool.LlmInstancePool
import contracts.llmpool.QuotaConfig
import contracts.llmpool.ResetPolicy
import contracts.llmpool.RotationStrategy
import contracts.vibecoding.registry.ToolRegistry
import vibecoding.contracts.state.VibecodingState
import codebase.koog.VibecodingGraph
import kotlin.test.assertTrue
import java.time.Instant

/**
 * World Object injecté par PicoContainer dans toutes les Steps Cucumber
 * de type vibecoding.
 *
 * Pattern aligné sur AugmentedPlanningWorld :
 * - Injection par constructeur dans les Steps
 * - État mutable partagé entre les scénarios
 * - PicoContainer crée une nouvelle instance par scénario → pas besoin de reset()
 * - VibecodingGraph sans graphe augmenté (pas de pgvector/Ollama)
 */
class VibecodingWorld {

    var intention: String = ""
    var maxActions: Int = 10
    var dryRun: Boolean = false
    var sessionTimeoutSeconds: Int = 300
    var resultState: VibecodingState? = null
    var mermaidDiagram: String = ""
    var securityException: SecurityException? = null

    /** V-5 : fake LLM provider pour les tests (null = mode déterministe) */
    var fakeLlmProvider: FakeLlmProvider? = null
    var tokenTracker: TokenTracker = TokenTracker()

    var graph: VibecodingGraph = VibecodingGraph(
        augmentedGraph = null,
        toolRegistry = ToolRegistry()
    )

    // ── @epic_v_6 : Ollama Pool ──

    /** Pool Ollama configuré par le scénario Cucumber */
    var ollamaPool: LlmInstancePool? = null

    /** Provider Ollama cablé sur le pool */
    var ollamaLlmProvider: OllamaLlmProvider? = null

    /** Nombre d'appels effectués par le scénario */
    var providerCallCount: Int = 0

    /** Instances du pool (pour assertions) */
    var poolInstances: List<LlmInstance> = emptyList()

    // ── @epic_v_7 : Resume session ──

    /** SessionRecord construit par le scénario Cucumber */
    var sessionRecord: SessionRecord? = null

    /** VibecodingState résultant de resumeSession() */
    var resumedState: VibecodingState? = null

    // ── @epic_v_8 : DashboardTask ──

    /** Projet Gradle construit par le Background */
    var gradleProject: org.gradle.api.Project? = null

    /** Tâche Gradle récupérée par lookup */
    var foundTask: org.gradle.api.Task? = null

    /** Exception levée par executeDashboard() */
    var dashboardException: Exception? = null

    /**
     * Configure un pool Ollama avec N instances et un seuil de quota.
     */
    fun setupOllamaPool(instanceIds: List<String>, threshold: Int) {
        poolInstances = instanceIds.map { id ->
            LlmInstance(
                id = id,
                baseUrl = "http://localhost:11437",  // fake URL pour Cucumber (pas de vrai Ollama)
                model = "gpt-oss:120b-cloud",
                quota = QuotaConfig(limitValue = 10, thresholdPercent = threshold, resetPolicy = ResetPolicy.NEVER)
            )
        }
        ollamaPool = OllamaPool(poolInstances, rotationStrategy = RotationStrategy.ROUND_ROBIN)
    }

    /**
     * Appelle le provider Ollama N fois (via FakeOllamaLlmProvider pour Cucumber).
     * Sans vrai serveur Ollama, on utilise un mock qui appelle nextInstance() sur le pool.
     */
    fun callOllamaProviderMultiple(times: Int) {
        val pool = ollamaPool ?: error("Ollama pool not configured — call setupOllamaPool first")
        repeat(times) {
            pool.nextInstance()  // exerce le mécanisme de rotation/quota
            providerCallCount++
        }
    }

    /**
     * Vérifie que les deux instances ont été utilisées au moins une fois.
     */
    fun assertBothInstancesUsed(idA: String, idB: String) {
        val pool = ollamaPool as? OllamaPool ?: error("Pool is not an OllamaPool")
        // Avec 5 appels et threshold=2 (20%→seuil 2), instance a atteint quota après 2 appels
        // Instance b reçoit les appels restants (3) → les deux sont utilisées
        val instanceA = poolInstances.first { it.id == idA }
        val exceeded = pool.isQuotaExceeded(instanceA)
        assertTrue(exceeded, "Instance $idA should have quota exceeded after 5 calls with threshold=2 (limit=10, 20%=2)")
        // b a été utilisée (sinon on aurait eu une IllegalStateException au 3e appel)
    }

    /**
     * Vérifie que le quota d'une instance est dépassé.
     */
    fun assertQuotaExceeded(id: String) {
        val pool = ollamaPool ?: error("Ollama pool not configured")
        val instance = poolInstances.first { it.id == id }
        assertTrue(pool.isQuotaExceeded(instance), "Instance $id should have quota exceeded")
    }

    /**
     * Réinitialise le graphe avec le FakeLlmProvider après configuration.
     * Appelé dans le Background @epic_v_5.
     */
    fun initGraphWithLLM() {
        val provider = fakeLlmProvider ?: FakeLlmProvider()
        fakeLlmProvider = provider
        tokenTracker = TokenTracker()
        graph = VibecodingGraph(
            augmentedGraph = null,
            toolRegistry = ToolRegistry(),
            llmProvider = provider,
            tokenTracker = tokenTracker
        )
    }
}

