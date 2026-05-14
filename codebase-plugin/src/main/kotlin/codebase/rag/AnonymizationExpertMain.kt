package codebase.rag

import java.io.File

object AnonymizationExpertMain {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 1) {
            System.err.println("Usage: java ... AnonymizationExpertMain <inputFile> [outputFile]")
            return
        }

        val inputFile = File(args[0])
        if (!inputFile.exists()) {
            System.err.println("Input file not found: ${inputFile.absolutePath}")
            return
        }

        val outputFile = if (args.size >= 2) File(args[1]) else File("build/anonymized-output.yml")
        val content = inputFile.readText()
        val extension = inputFile.extension.lowercase()
        val format = when (extension) {
            "yml", "yaml" -> "yaml"
            "json" -> "json"
            "xml" -> "xml"
            "sql" -> "sql"
            "adoc" -> "adoc"
            "kt" -> "kotlin"
            else -> "text"
        }

        System.err.println("[EPIC-2] Anonymisation de ${inputFile.name} (format=$format)")

        val expert = AnonymizationExpertFactory.create()
        val request = AnonymizationRequest(
            sourcePath = inputFile.absolutePath,
            content = content,
            targetFormat = format
        )

        val result = expert.anonymizeRequest(request)

        outputFile.parentFile.mkdirs()
        outputFile.writeText(result.anonymizedContent)

        System.err.println("[EPIC-2] Résultat:")
        System.err.println("  Fichier output  : ${outputFile.absolutePath}")
        System.err.println("  Confiance       : ${"%.2f".format(result.confidenceScore)}")
        System.err.println("  PII détectées   : ${result.detectedPiiCategories.joinToString()}")
        System.err.println("  Remplacements   : ${result.replacedCount}")
        System.err.println("  Résumé          : ${result.summary}")
    }
}
