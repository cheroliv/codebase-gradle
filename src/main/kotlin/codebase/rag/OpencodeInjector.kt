package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File

class OpencodeInjector {

    private val log = LoggerFactory.getLogger(OpencodeInjector::class.java)

    fun inject(context: CompositeContext): String {
        val sb = StringBuilder()

        sb.appendLine("[RÈGLES_EAGER] Contexte de gouvernance et règles absolues des boroughs")
        sb.appendLine(context.eagerSection)
        sb.appendLine()

        sb.appendLine("[CONTEXTE_RAG] Résultats sémantiques depuis pgvector (cosine similarity)")
        sb.appendLine(context.ragSection)
        sb.appendLine()

        sb.appendLine("[RELATIONS_GRAPHIFY] Graphe de dépendances connaissances entre projets")
        sb.appendLine(context.graphifySection)

        val output = sb.toString()
        log.info("OpencodeInjector: injected {} chars (EAGER={}, RAG={}, Graphify={})",
            output.length,
            context.eagerSection.length,
            context.ragSection.length,
            context.graphifySection.length)

        return output
    }

    fun injectToFile(context: CompositeContext, outputFile: File): File {
        val content = inject(context)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(content)
        log.info("Context injected to ${outputFile.absolutePath} ({} chars)", content.length)
        return outputFile
    }
}
