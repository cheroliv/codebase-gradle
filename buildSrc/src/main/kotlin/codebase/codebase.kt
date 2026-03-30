package codebase

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.logging.Logger
import java.io.File
import java.time.LocalDate

// ════════════════════════════════════════════════════════════════════════════
// codebase.yml — Models, Loader, Anonymizer
// ════════════════════════════════════════════════════════════════════════════

// ── codebase.yml Models ───────────────────────────────────────────────────────

/**
 * Une apiKey nommée appartenant à un compte provider.
 *
 * - [label]     → identifiant fonctionnel (ex: "chatbot", "ci", "prod")
 * - [key]       → valeur secrète de la clé → masquée à l'anonymisation
 * - [expiresAt] → date ISO-8601 optionnelle (ex: "2026-12-31") ;
 *                 vide/null = ignoré silencieusement ;
 *                 date dépassée = warning dans les logs Gradle
 */
data class NamedApiKey(
    val label: String = "",
    val key: String = "",
    val expiresAt: String = ""
)

/**
 * Un compte utilisateur chez un provider LLM.
 *
 * - [name]   → identifiant libre du compte (ex: "perso", "pro", "client-acme")
 * - [email]  → email associé au compte (non secret, préservé à l'anonymisation)
 * - [keys]   → liste des apiKeys nommées de ce compte
 */
data class LlmAccount(
    val name: String = "",
    val email: String = "",
    val keys: List<NamedApiKey> = emptyList()
)

/**
 * Configuration d'un provider LLM.
 *
 * - [defaultAccount] → nom du compte actif par défaut
 * - [defaultKey]     → label de la clé active par défaut
 * - [baseUrl]        → URL locale non secrète (utilisée par ollama)
 * - [models]         → liste des modèles disponibles, préservée à l'anonymisation
 * - [defaultModel]   → modèle actif par défaut, préservé à l'anonymisation
 * - [accounts]       → liste des comptes, chacun avec ses propres apiKeys nommées
 */
data class LlmProviderConfig(
    val defaultAccount: String = "",
    val defaultKey: String = "",
    val baseUrl: String = "",
    val models: List<String> = emptyList(),
    val defaultModel: String = "",
    val accounts: List<LlmAccount> = emptyList()
)

/**
 * Regroupe tous les providers LLM configurés dans codebase.yml.
 */
data class AiProvidersConfig(
    val anthropic: LlmProviderConfig = LlmProviderConfig(),
    val gemini: LlmProviderConfig = LlmProviderConfig(),
    val huggingface: LlmProviderConfig = LlmProviderConfig(),
    val mistral: LlmProviderConfig = LlmProviderConfig(),
    val ollama: LlmProviderConfig = LlmProviderConfig(baseUrl = "http://localhost:11434"),
    val grok: LlmProviderConfig = LlmProviderConfig(),
    val groq: LlmProviderConfig = LlmProviderConfig()
)

/**
 * Sélection active globale — surcharge possible via paramètres CLI Gradle :
 *   -Pcodebase.provider=gemini -Pcodebase.account=pro -Pcodebase.key=prod
 */
data class ActiveSelection(
    val provider: String = "anthropic",
    val account: String = "",
    val key: String = ""
)

/**
 * Configuration du chatbot Javelit embarqué.
 */
data class ChatbotConfig(
    val port: Int = 7070,
    val defaultProvider: String = "anthropic"
)

/**
 * Racine de codebase.yml — centralise credentials AI et config chatbot.
 */
data class CodebaseConfiguration(
    val active: ActiveSelection = ActiveSelection(),
    val ai: AiProvidersConfig = AiProvidersConfig(),
    val chatbot: ChatbotConfig = ChatbotConfig()
)

// ── CodebaseYmlConfig ─────────────────────────────────────────────────────────

/**
 * Encapsulates I/O and parsing logic for codebase.yml.
 *
 * codebase.yml is NEVER committed — it contains LLM API keys.
 * It must be listed in .gitignore (managed by scaffoldCodebaseYml).
 *
 * Sensitive fields : NamedApiKey.key (all accounts, all providers except ollama).
 * Non-sensitive    : account.name, account.email, key.label, key.expiresAt,
 *                    ollama.baseUrl, *.models, *.defaultModel, chatbot.*.
 */
class CodebaseYmlConfig {

    @Suppress("PrivatePropertyName")
    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Suppress("PropertyName")
    val CONFIG_FILE_NAME = "codebase.yml"

