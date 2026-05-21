package codebase.koog

import codebase.koog.tools.ExecGradleTool
import codebase.koog.tools.ExecShellTool
import codebase.koog.tools.ToolkitIsMissingException
import java.io.File
import java.nio.file.Paths
import java.time.Instant

data class ToolInfo(
    val name: String,
    val description: String
)

data class AuditEntry(
    val timestamp: Instant,
    val tool: String,
    val arguments: Map<String, String>,
    val dryRun: Boolean,
    val result: String,
    val error: String? = null,
    val workspaceRoot: String = ""
)

class ToolRegistry(
    private val tools: MutableMap<String, ToolInfo> = mutableMapOf(),
    private val auditTrail: MutableList<AuditEntry> = mutableListOf()
) {
    companion object {
        const val MAX_READ_FILE_SIZE: Long = 10 * 1024 * 1024 // 10 MB
        const val MAX_OUTPUT_CHARS: Int = 8000
    }
    init {
        register(ToolInfo("read_file", "Read the contents of a file at the given path"))
        register(ToolInfo("write_file", "Write content to a file at the given path"))
        register(ToolInfo("edit_file", "Edit a file by replacing oldString with newString at the given path"))
        register(ToolInfo("list_directory", "List the contents of a directory at the given path"))
        register(ToolInfo("exit", "Exit the vibecoding loop"))
        register(ToolInfo("exec_shell", "Execute a shell command via bash -c. Returns EXIT code + stdout (max 8000 chars). DANGEROUS commands blocked."))
        register(ToolInfo("exec_gradle", "Execute a Gradle task via ./gradlew. Returns EXIT code + stdout (max 8000 chars)."))
    }

    fun register(tool: ToolInfo) {
        tools[tool.name] = tool
    }

    fun get(name: String): ToolInfo =
        tools[name] ?: throw ToolkitIsMissingException(name)

    fun toolCount(): Int = tools.size

    fun toolNames(): List<String> = tools.keys.toList()

    fun auditEntries(): List<AuditEntry> = auditTrail.toList()

    fun clearAudit() {
        auditTrail.clear()
    }

    fun execute(toolName: String, arguments: Map<String, String>, workspaceRoot: String, dryRun: Boolean = false): String {
        val start = Instant.now()
        val result = try {
            executeInternal(toolName, arguments, workspaceRoot, dryRun)
        } catch (e: SecurityException) {
            auditTrail.add(AuditEntry(start, toolName, arguments, dryRun, result = "", error = e.message, workspaceRoot = workspaceRoot))
            throw e
        } catch (e: Exception) {
            auditTrail.add(AuditEntry(start, toolName, arguments, dryRun, result = "", error = e.message, workspaceRoot = workspaceRoot))
            throw e
        }
        auditTrail.add(AuditEntry(start, toolName, arguments, dryRun, result = result.take(500), workspaceRoot = workspaceRoot))
        return result
    }

    private fun executeInternal(toolName: String, arguments: Map<String, String>, workspaceRoot: String, dryRun: Boolean): String {
        return when (toolName) {
            "read_file" -> {
                val path = arguments["path"] ?: throw IllegalArgumentException("read_file requires 'path'")
                val file = resolvePath(path, workspaceRoot)
                if (!file.exists()) throw IllegalStateException("File not found: ${file.absolutePath}")
                val maxSize = MAX_READ_FILE_SIZE
                if (file.length() > maxSize) {
                    throw SecurityException(
                        "File too large: ${file.length()} bytes exceeds 10 MB limit (${file.absolutePath})"
                    )
                }
                file.readText().take(MAX_OUTPUT_CHARS)
            }
            "write_file" -> {
                val path = arguments["path"] ?: throw IllegalArgumentException("write_file requires 'path'")
                val content = arguments["content"] ?: throw IllegalArgumentException("write_file requires 'content'")
                val file = resolvePath(path, workspaceRoot)
                if (dryRun) return "DRY RUN: would write ${content.length} chars to ${file.absolutePath}"
                file.parentFile.mkdirs()
                file.writeText(content)
                "File written: ${file.absolutePath}"
            }
            "edit_file" -> {
                val path = arguments["path"] ?: throw IllegalArgumentException("edit_file requires 'path'")
                val oldString = arguments["oldString"] ?: throw IllegalArgumentException("edit_file requires 'oldString'")
                val newString = arguments["newString"] ?: throw IllegalArgumentException("edit_file requires 'newString'")
                val file = resolvePath(path, workspaceRoot)
                if (!file.exists()) throw IllegalStateException("File not found: ${file.absolutePath}")
                if (dryRun) return "DRY RUN: would replace in ${file.absolutePath} (${oldString.length}→${newString.length} chars)"
                val original = file.readText()
                if (!original.contains(oldString)) throw IllegalStateException("oldString not found in file: ${file.absolutePath}")
                val updated = original.replace(oldString, newString)
                file.writeText(updated)
                "File edited: ${file.absolutePath}"
            }
            "list_directory" -> {
                val path = arguments["path"] ?: "."
                val dir = resolvePath(path, workspaceRoot)
                if (!dir.isDirectory) throw IllegalStateException("Not a directory: ${dir.absolutePath}")
                dir.listFiles()?.joinToString("\n") { "${if (it.isDirectory) "d" else "f"} ${it.name}" } ?: ""
            }
            "exit" -> "Vibecoding loop terminated"
            "exec_shell" -> {
                val command = arguments["command"] ?: throw IllegalArgumentException("exec_shell requires 'command'")
                if (dryRun) return "DRY RUN: would execute shell command: $command"
                ExecShellTool.executeBlocking(command, workspaceRoot)
            }
            "exec_gradle" -> {
                val task = arguments["task"] ?: throw IllegalArgumentException("exec_gradle requires 'task'")
                if (dryRun) return "DRY RUN: would execute gradle task: $task"
                ExecGradleTool.executeBlocking(task, workspaceRoot)
            }
            else -> throw ToolkitIsMissingException(toolName)
        }
    }

    private fun resolvePath(path: String, workspaceRoot: String): File {
        val rootPath = Paths.get(workspaceRoot).normalize().toAbsolutePath()
        val resolvedPath = rootPath.resolve(path).normalize().toAbsolutePath()
        if (!resolvedPath.startsWith(rootPath)) {
            throw SecurityException("Path traversal blocked: '$path' resolved to '$resolvedPath' outside workspaceRoot '$rootPath'")
        }
        return resolvedPath.toFile()
    }
}
