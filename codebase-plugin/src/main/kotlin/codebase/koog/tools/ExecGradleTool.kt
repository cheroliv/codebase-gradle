package codebase.koog.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.serialization.typeToken
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.TimeUnit

private val GRADLE_BLACKLIST = listOf(
    Regex("clean\\s+build", RegexOption.IGNORE_CASE),
    Regex("--refresh-dependencies", RegexOption.IGNORE_CASE),
    Regex("rm\\s", RegexOption.IGNORE_CASE),
    Regex("delete\\s", RegexOption.IGNORE_CASE)
)

object ExecGradleTool : SimpleTool<ExecGradleTool.Args>(
    argsType = typeToken<Args>(),
    name = "__exec_gradle__",
    description = "Execute a Gradle task via ./gradlew. " +
        "Returns EXIT code + stdout (max 8000 chars). " +
        "Use for build, test, compile, publish, etc."
) {
    @Serializable
    data class Args(
        val task: String,
        val workingDir: String = "."
    )

    override suspend fun execute(args: Args): String {
        validateGradleTask(args.task)

        val workingDir = args.workingDir
        val process = ProcessBuilder("./gradlew", args.task)
            .directory(File(workingDir))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().take(8000)
        val exited = process.waitFor(120, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            return "GRADLE EXIT: 124 (timeout)\n$output"
        }
        return "GRADLE EXIT: ${process.exitValue()}\n$output"
    }

    fun executeBlocking(task: String, workingDir: String = "."): String {
        validateGradleTask(task)
        return runBlocking { execute(Args(task, workingDir)) }
    }

    fun validateGradleTask(task: String) {
        for (pattern in GRADLE_BLACKLIST) {
            if (pattern.containsMatchIn(task)) {
                throw SecurityException(
                    "Gradle task rejected: contains blacklisted pattern '${pattern.pattern}'"
                )
            }
        }
    }
}