    /**
     * Loads [CodebaseConfiguration] from [projectDir]/codebase.yml.
     * Falls back to defaults if the file is absent, empty, or contains invalid YAML.
     */
    fun File.loadCodebaseConfiguration(): CodebaseConfiguration {
        val configFile = File(this, CONFIG_FILE_NAME)

        if (!configFile.exists() || configFile.length() == 0L) {
            return CodebaseConfiguration()
                .also { println("[codebase] No $CONFIG_FILE_NAME found or empty file — using defaults") }
        }

        return try {
            MAPPER.readValue(configFile, CodebaseConfiguration::class.java)
                .also { println("[codebase] Config loaded: ${configFile.absolutePath}") }
        } catch (e: Exception) {
            println(
                "[codebase] WARNING: $CONFIG_FILE_NAME contains invalid YAML — " +
                        "using defaults (${e.message})"
            )
            CodebaseConfiguration()
        }
    }

    /**
     * Resolves the active [NamedApiKey] from [cfg], applying CLI overrides when provided.
     *
     * Resolution order (first non-blank wins):
     *  1. CLI parameters : [cliProvider], [cliAccount], [cliKey]
     *  2. cfg.active     : provider / account / key
     *  3. provider defaults : defaultAccount / defaultKey
     *
     * Logs a warning if the resolved key is expired ([NamedApiKey.expiresAt] < today).
     * Returns null if no matching account or key is found.
     */
    fun resolveActiveKey(
        cfg: CodebaseConfiguration,
        logger: Logger,
        cliProvider: String? = null,
        cliAccount: String? = null,
        cliKey: String? = null
    ): NamedApiKey? {
        val providerName = (cliProvider?.takeIf { it.isNotBlank() }
            ?: cfg.active.provider.takeIf { it.isNotBlank() }
            ?: "anthropic").lowercase()

        val provider: LlmProviderConfig = when (providerName) {
            "anthropic" -> cfg.ai.anthropic
            "gemini" -> cfg.ai.gemini
            "huggingface" -> cfg.ai.huggingface
            "mistral" -> cfg.ai.mistral
            "ollama" -> cfg.ai.ollama
            "grok" -> cfg.ai.grok
            "groq" -> cfg.ai.groq
            else -> {
                logger.warn("[codebase] Unknown provider '$providerName' — cannot resolve active key")
                return null
            }
        }

        val accountName = cliAccount?.takeIf { it.isNotBlank() }
            ?: cfg.active.account.takeIf { it.isNotBlank() }
            ?: provider.defaultAccount.takeIf { it.isNotBlank() }

        val account = provider.accounts.firstOrNull { it.name == accountName }
            ?: provider.accounts.firstOrNull()
            ?: return null.also {
                logger.warn("[codebase] No account found for provider '$providerName'")
            }

        val keyLabel = cliKey?.takeIf { it.isNotBlank() }
            ?: cfg.active.key.takeIf { it.isNotBlank() }
            ?: provider.defaultKey.takeIf { it.isNotBlank() }

        val namedKey = (if (keyLabel != null) account.keys.firstOrNull { it.label == keyLabel }
        else account.keys.firstOrNull())
            ?: return null.also {
                logger.warn("[codebase] No key found in account '${account.name}' for provider '$providerName'")
            }

        // ── Warning si clé expirée ────────────────────────────────────────────
        if (namedKey.expiresAt.isNotBlank()) {
            try {
                val expiry = LocalDate.parse(namedKey.expiresAt)
                if (expiry.isBefore(LocalDate.now())) {
                    logger.warn(
                        "[codebase] ⚠️  Key '${namedKey.label}' (account '${account.name}', " +
                                "provider '$providerName') expired on ${namedKey.expiresAt}"
                    )
                }
            } catch (_: Exception) {
                logger.warn("[codebase] expiresAt '${namedKey.expiresAt}' is not a valid ISO-8601 date — ignored")
            }
        }

        return namedKey
    }
}

val codebaseYmlConfig = CodebaseYmlConfig()

// ── CodebaseYmlAnonymizer ─────────────────────────────────────────────────────

