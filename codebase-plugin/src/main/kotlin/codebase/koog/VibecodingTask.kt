package codebase.koog

import cccp.vibecoding.contracts.registry.ToolRegistry
import cccp.vibecoding.contracts.state.VibecodingState
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
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
 * Usage (réel) :
 * ```
 * ./gradlew vibecode --intention="Refactor the DAG N1→N2→N3" --maxActions=20
 * ```
 */
abstract class VibecodingTask : DefaultTask() {

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
    @get:Option(option = "sessionTimeout", description = "Timeout global de la session en secondes (défaut 300)")
    abstract val sessionTimeoutSeconds: Property<Int>

    @get:Internal
    abstract val workspaceRoot: DirectoryProperty

    @get:Internal
    val toolRegistry: ToolRegistry = ToolRegistry()

    init {
        group = "generate"
        description = "Vibecoding agent — boucle autonome koog (contexte → plan → exécution → audit)"
        intention.convention("")
        dryRun.convention(false)
        maxActions.convention(10)
        sessionTimeoutSeconds.convention(300)
    }

    @TaskAction
    fun executeVibecoding() {
        val root = workspaceRoot.asFile.getOrElse(project.rootDir)
        val auditedDryRun = dryRun.get()
        val auditDir = File(root, "build/vibecoding")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit.jsonl")

        toolRegistry.clearAudit()

        val graph = VibecodingGraph(
            augmentedGraph = KoogAugmentedContextGraph(),
            toolRegistry = toolRegistry
        )
        val state = VibecodingState(
            intention = intention.get(),
            workspaceRoot = root.absolutePath,
            dryRun = auditedDryRun,
            maxActions = maxActions.get(),
            sessionTimeoutSeconds = sessionTimeoutSeconds.get()
        )

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
