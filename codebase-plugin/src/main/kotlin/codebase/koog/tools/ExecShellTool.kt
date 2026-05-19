package codebase.koog.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.typeToken
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

private val BASH_BLACKLIST = listOf(
    Regex("rm\\s+-rf", RegexOption.IGNORE_CASE),
    Regex("sudo", RegexOption.IGNORE_CASE),
    Regex("chmod\\s+777"),
    Regex("curl(?![^\\s]*\\s+localhost)", RegexOption.IGNORE_CASE),
    Regex("wget", RegexOption.IGNORE_CASE),
    Regex("/etc/"),
    Regex("/dev/"),
    Regex(">\\s*/"),
    Regex("\\|\\s*sh"),
    Regex("\\|\\s*bash")
)

object ExecShellTool : SimpleTool<ExecShellTool.Args>(
    argsType = typeToken<Args>(),
    name = "__exec_shell__",
    description = "Execute a shell command via bash -c. " +
        "Returns EXIT code + stdout (max 8000 chars). " +
        "Working directory defaults to workspaceRoot. " +
        "DANGEROUS commands (rm -rf, sudo, curl, wget, etc.) are blocked."
) {
    @Serializable
    data class Args(
        @kotlinx.serialization.Transient
        val command: String,
        val workingDir: String = "."
    )

    override suspend fun execute(args: Args): String {
        validateCommand(args.command)

        val workingDir = args.workingDir
        val process = ProcessBuilder("bash", "-c", args.command)
            .directory(File(workingDir))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().take(8000)
        val exited = process.waitFor(120, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            return "EXIT: 124 (timeout)\n$output"
        }
        return "EXIT: ${process.exitValue()}\n$output"
    }

    fun executeBlocking(command: String, workingDir: String = "."): String {
        validateCommand(command)
        return runBlocking { execute(Args(command, workingDir)) }
    }

    fun executeBlocking(command: String, workingDir: String, timeoutMs: Int): String {
        validateCommand(command)
        val workingDirFile = File(workingDir)
        val process = ProcessBuilder("bash", "-c", command)
            .directory(workingDirFile)
            .redirectErrorStream(true)
            .start()

        val exited = process.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        return if (!exited) {
            process.destroyForcibly()
            throw SecurityException("Shell command rejected: timeout after ${timeoutMs}ms")
        } else {
            val output = process.inputStream.bufferedReader().readText().take(8000)
            "EXIT: ${process.exitValue()}\n$output"
        }
    }

    fun validateCommand(command: String) {
        for (pattern in BASH_BLACKLIST) {
            if (pattern.containsMatchIn(command)) {
                throw SecurityException(
                    "Shell command rejected: contains blacklisted pattern '${pattern.pattern}'"
                )
            }
        }
    }
}