/**
 * Encapsulates all anonymization logic for codebase.yml sensitive fields.
 *
 * Sensitive fields handled (→ TOKEN_MASK when non-blank):
 *  - ai.*.accounts[*].keys[*].key  (toutes les apiKeys nommées, sauf ollama)
 *
 * Non-sensitive fields preserved as-is:
 *  - account.name, account.email
 *  - key.label, key.expiresAt
 *  - ollama.baseUrl  (URL locale, pas un secret)
 *  - *.models, *.defaultModel, *.defaultAccount, *.defaultKey
 *  - active.*, chatbot.*
 *
 * Rule: blank key.key → no mask (avoids spurious *** in scaffold output).
 */
class CodebaseYmlAnonymizer {

    @Suppress("PropertyName")
    val TOKEN_MASK = "***"

    /** Masque [NamedApiKey.key] uniquement si non-blank. Label et expiresAt sont préservés. */
    private fun NamedApiKey.anonymized(): NamedApiKey =
        if (key.isBlank()) this else copy(key = TOKEN_MASK)

    /** Masque toutes les clés d'un compte. name et email sont préservés. */
    private fun LlmAccount.anonymized(): LlmAccount =
        copy(keys = keys.map { it.anonymized() })

    /**
     * Masque toutes les clés d'un provider.
     * [maskKeys] = false pour ollama (pas de clés secrètes).
     */
    private fun LlmProviderConfig.anonymized(maskKeys: Boolean = true): LlmProviderConfig =
        if (!maskKeys) this
        else copy(accounts = accounts.map { it.anonymized() })

    /**
     * Returns an anonymized copy of [CodebaseConfiguration].
     * The original is never mutated (data class copy semantics).
     */
    fun CodebaseConfiguration.anonymize(): CodebaseConfiguration = copy(
        ai = ai.copy(
            anthropic = ai.anthropic.anonymized(),
            gemini = ai.gemini.anonymized(),
            huggingface = ai.huggingface.anonymized(),
            mistral = ai.mistral.anonymized(),
            ollama = ai.ollama.anonymized(maskKeys = false),
            grok = ai.grok.anonymized(),
            groq = ai.groq.anonymized()
        )
    )

    /**
     * Serializes the receiver to YAML after anonymizing all sensitive fields.
     */
    fun CodebaseConfiguration.toAnonymizedYaml(mapper: ObjectMapper): String =
        anonymize().let(mapper::writeValueAsString)
}

val codebaseYmlAnonymizer = CodebaseYmlAnonymizer()


// ── Chatbot helpers ───────────────────────────────────────────────────────────

/**
 * Retourne la liste des modèles disponibles pour le provider actif.
 * Retourne les modèles configurés dans LlmProviderConfig.models.
 * Fallback sur un placeholder si la liste est vide (phase tâtonnement UI).
 */
fun availableModels(cfg: CodebaseConfiguration, providerName: String): List<String> {
    val provider: LlmProviderConfig = when (providerName.lowercase()) {
        "anthropic" -> cfg.ai.anthropic
        "gemini" -> cfg.ai.gemini
        "huggingface" -> cfg.ai.huggingface
        "mistral" -> cfg.ai.mistral
        "ollama" -> cfg.ai.ollama
        "grok" -> cfg.ai.grok
        "groq" -> cfg.ai.groq
        else -> return emptyList()
    }
    return provider.models.ifEmpty { listOf("${providerName}-default") }
}

// ── Embeds models + loader ────────────────────────────────────────────────────

/**
 * Un embed RAG — nom affiché dans l'UI + chemin fichier source relatif au projet.
 */
data class EmbedEntry(
    val name: String = "",
    val path: String = ""
)

/**
 * Racine de embeds.yml — liste des embeds disponibles pour enrichissement RAG.
 */
data class EmbedsConfig(
    val embeds: List<EmbedEntry> = emptyList()
)

/**
 * Loader pour embeds.yml — même pattern que les autres YmlConfig.
 * Fallback sur liste vide si le fichier est absent.
 */
class EmbedsYmlConfig {
    @Suppress("PrivatePropertyName")
    private val MAPPER = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @Suppress("PropertyName")
    val CONFIG_FILE_NAME = "embeds.yml"

    fun File.loadEmbedsConfiguration(): EmbedsConfig {
        val f = File(this, CONFIG_FILE_NAME)
        if (!f.exists() || f.length() == 0L) return EmbedsConfig()
        return try {
            MAPPER.readValue(f, EmbedsConfig::class.java)
        } catch (e: Exception) {
            println("[embeds] WARNING: $CONFIG_FILE_NAME invalid — ${e.message}")
            EmbedsConfig()
        }
    }
}

val embedsYmlConfig = EmbedsYmlConfig()

