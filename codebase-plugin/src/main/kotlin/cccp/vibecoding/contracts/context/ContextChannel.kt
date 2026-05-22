package cccp.vibecoding.contracts.context

/**
 * Types de canaux de contexte.
 * Correspond aux 4 variants de la sealed class ContextChannel.
 */
enum class ChannelType { EAGER, RAG, GRAPHIFY, RESOURCE, DOCS }

/**
 * Canal de contexte — sealed class 4 variants.
 * Extraite de codebase.rag dans N0 pour partage N1→N2 sans violation DAG.
 */
sealed class ContextChannel(val source: String, val content: String) {
    /** Type du canal dérivé du source */
    val type: ChannelType get() = when (this) {
        is Eager -> ChannelType.EAGER
        is Rag -> ChannelType.RAG
        is Graphify -> ChannelType.GRAPHIFY
        is Resource -> ChannelType.RESOURCE
        is Docs -> ChannelType.DOCS
    }

    abstract val budgetProportion: Double
    abstract val name: String
    abstract val description: String

    val contentNonEmpty: Boolean get() = content.isNotBlank()
    fun isNotEmpty(): Boolean = contentNonEmpty
    fun withContent(newContent: String): ContextChannel = when (this) {
        is Eager -> Eager(newContent)
        is Rag -> Rag(newContent)
        is Graphify -> Graphify(newContent)
        is Resource -> Resource(newContent)
        is Docs -> Docs(newContent)
    }

    /** Section header utilisé dans l'assemblage composite */
    open val sectionHeader: String get() = ""

    /** Contexte chargé immédiatement (fichiers .adoc EAGER) */
    data class Eager(val contentOnly: String = "") : ContextChannel("EAGER", contentOnly) {
        override val budgetProportion: Double get() = 0.40
        override val name: String get() = "EAGER/LAZY"
        override val description: String get() = "Gouvernance déterministe (.adoc)"
        override val sectionHeader: String get() = "RÈGLES_EAGER"
        constructor(content: String, @Suppress("UNUSED_PARAMETER") dummy: Unit = Unit) : this(content)
    }

    /** Contexte extrait du RAG pgvector (similarité sémantique) */
    data class Rag(val contentOnly: String = "") : ContextChannel("RAG", contentOnly) {
        override val budgetProportion: Double get() = 0.30
        override val name: String get() = "RAG/pgvector"
        override val description: String get() = "Similarité vectorielle (pgvector)"
        override val sectionHeader: String get() = "CONTEXTE_RAG"
    }

    /** Contexte extrait du graphe de dépendances Graphify */
    data class Graphify(val contentOnly: String = "") : ContextChannel("Graphify", contentOnly) {
        override val budgetProportion: Double get() = 0.20
        override val name: String get() = "Graphify"
        override val description: String get() = "Relations structurelles (graphe)"
        override val sectionHeader: String get() = "RELATIONS_GRAPHIFY"
    }

    /** Contexte chargé à la demande (fichiers LAZY) */
    data class Resource(val contentOnly: String = "") : ContextChannel("Resource", contentOnly) {
        override val budgetProportion: Double get() = 0.10
        override val name: String get() = "Ressources"
        override val description: String get() = "Mémoire froide (sur demande)"
        override val sectionHeader: String get() = "RESSOURCES_COLD"
    }

    /** Contexte documentaire — résultats de la recherche vectorielle codex (corpus AFNOR, REAC, manuels) */
    data class Docs(val contentOnly: String = "") : ContextChannel("Docs", contentOnly) {
        override val budgetProportion: Double get() = 0.10
        override val name: String get() = "Codex/Docs"
        override val description: String get() = "Corpus documentaire (codex pgvector)"
        override val sectionHeader: String get() = "CONTEXTE_DOCS"
    }

    companion object {
        fun all(): List<ContextChannel> = listOf(
            Eager(), Rag(), Graphify(), Resource(), Docs()
        )
        fun estimateTokens(text: String): Int = (text.length / 3.5).toInt().coerceAtLeast(1)

        /** Token budget par défaut utilisé pour les calculs tronqués. */
        const val DEFAULT_TOKEN_BUDGET: Int = 8000
    }

    /**
     * Tronque ce canal au nombre de tokens maximum spécifié.
     * Ne modifie pas le contenu si déjà en-dessous du budget.
     */
    fun truncateToTokens(maxTokens: Int): ContextChannel {
        val lines = content.lines()
        val truncated = lines.fold("" to 0) { (acc, count), line ->
            val lineTokens = estimateTokens(line)
            if (count + lineTokens <= maxTokens) (acc + line + "\n") to (count + lineTokens)
            else acc to count
        }.first
        return when (this) {
            is Eager -> Eager(truncated)
            is Rag -> Rag(truncated)
            is Graphify -> Graphify(truncated)
            is Resource -> Resource(truncated)
            is Docs -> Docs(truncated)
        }
    }
}

/**
 * Budget token proportionnel 40/30/20/10.
 * Les valeurs (eager, rag, graphify, docs, resource) sont des fractions (0.0–1.0) qui doivent sommer à 1.0.
 * Les propriétés `*Tokens` calculent le nombre de tokens à partir du budget total.
 */
data class ChannelBudget(
    val totalTokenBudget: Int = ContextChannel.DEFAULT_TOKEN_BUDGET,
    val budgetEager: Double = 0.40,
    val budgetRag: Double = 0.30,
    val budgetGraphify: Double = 0.20,
    val budgetDocs: Double = 0.10,
    val budgetResource: Double = 0.0
) {
    init {
        val sum = budgetEager + budgetRag + budgetGraphify + budgetDocs + budgetResource
        require(kotlin.math.abs(sum - 1.0) < 0.001) { "ChannelBudget must sum to 1.0, got $sum" }
    }

    val eagerTokens: Int get() = (totalTokenBudget * budgetEager).toInt()
    val ragTokens: Int get() = (totalTokenBudget * budgetRag).toInt()
    val graphifyTokens: Int get() = (totalTokenBudget * budgetGraphify).toInt()
    val docsTokens: Int get() = (totalTokenBudget * budgetDocs).toInt()
    val resourceTokens: Int get() = (totalTokenBudget * budgetResource).toInt()

    fun tokensFor(type: ChannelType): Int = when (type) {
        ChannelType.EAGER -> eagerTokens
        ChannelType.RAG -> ragTokens
        ChannelType.GRAPHIFY -> graphifyTokens
        ChannelType.DOCS -> docsTokens
        ChannelType.RESOURCE -> resourceTokens
    }

    fun tokensFor(channel: ContextChannel): Int = tokensFor(channel.type)

    fun applyBudget(channels: List<ContextChannel>): List<ContextChannel> {
        val tokenCounts = listOf(eagerTokens, ragTokens, graphifyTokens, docsTokens, resourceTokens)
        return channels.zip(tokenCounts) { channel, maxTokens ->
            channel.truncateToTokens(maxTokens)
        }
    }

    companion object {
        fun fromConfig(config: CompositeContextConfig): ChannelBudget {
            return ChannelBudget(
                totalTokenBudget = config.totalTokenBudget,
                budgetEager = config.budgetEagerLazy,
                budgetRag = config.budgetRag,
                budgetGraphify = config.budgetGraphify,
                budgetDocs = config.budgetDocs,
                budgetResource = config.budgetOverhead
            )
        }
    }
}
