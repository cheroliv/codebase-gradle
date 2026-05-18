package codebase.rag

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Workspace context changes between boroughs — never cache")
abstract class AssembleWorkspaceContextTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val foundryDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val foundry = foundryDir.get().asFile
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()

        val contextFiles = foundry.listFiles { it.isDirectory }
            ?.sortedBy { it.name }
            ?.flatMap { boroughDir ->
                val contextDir = boroughDir.resolve("build/context")
                if (!contextDir.isDirectory) return@flatMap emptyList()
                contextDir.listFiles { it.isFile && it.extension == "txt" }
                    ?.filter { it.name.endsWith(".context.txt") }
                    ?.map { boroughDir.name to it }
                    ?: emptyList()
            }
            ?: emptyList()

        if (contextFiles.isEmpty()) {
            logger.warn("[collectCompositeContext] No context files found — check build/context/ in boroughs")
            output.writeText("[Workspace Context] Aucun fichier contexte borough disponible.\n")
            return
        }

        val sb = StringBuilder()
        sb.appendLine("= CONTEXTE WORKSPACE AUGMENTE — Engine N3")
        sb.appendLine(":toc:")
        sb.appendLine(":data: ${System.currentTimeMillis()}")
        sb.appendLine(":boroughs_agreges: ${contextFiles.size}")
        sb.appendLine()
        sb.appendLine("== Sommaire des Boroughs Indexes")
        sb.appendLine()
        for ((boroughName, _) in contextFiles) {
            sb.appendLine("* $boroughName")
        }
        sb.appendLine()

        var totalBytes = 0L
        for ((boroughName, file) in contextFiles) {
            val content = file.readText()
            totalBytes += file.length()
            sb.appendLine("== $boroughName")
            sb.appendLine(content)
            sb.appendLine()
        }

        val result = sb.toString()
        output.writeText(result)

        logger.lifecycle("[collectCompositeContext] {} boroughs agreges, {} bytes -> {}",
            contextFiles.size, totalBytes, output.absolutePath)
    }
}
