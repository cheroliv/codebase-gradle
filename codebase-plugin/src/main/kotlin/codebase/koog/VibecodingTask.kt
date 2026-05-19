package codebase.koog

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

abstract class VibecodingTask : DefaultTask() {

    @get:Input
    @get:Optional
    @get:Option(option = "intention", description = "Intention de vibecoding")
    abstract val intention: Property<String>

    @get:Input
    @get:Optional
    abstract val dryRun: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val maxActions: Property<Int>

    @get:Internal
    abstract val workspaceRoot: DirectoryProperty

    val toolRegistry: ToolRegistry = ToolRegistry()

    init {
        group = "generate"
        description = "Vibecoding agent — koog autonomous loop (context → plan → execute)"
        intention.convention("")
        dryRun.convention(false)
        maxActions.convention(10)
    }

    @TaskAction
    fun executeVibecoding() {
        val root = workspaceRoot.asFile.get()
        val auditedDryRun = dryRun.get()
        val auditDir = File(root, "build/vibecoding")
        auditDir.mkdirs()
        val auditFile = File(auditDir, "audit.jsonl")

        toolRegistry.clearAudit()

        val graph = VibecodingGraph()
        val state = VibecodingState(
            intention = intention.get(),
            workspaceRoot = root.absolutePath,
            dryRun = auditedDryRun,
            maxActions = maxActions.get()
        )

        val result = graph.execute(state)

        writeSessionAudit(auditFile, result)
        writeToolAudit(auditFile)
        if (result.error != null) {
            throw RuntimeException("Vibecoding failed: ${result.error}")
        }
    }

    private fun writeSessionAudit(file: File, state: VibecodingState) {
        val errorValue = if (state.error != null) "\"${state.error.replace("\"", "\\\"")}\"" else "null"
        val entry = """|{"timestamp":"${Instant.now()}","iteration":${state.iteration},"intention":"${state.intention.replace("\"", "\\\"")}","dryRun":${state.dryRun},"action":"session_complete","error":$errorValue,"finished":${state.finished}}
""".trimMargin()
        file.appendText(entry)
    }

    private fun writeToolAudit(file: File) {
        val entries = toolRegistry.auditEntries()
        for (entry in entries) {
            val escapedTool = entry.tool.replace("\"", "\\\"")
            val errorStr = entry.error?.replace("\"", "\\\"")?.let { "\"$it\"" } ?: "null"
            val jsonl = """|{"timestamp":"${entry.timestamp}","tool":"$escapedTool","dryRun":${entry.dryRun},"result":"${entry.result.replace("\"","\\\"")}","error":$errorStr}
""".trimMargin()
            file.appendText(jsonl)
        }
    }
}
