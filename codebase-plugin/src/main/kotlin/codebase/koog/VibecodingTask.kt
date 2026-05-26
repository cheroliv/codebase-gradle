package codebase.koog

import codebase.koog.llm.LlmProviderResolver
import codebase.koog.session.SessionRepository
import codebase.koog.tracking.TokenTracker
import contracts.vibecoding.registry.ToolRegistry
import vibecoding.contracts.state.VibecodingState
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

/**
 * Tâche Gradle vibecoding — agent koog autonome.
 *
 * Pipeline : buildContext (RAG/LLM) → plan → exécution outils → audit JSONL.
 * Mode dryRun pour valider le pipeline sans effets de bord.
 *
 * Usage (dry-run) :
 * ```
 * ./gradlew vibecode --intention="Fix typo in README" --dryRun
 * ```
 *
 * Usage (réel avec LLM) :
 * ```
 * ./gradlew vibecode --intention="Refactor the DAG N1→N2→N3" --model=deepseek --maxActions=20
 * ```
 *
 * Usage (Gemini) :
 * ```
 * ./gradlew vibecode --intention="Génère un rapport" --model=gemini --maxActions=10
 * ```
 */
@DisableCachingByDefault(because = "Vibecoding LLM agent — non-deterministic LLM calls, non-cacheable")
abstract class VibecodingTask : DefaultTask() {

    private val log = LoggerFactory.getLogger(VibecodingTask::class.java)

    @get:Input
    @get:Optional
    @get:Option(option = "intention", description = "Intention de vibecoding — décrit le travail à effectuer")
    abstract val intention: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "dryRun", description = "Mode simulation — aucune action réelle")
    abstract val dryRun: Property<Boolean>

    @get:Input
    @get:Optional
    @get:Option(option = "maxActions", description = "Nombre maximum d'actions à exécuter")
    abstract val maxActions: Property<Int>

    @get:Input
    @get:Optional
    @get:Option(option = "model", description = "Modèle LLM (gemini, deepseek, ollama, gpt-oss:120b-cloud, etc.). Défaut: ollama (gpt-oss:120b-cloud)")
    abstract val model: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "sessionTimeout", description = "Timeout global de la session en secondes (défaut 300)")
    abstract val sessionTimeoutSeconds: Property<Int>

    @get:Input
    @get:Optional
    @get:Option(option = "resume", description = "ID de session à reprendre (--resume <sessionId>)")
    abstract val resume: Property<String>

    @get:Internal
    abstract val workspaceRoot: DirectoryProperty

    @get:Internal
    val toolRegistry: ToolRegistry = ToolRegistry()

    /**
     * ConnectionFactory injectable pour la persistance R2DBC.
     * Null par défaut — sans injection, --resume est inopérant.
     */
    var connectionFactory: ConnectionFactory? = null

    init {
        group = "generate"
        description = "Vibecoding agent — boucle autonome koog (contexte → plan → exécution → audit)"
        intention.convention("")
        model.convention("")
        dryRun.convention(false)
        maxActions.convention(10)
        sessionTimeoutSeconds.convention(300)
        resume.convention("")
    }

    @TaskAction
    fun executeVibecoding() {
        val root = workspaceRoot.asFile.getOrElse(project.rootDir)
        val auditedDryRun = dryRun.get()
        val auditDir = File(root, "build/vibecoding")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit.jsonl")

        toolRegistry.clearAudit()

        val tokenTracker = TokenTracker()
        val modelName = model.getOrElse("")
        val llmProvider = if (modelName.isBlank()) null
            else LlmProviderResolver.resolve(modelName)

        // --resume flow : reconstruit le state depuis la DB,
        // sinon crée un nouveau state à partir des options Gradle.
        val sessionId = resume.getOrElse("")
        // ConnectionFactory partagé entre path normal et --resume.
        // Permet la persistance automatique même sans --resume.
        val cf = connectionFactory

        val (graph, state) = if (sessionId.isNotBlank()) {
            val effectiveCf = cf
                ?: throw IllegalStateException("Cannot resume session $sessionId: no ConnectionFactory injected. " +
                    "Ensure R2DBC connection pool is available in your project.")
            val repo = SessionRepository(effectiveCf)
            runBlocking { repo.initSchema() }
            val record = runBlocking { repo.getSession(sessionId) }
                ?: throw IllegalStateException("Session not found: $sessionId")
            log.info("Resuming session {} (intention={}, iterationCount={}, finished={})",
                sessionId, record.intention, record.iterationCount, record.finished)
            val resumedState = VibecodingGraph.resumeSession(record)
            val resumedGraph = VibecodingGraph(
                augmentedGraph = KoogAugmentedContextGraph(),
                toolRegistry = toolRegistry,
                llmProvider = llmProvider,
                sessionRepository = repo,
                connectionFactory = effectiveCf,
                tokenTracker = tokenTracker
            )
            Pair(resumedGraph, resumedState)
        } else {
            val freshGraph = VibecodingGraph(
                augmentedGraph = KoogAugmentedContextGraph(),
                toolRegistry = toolRegistry,
                llmProvider = llmProvider,
                connectionFactory = cf,
                tokenTracker = tokenTracker
            )
            val freshState = VibecodingState(
                intention = intention.get(),
                workspaceRoot = root.absolutePath,
                dryRun = auditedDryRun,
                maxActions = maxActions.get(),
                sessionTimeoutSeconds = sessionTimeoutSeconds.get()
            )
            Pair(freshGraph, freshState)
        }

        val result = graph.execute(state)

        // Écriture de l'audit de session
        val errorValue = result.error?.let { "\"${jsonlEscape(it)}\"" } ?: "null"
        val sessionEntry = buildString {
            append("{\"timestamp\":\"${Instant.now()}\"")
            append(",\"iteration\":${result.iteration}")
            append(",\"intention\":\"${jsonlEscape(result.intention)}\"")
            append(",\"dryRun\":$auditedDryRun")
            append(",\"action\":\"session_complete\"")
            append(",\"classification\":\"${jsonlEscape(result.classification)}\"")
            append(",\"error\":$errorValue")
            append(",\"finished\":${result.finished}")
            append("}")
            appendLine()
        }
        auditFile.appendText(sessionEntry)

        // Écriture de l'audit des outils
        for (entry in toolRegistry.auditEntries()) {
            val escapedTool = jsonlEscape(entry.tool)
            val errorStr = entry.error?.let { "\"${jsonlEscape(it)}\"" } ?: "null"
            val toolEntry = buildString {
                append("{\"timestamp\":\"${entry.timestamp}\"")
                append(",\"tool\":\"$escapedTool\"")
                append(",\"dryRun\":${entry.dryRun}")
                append(",\"result\":\"${jsonlEscape(entry.result)}\"")
                append(",\"error\":$errorStr")
                append("}")
                appendLine()
            }
            auditFile.appendText(toolEntry)
        }

        if (result.error != null) {
            throw RuntimeException("Vibecoding failed: ${result.error}")
        }
    }

    private fun jsonlEscape(raw: String): String =
        raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
