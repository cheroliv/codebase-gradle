package codebase.rag

sealed class ContextChannel {
    abstract val name: String
    abstract val sectionHeader: String
    abstract val content: String
    abstract val budgetProportion: Double
    abstract val description: String
    abstract val type: ChannelType

    fun truncateToTokens(maxTokens: Int): ContextChannel {
        val lines = content.lines()
        val result = mutableListOf<String>()
        var tokenCount = 0
        for (line in lines) {
            val lineTokens = (line.length / 3.5).toInt().coerceAtLeast(1)
            if (tokenCount + lineTokens > maxTokens) break
            result.add(line)
            tokenCount += lineTokens
        }
        return withContent(result.joinToString("\n"))
    }

    fun isNotEmpty(): Boolean = content.isNotBlank()

    abstract fun withContent(newContent: String): ContextChannel

    data class Eager(
        override val content: String = ""
    ) : ContextChannel() {
        override val name = "EAGER/LAZY"
        override val sectionHeader = "[RÈGLES_EAGER] Contexte de gouvernance et règles absolues des boroughs"
        override val budgetProportion = 0.40
        override val description = "Gouvernance déterministe — INDEX.adoc, PROMPT_REPRISE.adoc, règles absolues"
        override val type get() = ChannelType.EAGER
        override fun withContent(newContent: String) = copy(content = newContent)
    }

    data class Rag(
        override val content: String = ""
    ) : ContextChannel() {
        override val name = "RAG/pgvector"
        override val sectionHeader = "[CONTEXTE_RAG] Résultats sémantiques depuis pgvector (cosine similarity)"
        override val budgetProportion = 0.30
        override val description = "Similarité vectorielle — documents, code source, articles proches sémantiquement"
        override val type get() = ChannelType.RAG
        override fun withContent(newContent: String) = copy(content = newContent)
    }

    data class Graphify(
        override val content: String = ""
    ) : ContextChannel() {
        override val name = "Graphify"
        override val sectionHeader = "[RELATIONS_GRAPHIFY] Graphe de dépendances connaissances entre projets"
        override val budgetProportion = 0.20
        override val description = "Relations exactes — nœuds, arêtes, communautés, dépendances structurelles"
        override val type get() = ChannelType.GRAPHIFY
        override fun withContent(newContent: String) = copy(content = newContent)
    }

    data class Resource(
        override val content: String = ""
    ) : ContextChannel() {
        override val name = "Ressources"
        override val sectionHeader = "[RESSOURCES_COLD] Archives, encyclopédies, datasets historiques"
        override val budgetProportion = 0.10
        override val description = "Mémoire froide — ce qui est archivé mais accessible (encyclopédies, datasets)"
        override val type get() = ChannelType.RESOURCE
        override fun withContent(newContent: String) = copy(content = newContent)
    }

    companion object {
        fun all(): List<ContextChannel> = listOf(Eager(), Rag(), Graphify(), Resource())
    }
}

enum class ChannelType { EAGER, RAG, GRAPHIFY, RESOURCE }
