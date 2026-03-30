// build.gradle.kts
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.javelit.components.layout.ColumnsComponent
import io.javelit.core.Jt
import io.javelit.core.JtComponent
import io.javelit.core.Server
import jakarta.validation.Validation
import jakarta.validation.constraints.Email
import java.time.LocalDate
import java.util.concurrent.CountDownLatch

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2")
        classpath("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
        classpath("jakarta.validation:jakarta.validation-api:3.1.0")
        classpath("org.hibernate.validator:hibernate-validator:8.0.1.Final")
        classpath("org.glassfish.expressly:expressly:5.0.0")
        classpath("io.javelit:javelit:0.86.0")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.readme)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// ── readme.yml Models ─────────────────────────────────────────────────────────

data class SourceConfig(
    val dir: String = ".",
    val defaultLang: String = "en"
)

data class OutputConfig(
    val imgDir: String = ".github/workflows/readmes/images"
)

data class GitConfig(
    val userName: String = "github-actions[bot]",
    val userEmail: String = "github-actions[bot]@users.noreply.github.com",
    val commitMessage: String = "chore: generate readme [skip ci]",
    val token: String = "",
    val watchedBranches: List<String> = listOf("main", "master")
)

data class ReadmePlantUmlConfig(
    val source: SourceConfig = SourceConfig(),
    val output: OutputConfig = OutputConfig(),
    val git: GitConfig = GitConfig()
) {
    companion object
}

// ── ReadmeYmlConfig ───────────────────────────────────────────────────────────

/**
 * Encapsulates all I/O and parsing logic for readme.yml.
 *
 * readme.yml is NEVER committed with a real token.
 * Its content (token included) is stored in the GitHub secret
 * README_GRADLE_PLUGIN and written to disk by CI:
 *
 *   echo "${{ secrets.README_GRADLE_PLUGIN }}" > readme.yml
 *   ./gradlew -q -s commitGeneratedReadme
 *
 * TODO: migrate to tools.jackson 3.x when a stable release is available
 */
class ReadmeYmlConfig {

    @Suppress("PrivatePropertyName")
    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

    @Suppress("PropertyName")
    val CONFIG_FILE_NAME = "readme.yml"

    /**
     * Loads [ReadmePlantUmlConfig] from [projectDir]/readme.yml.
     * Falls back to defaults if the file is absent, empty, or contains invalid YAML.
     */
    fun ReadmePlantUmlConfig.Companion.load(projectDir: File): ReadmePlantUmlConfig {
        val configFile = File(projectDir, CONFIG_FILE_NAME)

        if (!configFile.exists() || configFile.length() == 0L) {
            return ReadmePlantUmlConfig()
                .also { println("[readme] No $CONFIG_FILE_NAME found or empty file — using defaults") }
        }

        return try {
            MAPPER.readValue(configFile, ReadmePlantUmlConfig::class.java)
                .also { println("[readme] Config loaded: ${configFile.absolutePath}") }
        } catch (e: Exception) {
            println(
                "[readme] WARNING: $CONFIG_FILE_NAME contains invalid YAML — " +
                        "using defaults (${e.message})"
            )
            ReadmePlantUmlConfig()
        }
    }
}

val readmeYmlConfig = ReadmeYmlConfig()

// ── ReadmeYmlAnonymizer ───────────────────────────────────────────────────────

/**
 * Encapsulates all anonymization logic for readme.yml sensitive fields.
 *
 * Sensitive fields handled:
 *  - git.token     → TOKEN_MASK
 *  - git.userName  → ANONYMOUS_USERNAME
 *  - git.userEmail → built from ANONYMOUS_USERNAME + ACME_DOMAIN,
 *                    validated with @Email before use
 */
class ReadmeYmlAnonymizer {

    @Suppress("PropertyName")
    val TOKEN_MASK = "***"

    @Suppress("PropertyName")
    val ANONYMOUS_USERNAME = "anonymous"

    @Suppress("PropertyName")
    val ACME_DOMAIN = "acme.com"

    /** Carrier used solely to trigger Bean Validation @Email on a string. */
    private data class EmailHolder(@field:Email val value: String)

    /**
     * Builds a valid anonymized email: "<ANONYMOUS_USERNAME>@<ACME_DOMAIN>".
     * Throws [IllegalStateException] if the generated address fails @Email validation.
     */
    fun anonymizedEmail(): String {
        val candidate = "$ANONYMOUS_USERNAME@$ACME_DOMAIN"
        val validator = Validation.buildDefaultValidatorFactory().validator
        val violations = validator.validate(EmailHolder(candidate))
        check(violations.isEmpty()) {
            "Generated email '$candidate' failed @Email validation: ${violations.map { it.message }}"
        }
        return candidate
    }

    /**
     * Returns an anonymized copy of the receiver.
     * The original is never mutated (data class copy semantics).
     */
    fun ReadmePlantUmlConfig.anonymize(): ReadmePlantUmlConfig =
        copy(
            git = git.copy(
                token = TOKEN_MASK,
                userName = ANONYMOUS_USERNAME,
                userEmail = anonymizedEmail()
            )
        )

    /**
     * Serializes the receiver to YAML after anonymizing all sensitive fields.
     * The [mapper] is provided by the caller (format and modules are caller's responsibility).
     */
    fun ReadmePlantUmlConfig.toAnonymizedYaml(mapper: ObjectMapper): String =
        anonymize().let(mapper::writeValueAsString)
}

val readmeYmlAnonymizer = ReadmeYmlAnonymizer()

// ── slider-context.yml Models ─────────────────────────────────────────────────

data class RepositoryCredentials(
    val username: String = "",
    val password: String = ""
)

data class RepositoryConfiguration(
    val name: String = "",
    val repository: String = "",
    val credentials: RepositoryCredentials = RepositoryCredentials()
)

data class GitPushConfiguration(
    val from: String = "",
    val to: String = "",
    val repo: RepositoryConfiguration = RepositoryConfiguration(),
    val branch: String = "",
    val message: String = ""
)

data class AiConfiguration(
    val gemini: List<String> = emptyList(),
    val huggingface: List<String> = emptyList(),
    val mistral: List<String> = emptyList()
)

data class SliderConfiguration(
    val srcPath: String? = null,
    val pushSlider: GitPushConfiguration? = null,
    val ai: AiConfiguration? = null
) {
    companion object
}

// ── SliderYmlConfig ───────────────────────────────────────────────────────────

/**
 * Encapsulates I/O and parsing logic for slider-context.yml.
 *
 * slider-context.yml is NEVER committed with real credentials.
 * Sensitive fields: repo.repository, repo.credentials.username,
 * repo.credentials.password, branch, ai.gemini, ai.huggingface, ai.mistral.
 */
class SliderYmlConfig {

    @Suppress("PrivatePropertyName")
    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

    @Suppress("PropertyName")
    val CONFIG_FILE_NAME = "slider-context.yml"

    /**
     * Loads [SliderConfiguration] from [projectDir]/slider-context.yml.
     * Falls back to defaults if the file is absent, empty, or contains invalid YAML.
     */
    fun SliderConfiguration.Companion.load(projectDir: File): SliderConfiguration {
        val configFile = File(projectDir, CONFIG_FILE_NAME)

        if (!configFile.exists() || configFile.length() == 0L) {
            return SliderConfiguration()
                .also { println("[slider] No $CONFIG_FILE_NAME found or empty file — using defaults") }
        }

        return try {
            MAPPER.readValue(configFile, SliderConfiguration::class.java)
                .also { println("[slider] Config loaded: ${configFile.absolutePath}") }
        } catch (e: Exception) {
            println(
                "[slider] WARNING: $CONFIG_FILE_NAME contains invalid YAML — " +
                        "using defaults (${e.message})"
            )
            SliderConfiguration()
        }
    }
}

val sliderYmlConfig = SliderYmlConfig()

// ── SliderYmlAnonymizer ───────────────────────────────────────────────────────

/**
 * Encapsulates all anonymization logic for slider-context.yml sensitive fields.
 *
 * Sensitive fields handled:
 *  - pushSlider.repo.repository           → REPO_MASK
 *  - pushSlider.repo.credentials.username → ANONYMOUS_USERNAME
 *  - pushSlider.repo.credentials.password → TOKEN_MASK
 *  - pushSlider.branch                    → BRANCH_MASK
 *  - ai.gemini      (all entries)         → [TOKEN_MASK]
 *  - ai.huggingface (all entries)         → [TOKEN_MASK]
 *  - ai.mistral     (all entries)         → [TOKEN_MASK]
 */
class SliderYmlAnonymizer {

    @Suppress("PropertyName")
    val TOKEN_MASK = "***"

    @Suppress("PropertyName")
    val ANONYMOUS_USERNAME = "anonymous"

    @Suppress("PropertyName")
    val REPO_MASK = "https://github.com/anonymous/anonymous.git"

    @Suppress("PropertyName")
    val BRANCH_MASK = "main"

    private fun List<String>.maskAll(): List<String> =
        if (isEmpty()) emptyList() else List(size) { TOKEN_MASK }

    /**
     * Returns an anonymized copy of the receiver.
     * The original is never mutated (data class copy semantics).
     */
    fun SliderConfiguration.anonymize(): SliderConfiguration = copy(
        pushSlider = pushSlider?.copy(
            branch = BRANCH_MASK,
            repo = pushSlider.repo.copy(
                repository = REPO_MASK,
                credentials = pushSlider.repo.credentials.copy(
                    username = ANONYMOUS_USERNAME,
                    password = TOKEN_MASK
                )
            )
        ),
        ai = ai?.copy(
            gemini = ai.gemini.maskAll(),
            huggingface = ai.huggingface.maskAll(),
            mistral = ai.mistral.maskAll()
        )
    )

    /**
     * Serializes the receiver to YAML after anonymizing all sensitive fields.
     */
    fun SliderConfiguration.toAnonymizedYaml(mapper: ObjectMapper): String =
        anonymize().let(mapper::writeValueAsString)
}

val sliderYmlAnonymizer = SliderYmlAnonymizer()

// ── site.yml Models ───────────────────────────────────────────────────────────

data class BakeConfiguration(
    val srcPath: String = "",
    val destDirPath: String = "",
    val cname: String = ""
)

data class SupabaseColumn(
    val name: String,
    val type: String
)

data class SupabaseParam(
    val name: String,
    val type: String
)

data class SupabaseTable(
    val name: String,
    val columns: List<SupabaseColumn>,
    val rlsEnabled: Boolean
)

data class SupabaseDatabaseSchema(
    val contacts: SupabaseTable,
    val messages: SupabaseTable
)

data class SupabaseRpcFunction(
    val name: String,
    val params: List<SupabaseParam>
)

data class SupabaseProjectInfo(
    val url: String,
    val publicKey: String
)

data class SupabaseContactFormConfig(
    val project: SupabaseProjectInfo,
    val schema: SupabaseDatabaseSchema,
    val rpc: SupabaseRpcFunction
)

data class SiteConfiguration(
    val bake: BakeConfiguration = BakeConfiguration(),
    val pushPage: GitPushConfiguration = GitPushConfiguration(),
    val pushMaquette: GitPushConfiguration = GitPushConfiguration(),
    val pushSource: GitPushConfiguration? = null,
    val pushTemplate: GitPushConfiguration? = null,
    val supabase: SupabaseContactFormConfig? = null
) {
    companion object
}

// ── SiteYmlConfig ─────────────────────────────────────────────────────────────

/**
 * Encapsulates I/O and parsing logic for site.yml.
 *
 * site.yml is NEVER committed with real credentials.
 * Sensitive fields: repo.repository, repo.credentials.username,
 * repo.credentials.password, branch, supabase.project.url,
 * supabase.project.publicKey.
 */
class SiteYmlConfig {

    @Suppress("PrivatePropertyName")
    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

    @Suppress("PropertyName")
    val CONFIG_FILE_NAME = "site.yml"

    /**
     * Loads [SiteConfiguration] from [projectDir]/site.yml.
     * Falls back to defaults if the file is absent, empty, or contains invalid YAML.
     */
    fun SiteConfiguration.Companion.load(projectDir: File): SiteConfiguration {
        val configFile = File(projectDir, CONFIG_FILE_NAME)

        if (!configFile.exists() || configFile.length() == 0L) {
            return SiteConfiguration()
                .also { println("[bakery] No $CONFIG_FILE_NAME found or empty file — using defaults") }
        }

        return try {
            MAPPER.readValue(configFile, SiteConfiguration::class.java)
                .also { println("[bakery] Config loaded: ${configFile.absolutePath}") }
        } catch (e: Exception) {
            println(
                "[bakery] WARNING: $CONFIG_FILE_NAME contains invalid YAML — " +
                        "using defaults (${e.message})"
            )
            SiteConfiguration()
        }
    }
}

val siteYmlConfig = SiteYmlConfig()

// ── SiteYmlAnonymizer ─────────────────────────────────────────────────────────

/**
 * Encapsulates all anonymization logic for site.yml sensitive fields.
 *
 * Sensitive fields handled:
 *  - pushPage.repo.repository           → REPO_MASK
 *  - pushPage.repo.credentials.username → ANONYMOUS_USERNAME
 *  - pushPage.repo.credentials.password → TOKEN_MASK
 *  - pushPage.branch                    → BRANCH_MASK
 *  - pushMaquette  (idem)
 *  - pushSource    (idem, nullable)
 *  - pushTemplate  (idem, nullable)
 *  - supabase.project.url               → URL_MASK
 *  - supabase.project.publicKey         → TOKEN_MASK
 */
class SiteYmlAnonymizer {

    @Suppress("PropertyName")
    val TOKEN_MASK = "***"

    @Suppress("PropertyName")
    val ANONYMOUS_USERNAME = "anonymous"

    @Suppress("PropertyName")
    val REPO_MASK = "https://github.com/anonymous/anonymous.git"

    @Suppress("PropertyName")
    val BRANCH_MASK = "main"

    @Suppress("PropertyName")
    val URL_MASK = "https://anonymous.supabase.co"

    /**
     * Anonymizes one [GitPushConfiguration] — shared by all push* fields.
     */
    private fun GitPushConfiguration.anonymize(): GitPushConfiguration = copy(
        branch = BRANCH_MASK,
        repo = repo.copy(
            repository = REPO_MASK,
            credentials = repo.credentials.copy(
                username = ANONYMOUS_USERNAME,
                password = TOKEN_MASK
            )
        )
    )

    /**
     * Returns an anonymized copy of the receiver.
     * The original is never mutated (data class copy semantics).
     */
    fun SiteConfiguration.anonymize(): SiteConfiguration = copy(
        pushPage = pushPage.anonymize(),
        pushMaquette = pushMaquette.anonymize(),
        pushSource = pushSource?.anonymize(),
        pushTemplate = pushTemplate?.anonymize(),
        supabase = supabase?.copy(
            project = supabase.project.copy(
                url = URL_MASK,
                publicKey = TOKEN_MASK
            )
        )
    )

    /**
     * Serializes the receiver to YAML after anonymizing all sensitive fields.
     */
    fun SiteConfiguration.toAnonymizedYaml(mapper: ObjectMapper): String =
        anonymize().let(mapper::writeValueAsString)
}

val siteYmlAnonymizer = SiteYmlAnonymizer()

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
) {
    companion object
}

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
    fun CodebaseConfiguration.Companion.load(projectDir: File): CodebaseConfiguration {
        val configFile = File(projectDir, CONFIG_FILE_NAME)

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

// ── SnapshotManager ───────────────────────────────────────────────────────────

/**
 * Encapsulates all snapshot generation logic:
 * tree view, file collection, AsciiDoc rendering, and output writing.
 */
class SnapshotManager {

    @Suppress("PropertyName")
    val PRUNED_DIRS = setOf(
        "build", ".gradle", ".git", ".idea",
        "node_modules", ".kotlin", "__pycache__"
    )

    @Suppress("PropertyName")
    val COLLECTED_EXTENSIONS = setOf(
        "kt", "kts", "yml", "yaml", "properties", "toml", "adoc"
    )

    @Suppress("PropertyName")
    val COLLECTED_FILENAMES = setOf(
        "readme.yml",
        "slider-context.yml",
        "site.yml",
        "codebase.yml",
        "gradle.properties",
        "settings.gradle.kts",
        "build.gradle.kts"
    )

    @Suppress("PropertyName")
    val EXCLUDED_FILENAMES = setOf(
        "snapshot.adoc",
        "gradlew",
        "gradlew.bat"
    )

    fun File.isPrunedDir(): Boolean =
        this.isDirectory && this.name in PRUNED_DIRS

    fun File.extension(): String =
        this.name.substringAfterLast('.', "")

    fun File.isCollectible(): Boolean =
        this.isFile &&
                this.name !in EXCLUDED_FILENAMES &&
                this.extension() in COLLECTED_EXTENSIONS

    fun File.asciidocLang(): String = when (this.extension()) {
        "kt", "kts" -> "kotlin"
        "toml" -> "toml"
        "adoc" -> "asciidoc"
        "yml", "yaml" -> "yaml"
        "properties" -> "properties"
        else -> "text"
    }

    /**
     * Builds a readable project tree rooted at [this], pruning noisy directories
     * and excluded files.
     */
    fun File.buildTreeView(): String {
        val lines = mutableListOf<String>()

        fun File.walk(prefix: String) {
            val children = this.listFiles()
                ?.filter { !it.isPrunedDir() && it.name !in EXCLUDED_FILENAMES }
                ?.sortedWith(compareBy({ it.isFile }, { it.name }))
                ?: return

            children.forEachIndexed { index, file ->
                val isLast = index == children.lastIndex
                val connector = if (isLast) "└── " else "├── "
                val childPfx = if (isLast) "    " else "│   "
                lines += "$prefix$connector${file.name}"
                if (file.isDirectory) file.walk("$prefix$childPfx")
            }
        }

        lines += this.name
        this.walk("")
        return lines.joinToString("\n")
    }

    /**
     * Resolves the GitHub token from [this].
     * Throws [IllegalStateException] if the token is blank or still a placeholder.
     */
    fun GitConfig.resolvedToken(): String =
        token.takeIf { it.isNotBlank() && it != "<YOUR_GITHUB_PAT>" }
            ?: error(
                "GitHub token is empty or still a placeholder in readme.yml.\n" +
                        "→ Check the README_GRADLE_PLUGIN secret in:\n" +
                        "   GitHub → Settings → Secrets and variables → Actions"
            )

    /**
     * Walks [this] directory, collecting all source files for the snapshot.
     */
    fun File.walk(collected: LinkedHashSet<File> = linkedSetOf()): LinkedHashSet<File> {
        if (isPrunedDir()) return collected
        listFiles()
            ?.sortedBy { it.name }
            ?.forEach { entry ->
                when {
                    entry.isDirectory && entry.name == "src" -> {
                        entry.walkTopDown()
                            .filter { it.isCollectible() }
                            .sorted()
                            .forEach { collected += it }
                        entry.parentFile
                            .listFiles()
                            ?.filter { it.isCollectible() }
                            ?.sorted()
                            ?.forEach { collected += it }
                        entry.walk(collected)
                    }

                    entry.isDirectory -> entry.walk(collected)
                    else -> Unit
                }
            }
        return collected
    }

    /**
     * Collects all source files to include in the snapshot.
     */
    fun File.collectFiles(): List<File> {
        val collected = walk()
        listFiles()
            ?.filter { it.isFile && it.name in COLLECTED_FILENAMES }
            ?.sorted()
            ?.forEach { collected += it }
        return collected.toList()
    }

    /**
     * Renders one AsciiDoc section for [file], relative to [root].
     * Sensitive YAML files are rendered anonymized via explicit with(anonymizer) —
     * no top-level facade to avoid ambiguous resolution and StackOverflow.
     */
    fun ObjectMapper.renderFileSection(file: File, root: File): String = buildString {
        val relPath = file.relativeTo(root).path
        appendLine("== $relPath")
        appendLine()
        appendLine("[source,${file.asciidocLang()}]")
        appendLine("----")
        val content = when (file.name) {
            readmeYmlConfig.CONFIG_FILE_NAME -> {
                val cfg = with(readmeYmlConfig) { ReadmePlantUmlConfig.load(root) }
                with(readmeYmlAnonymizer) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            sliderYmlConfig.CONFIG_FILE_NAME -> {
                val cfg = with(sliderYmlConfig) { SliderConfiguration.load(root) }
                with(sliderYmlAnonymizer) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            siteYmlConfig.CONFIG_FILE_NAME -> {
                val cfg = with(siteYmlConfig) { SiteConfiguration.load(root) }
                with(siteYmlAnonymizer) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            codebaseYmlConfig.CONFIG_FILE_NAME -> {
                val cfg = with(codebaseYmlConfig) { CodebaseConfiguration.load(root) }
                with(codebaseYmlAnonymizer) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            else -> file.readText()
        }
        appendLine(content)
        appendLine("----")
        appendLine()
    }

    /**
     * Assembles the full AsciiDoc document from [root].
     */
    fun ObjectMapper.buildAdoc(root: File): String {
        val sourceFiles = root.collectFiles()
        return buildString {
            appendLine("= Project Snapshot")
            appendLine(":toc:")
            appendLine(":toclevels: 3")
            appendLine(":source-highlighter: highlight.js")
            appendLine()
            appendLine("== Project Structure")
            appendLine()
            appendLine("[listing]")
            appendLine("----")
            appendLine(root.buildTreeView())
            appendLine("----")
            appendLine()
            sourceFiles.forEach { append(renderFileSection(it, root)) }
        }
    }

    /**
     * Entry point — generates snapshot.adoc under [this] root directory.
     */
    fun File.generate(logger: Logger): List<File> {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val sourceFiles = collectFiles()

        logger.lifecycle("── project tree ────────────────────────────────────")
        logger.lifecycle(buildTreeView())
        logger.lifecycle("── collected files (${sourceFiles.size}) ──────────────────────────")
        sourceFiles.forEach { logger.lifecycle("  ${it.relativeTo(this).path}") }

        File(this, "snapshot.adoc").writeText(mapper.buildAdoc(this))

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ snapshot.adoc generated — ${sourceFiles.size} file(s) captured")
        logger.lifecycle("   → ${File(this, "snapshot.adoc").absolutePath}")

        return sourceFiles
    }
}

val snapshotManager = SnapshotManager()

// ── Tasks ─────────────────────────────────────────────────────────────────────

/**
 * Verifies via logs that ReadmePlantUmlConfig anonymization masks token, userName
 * and userEmail, and preserves all other fields.
 *
 * Usage: ./gradlew verifyReadMeToAnonymizedYaml
 */
tasks.register("verifyReadMeToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that ReadmePlantUmlConfig anonymization masks sensitive fields"

    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = ReadmeYmlAnonymizer()

        // ── case 1: real token ───────────────────────────────────────────────
        val realToken = "ghp_supersecret123"
        val configReal = ReadmePlantUmlConfig(
            source = SourceConfig(dir = "src", defaultLang = "fr"),
            git = GitConfig(
                token = realToken,
                userName = "my-bot",
                userEmail = "my-bot@company.com",
                watchedBranches = listOf("main", "develop")
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: real token ──────────────────────────────")
        logger.lifecycle(yamlReal)

        check(anonymizer.TOKEN_MASK in yamlReal) { "FAIL case 1: token mask not found" }
        check(realToken !in yamlReal) { "FAIL case 1: real token is visible!" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1: userName not anonymized" }
        check("my-bot" !in yamlReal) { "FAIL case 1: real userName is visible!" }
        check(anonymizer.anonymizedEmail() in yamlReal) { "FAIL case 1: anonymized email not found" }
        check("my-bot@company.com" !in yamlReal) { "FAIL case 1: real email is visible!" }
        check("fr" in yamlReal) { "FAIL case 1: defaultLang lost" }
        check("src" in yamlReal) { "FAIL case 1: source.dir lost" }
        check("develop" in yamlReal) { "FAIL case 1: watchedBranches lost" }

        logger.lifecycle("✅ case 1 OK — token, userName, userEmail anonymized — other fields preserved")

        // ── case 2: placeholder token ────────────────────────────────────────
        val configPlaceholder = ReadmePlantUmlConfig(
            git = GitConfig(token = "<YOUR_GITHUB_PAT>")
        )
        val yamlPlaceholder = with(anonymizer) { configPlaceholder.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: placeholder token ───────────────────────")
        logger.lifecycle(yamlPlaceholder)

        check(anonymizer.TOKEN_MASK in yamlPlaceholder) { "FAIL case 2: token mask not found" }
        check("<YOUR_GITHUB_PAT>" !in yamlPlaceholder) { "FAIL case 2: placeholder is visible!" }
        check(anonymizer.anonymizedEmail() in yamlPlaceholder) { "FAIL case 2: anonymized email not found" }

        logger.lifecycle("✅ case 2 OK — placeholder anonymized")

        // ── case 3: empty token ──────────────────────────────────────────────
        val configEmpty = ReadmePlantUmlConfig(git = GitConfig(token = ""))
        val yamlEmpty = with(anonymizer) { configEmpty.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: empty token ─────────────────────────────")
        logger.lifecycle(yamlEmpty)

        check(anonymizer.TOKEN_MASK in yamlEmpty) { "FAIL case 3: token mask not found for empty token" }
        check(anonymizer.anonymizedEmail() in yamlEmpty) { "FAIL case 3: anonymized email not found" }

        logger.lifecycle("✅ case 3 OK — empty token anonymized")

        // ── case 4: idempotency — original object must not be mutated ────────
        val configIdempotent = ReadmePlantUmlConfig(
            git = GitConfig(token = "ghp_idempotence", userName = "real-user")
        )
        with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }
        val yaml2 = with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }

        check("ghp_idempotence" !in yaml2) { "FAIL case 4: token visible on 2nd call — mutation detected!" }
        check("real-user" !in yaml2) { "FAIL case 4: userName visible on 2nd call — mutation detected!" }
        check(anonymizer.TOKEN_MASK in yaml2) { "FAIL case 4: token mask not found on 2nd call" }
        check(anonymizer.ANONYMOUS_USERNAME in yaml2) { "FAIL case 4: anonymized userName not found on 2nd call" }

        logger.lifecycle("✅ case 4 OK — original not mutated, idempotent")

        // ── case 5: resolvedToken — valid token ──────────────────────────────
        val manager = SnapshotManager()
        with(manager) {
            val gitValid = GitConfig(token = "ghp_valid")
            check(gitValid.resolvedToken() == "ghp_valid") { "FAIL case 5: resolvedToken should return the token" }
            logger.lifecycle("✅ case 5 OK — resolvedToken returns valid token")

            // ── case 6: resolvedToken — blank token ──────────────────────────
            val gitBlank = GitConfig(token = "")
            try {
                gitBlank.resolvedToken()
                error("FAIL case 6: resolvedToken should have thrown")
            } catch (_: IllegalStateException) {
                logger.lifecycle("✅ case 6 OK — resolvedToken throws on blank token")
            }

            // ── case 7: resolvedToken — placeholder token ─────────────────────
            val gitPlaceholder = GitConfig(token = "<YOUR_GITHUB_PAT>")
            try {
                gitPlaceholder.resolvedToken()
                error("FAIL case 7: resolvedToken should have thrown")
            } catch (_: IllegalStateException) {
                logger.lifecycle("✅ case 7 OK — resolvedToken throws on placeholder token")
            }
        }

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ verifyReadMeToAnonymizedYaml — all cases passed")
    }
}

/**
 * Verifies via logs that SliderConfiguration anonymization masks all sensitive fields
 * and preserves all non-sensitive ones.
 *
 * Usage: ./gradlew verifySliderToAnonymizedYaml
 */
tasks.register("verifySliderToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that SliderConfiguration anonymization masks sensitive fields"

    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = SliderYmlAnonymizer()

        // ── case 1: real credentials ─────────────────────────────────────────
        val realPassword = "ghp_EkAxg8TgUFBT2ihx8QH6vn0I6k4T"
        val realUsername = "cheroliv"
        val realRepo = "https://github.com/foo/bar.git"
        val realBranch = "production"

        val configReal = SliderConfiguration(
            srcPath = "docs/asciidocRevealJs",
            pushSlider = GitPushConfiguration(
                from = "/docs/asciidocRevealJs",
                to = "cvs",
                branch = realBranch,
                message = "slides show",
                repo = RepositoryConfiguration(
                    name = "slider-gradle",
                    repository = realRepo,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword)
                )
            ),
            ai = AiConfiguration(
                gemini = listOf("FAKE-gemini-key-for-test-only"),
                huggingface = listOf("FAKE-hf-key-for-test-only"),
                mistral = listOf("FAKE-mistral-key-1", "FAKE-mistral-key-2")
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: real credentials ────────────────────────")
        logger.lifecycle(yamlReal)

        check(realPassword !in yamlReal) { "FAIL case 1: real password is visible!" }
        check(realUsername !in yamlReal) { "FAIL case 1: real username is visible!" }
        check(realRepo !in yamlReal) { "FAIL case 1: real repo is visible!" }
        check(realBranch !in yamlReal) { "FAIL case 1: real branch is visible!" }
        check("FAKE-gemini-key-for-test-only" !in yamlReal) { "FAIL case 1: real gemini key is visible!" }
        check("FAKE-hf-key-for-test-only" !in yamlReal) { "FAIL case 1: real huggingface key is visible!" }
        check("FAKE-mistral-key-1" !in yamlReal) { "FAIL case 1: real mistral key is visible!" }
        check("FAKE-mistral-key-2" !in yamlReal) { "FAIL case 1: real mistral key 2 is visible!" }
        check(anonymizer.TOKEN_MASK in yamlReal) { "FAIL case 1: token mask not found" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1: anonymous username not found" }
        check(anonymizer.REPO_MASK in yamlReal) { "FAIL case 1: repo mask not found" }
        check(anonymizer.BRANCH_MASK in yamlReal) { "FAIL case 1: branch mask not found" }
        check("docs/asciidocRevealJs" in yamlReal) { "FAIL case 1: srcPath lost" }
        check("slides show" in yamlReal) { "FAIL case 1: message lost" }
        check("slider-gradle" in yamlReal) { "FAIL case 1: repo name lost" }

        logger.lifecycle("✅ case 1 OK — all sensitive fields masked, non-sensitive fields preserved")

        // ── case 2: empty AI lists ────────────────────────────────────────────
        val configEmptyAi = SliderConfiguration(
            pushSlider = GitPushConfiguration(
                from = "", to = "", branch = "feature", message = "",
                repo = RepositoryConfiguration(
                    name = "", repository = "https://github.com/real/repo.git",
                    credentials = RepositoryCredentials(username = "cheroliv-bot", password = "ghp_emptyAiSecret99")
                )
            ),
            ai = AiConfiguration()
        )
        val yamlEmptyAi = with(anonymizer) { configEmptyAi.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: empty AI lists ──────────────────────────")
        logger.lifecycle(yamlEmptyAi)

        check("https://github.com/real/repo.git" !in yamlEmptyAi) { "FAIL case 2: real repo is visible!" }
        check("cheroliv-bot" !in yamlEmptyAi) { "FAIL case 2: real username is visible!" }
        check("ghp_emptyAiSecret99" !in yamlEmptyAi) { "FAIL case 2: real password is visible!" }
        check(anonymizer.REPO_MASK in yamlEmptyAi) { "FAIL case 2: repo mask not found" }

        logger.lifecycle("✅ case 2 OK — empty AI lists handled correctly")

        // ── case 3: null pushSlider and null ai ───────────────────────────────
        val configNulls = SliderConfiguration(srcPath = "src/slides")
        val yamlNulls = with(anonymizer) { configNulls.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: null pushSlider and null ai ─────────────")
        logger.lifecycle(yamlNulls)

        check("src/slides" in yamlNulls) { "FAIL case 3: srcPath lost" }

        logger.lifecycle("✅ case 3 OK — null fields handled safely")

        // ── case 4: idempotency — original must not be mutated ────────────────
        val configIdempotent = SliderConfiguration(
            pushSlider = GitPushConfiguration(
                from = "", to = "", branch = "prod", message = "",
                repo = RepositoryConfiguration(
                    name = "", repository = "https://github.com/real/repo.git",
                    credentials = RepositoryCredentials(username = "realuser", password = "realpass")
                )
            ),
            ai = AiConfiguration(gemini = listOf("real-gemini-key"))
        )
        with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }
        val yaml2 = with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }

        check("realuser" !in yaml2) { "FAIL case 4: username visible on 2nd call — mutation detected!" }
        check("realpass" !in yaml2) { "FAIL case 4: password visible on 2nd call — mutation detected!" }
        check("real-gemini-key" !in yaml2) { "FAIL case 4: gemini key visible on 2nd call — mutation detected!" }
        check(anonymizer.TOKEN_MASK in yaml2) { "FAIL case 4: token mask not found on 2nd call" }
        check(anonymizer.ANONYMOUS_USERNAME in yaml2) { "FAIL case 4: anonymous username not found on 2nd call" }

        logger.lifecycle("✅ case 4 OK — original not mutated, idempotent")

        // ── case 5: multiple AI keys per provider ─────────────────────────────
        val configMultiKeys = SliderConfiguration(
            ai = AiConfiguration(
                gemini = listOf("key1", "key2", "key3"),
                huggingface = listOf("hf1", "hf2"),
                mistral = listOf("ms1")
            )
        )
        val yamlMultiKeys = with(anonymizer) { configMultiKeys.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 5: multiple AI keys per provider ───────────")
        logger.lifecycle(yamlMultiKeys)

        listOf("key1", "key2", "key3", "hf1", "hf2", "ms1").forEach { key ->
            check(key !in yamlMultiKeys) { "FAIL case 5: real key '$key' is visible!" }
        }
        val maskCount = yamlMultiKeys.split(anonymizer.TOKEN_MASK).size - 1
        check(maskCount == 6) { "FAIL case 5: expected 6 masked keys, got $maskCount" }

        logger.lifecycle("✅ case 5 OK — all $maskCount AI keys masked across providers")

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ verifySliderToAnonymizedYaml — all cases passed")
    }
}

/**
 * Verifies via logs that SiteConfiguration anonymization masks all sensitive fields
 * and preserves all non-sensitive ones.
 *
 * Usage: ./gradlew verifySiteToAnonymizedYaml
 */
tasks.register("verifySiteToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that SiteConfiguration anonymization masks sensitive fields"

    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = SiteYmlAnonymizer()

        // ── case 1: real credentials sur pushPage et pushMaquette ────────────
        val realPassword1 = "ghoS9WycCyJ8pX24Ge9l"
        val realPassword2 = "ghoFRVZTNo8OycCyJ8pX24Ge9l"
        val realUsername = "foo"
        val realRepo1 = "https://github.com/foo/bar.git"
        val realRepo2 = "https://github.com/baz/qux.git"
        val realBranch = "production"

        val configReal = SiteConfiguration(
            bake = BakeConfiguration(srcPath = "site", destDirPath = "bake"),
            pushPage = GitPushConfiguration(
                from = "bake",
                to = "cvs",
                branch = realBranch,
                message = "com.cheroliv.bakery",
                repo = RepositoryConfiguration(
                    name = "pages-content/bakery",
                    repository = realRepo1,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword1)
                )
            ),
            pushMaquette = GitPushConfiguration(
                from = "maquette",
                to = "cvs",
                branch = realBranch,
                message = "cheroliv-maquette",
                repo = RepositoryConfiguration(
                    name = "bakery-maquette",
                    repository = realRepo2,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword2)
                )
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: pushPage + pushMaquette ─────────────────")
        logger.lifecycle(yamlReal)

        check(realPassword1 !in yamlReal) { "FAIL case 1: real password1 is visible!" }
        check(realPassword2 !in yamlReal) { "FAIL case 1: real password2 is visible!" }
        check(realUsername !in yamlReal) { "FAIL case 1: real username is visible!" }
        check(realRepo1 !in yamlReal) { "FAIL case 1: real repo1 is visible!" }
        check(realRepo2 !in yamlReal) { "FAIL case 1: real repo2 is visible!" }
        check(anonymizer.TOKEN_MASK in yamlReal) { "FAIL case 1: token mask not found" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1: anonymous username not found" }
        check(anonymizer.REPO_MASK in yamlReal) { "FAIL case 1: repo mask not found" }
        check(anonymizer.BRANCH_MASK in yamlReal) { "FAIL case 1: branch mask not found" }
        check("site" in yamlReal) { "FAIL case 1: bake.srcPath lost" }
        check("bake" in yamlReal) { "FAIL case 1: bake.destDirPath lost" }
        check("com.cheroliv.bakery" in yamlReal) { "FAIL case 1: pushPage.message lost" }
        check("cheroliv-maquette" in yamlReal) { "FAIL case 1: pushMaquette.message lost" }
        check("pages-content/bakery" in yamlReal) { "FAIL case 1: pushPage.repo.name lost" }
        check("bakery-maquette" in yamlReal) { "FAIL case 1: pushMaquette.repo.name lost" }

        logger.lifecycle("✅ case 1 OK — pushPage + pushMaquette masked, non-sensitive fields preserved")

        // ── case 2: supabase url et publicKey ────────────────────────────────
        val realUrl = "https://hkgrvjgukx.supabase.co"
        val realPublicKey =
            "eyJhbCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhrZ3lubGZtamh2cXpydmpndWt4Iiwicm9sZSI6ImFub24ifQ"

        val configSupabase = SiteConfiguration(
            pushPage = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            supabase = SupabaseContactFormConfig(
                project = SupabaseProjectInfo(url = realUrl, publicKey = realPublicKey),
                schema = SupabaseDatabaseSchema(
                    contacts = SupabaseTable(
                        name = "public.contacts", rlsEnabled = true,
                        columns = listOf(
                            SupabaseColumn("id", "uuid"),
                            SupabaseColumn("created_at", "timestamptz"),
                            SupabaseColumn("name", "text"),
                            SupabaseColumn("email", "text"),
                            SupabaseColumn("telephone", "text")
                        )
                    ),
                    messages = SupabaseTable(
                        name = "public.messages", rlsEnabled = true,
                        columns = listOf(
                            SupabaseColumn("id", "uuid"),
                            SupabaseColumn("created_at", "timestamptz"),
                            SupabaseColumn("contact_id", "uuid"),
                            SupabaseColumn("subject", "text"),
                            SupabaseColumn("message", "text")
                        )
                    )
                ),
                rpc = SupabaseRpcFunction(
                    name = "public.handle_contact_form",
                    params = listOf(
                        SupabaseParam("p_name", "text"),
                        SupabaseParam("p_email", "text"),
                        SupabaseParam("p_subject", "text"),
                        SupabaseParam("p_message", "text")
                    )
                )
            )
        )
        val yamlSupabase = with(anonymizer) { configSupabase.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: supabase url + publicKey ────────────────")
        logger.lifecycle(yamlSupabase)

        check(realUrl !in yamlSupabase) { "FAIL case 2: real supabase url is visible!" }
        check(realPublicKey !in yamlSupabase) { "FAIL case 2: real publicKey is visible!" }
        check(anonymizer.URL_MASK in yamlSupabase) { "FAIL case 2: url mask not found" }
        check(anonymizer.TOKEN_MASK in yamlSupabase) { "FAIL case 2: token mask not found" }
        check("public.contacts" in yamlSupabase) { "FAIL case 2: schema.contacts.name lost" }
        check("public.messages" in yamlSupabase) { "FAIL case 2: schema.messages.name lost" }
        check("public.handle_contact_form" in yamlSupabase) { "FAIL case 2: rpc.name lost" }

        logger.lifecycle("✅ case 2 OK — supabase url + publicKey masked, schema + rpc preserved")

        // ── case 3: pushSource et pushTemplate nulls ─────────────────────────
        val configNullPush = SiteConfiguration(
            pushPage = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            pushSource = null,
            pushTemplate = null
        )
        val yamlNullPush = with(anonymizer) { configNullPush.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: pushSource + pushTemplate nulls ─────────")
        logger.lifecycle(yamlNullPush)

        logger.lifecycle("✅ case 3 OK — null push fields handled safely (no NPE)")

        // ── case 4: pushSource et pushTemplate non-nulls ──────────────────────
        val realRepo3 = "https://github.com/secret/source.git"
        val configAllPush = SiteConfiguration(
            pushPage = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            pushSource = GitPushConfiguration(
                branch = "secret-branch",
                repo = RepositoryConfiguration(
                    repository = realRepo3,
                    credentials = RepositoryCredentials(username = "src-user", password = "src-pass")
                )
            ),
            pushTemplate = GitPushConfiguration(
                branch = "template-branch",
                repo = RepositoryConfiguration(
                    repository = "https://github.com/secret/template.git",
                    credentials = RepositoryCredentials(username = "tmpl-user", password = "tmpl-pass")
                )
            )
        )
        val yamlAllPush = with(anonymizer) { configAllPush.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 4: pushSource + pushTemplate non-nulls ──────")
        logger.lifecycle(yamlAllPush)

        check(realRepo3 !in yamlAllPush) { "FAIL case 4: real repo3 is visible!" }
        check("secret-branch" !in yamlAllPush) { "FAIL case 4: secret-branch is visible!" }
        check("src-user" !in yamlAllPush) { "FAIL case 4: src-user is visible!" }
        check("https://github.com/secret/template.git" !in yamlAllPush) { "FAIL case 4: template repo is visible!" }
        check(anonymizer.REPO_MASK in yamlAllPush) { "FAIL case 4: repo mask not found" }

        logger.lifecycle("✅ case 4 OK — pushSource + pushTemplate masked")

        // ── case 5: idempotency ───────────────────────────────────────────────
        val configIdempotent = SiteConfiguration(
            pushPage = GitPushConfiguration(
                branch = "prod",
                repo = RepositoryConfiguration(
                    repository = "https://github.com/real/repo.git",
                    credentials = RepositoryCredentials(username = "idem-user", password = "idem-pass")
                )
            ),
            pushMaquette = GitPushConfiguration()
        )
        with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }
        val yaml2 = with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }

        check("idem-user" !in yaml2) { "FAIL case 5: username visible on 2nd call — mutation detected!" }
        check("idem-pass" !in yaml2) { "FAIL case 5: password visible on 2nd call — mutation detected!" }
        check(anonymizer.TOKEN_MASK in yaml2) { "FAIL case 5: token mask not found on 2nd call" }

        logger.lifecycle("✅ case 5 OK — original not mutated, idempotent")

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ verifySiteToAnonymizedYaml — all cases passed")
    }
}

/**
 * Verifies via logs that CodebaseConfiguration anonymization masks all API keys
 * and preserves all non-sensitive fields.
 *
 * Usage: ./gradlew verifyCodebaseToAnonymizedYaml
 */
tasks.register("verifyCodebaseToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that CodebaseConfiguration anonymization masks API keys"

    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = CodebaseYmlAnonymizer()

        // ── case 1: anthropic — clé réelle ───────────────────────────────────
        val realAnthropic = "sk-ant-api03-supersecret"
        val config1 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    defaultAccount = "perso",
                    defaultKey = "chatbot",
                    models = listOf("claude-opus-4-5", "claude-sonnet-4-5"),
                    defaultModel = "claude-opus-4-5",
                    accounts = listOf(
                        LlmAccount(
                            "perso", "p@gmail.com", listOf(
                                NamedApiKey("chatbot", realAnthropic, "2026-12-31")
                            )
                        )
                    )
                )
            )
        )
        val yaml1 = with(anonymizer) { config1.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: anthropic real key ──────────────────────")
        logger.lifecycle(yaml1)

        check(realAnthropic !in yaml1) { "FAIL case 1: real anthropic key is visible!" }
        check(anonymizer.TOKEN_MASK in yaml1) { "FAIL case 1: token mask not found" }
        check("perso" in yaml1) { "FAIL case 1: account name lost" }
        check("p@gmail.com" in yaml1) { "FAIL case 1: account email lost" }
        check("chatbot" in yaml1) { "FAIL case 1: key label lost" }
        check("2026-12-31" in yaml1) { "FAIL case 1: expiresAt lost" }
        check("claude-opus-4-5" in yaml1) { "FAIL case 1: model lost" }

        logger.lifecycle("✅ case 1 OK — anthropic key masked, metadata preserved")

        // ── case 2: tous les providers sauf ollama ────────────────────────────
        val realGemini = "FAKE-gemini-AIzaSy-test"
        val realHf = "FAKE-hf-token-test"
        val realMistral = "FAKE-mistral-key-test"
        val realGrok = "FAKE-grok-key-test"
        val realGroq = "FAKE-groq-gsk-test"

        val config2 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                gemini = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "g@gmail.com", listOf(
                                NamedApiKey("main", realGemini, "2026-12-31")
                            )
                        )
                    )
                ),
                huggingface = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "hf@gmail.com", listOf(
                                NamedApiKey("main", realHf, "")
                            )
                        )
                    )
                ),
                mistral = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "ms@gmail.com", listOf(
                                NamedApiKey("main", realMistral, "2027-03-01"),
                                NamedApiKey("spare", "mst-spare-key", "")
                            )
                        )
                    )
                ),
                grok = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount("perso", "grok@x.com", listOf(NamedApiKey("main", realGrok, "")))
                    )
                ),
                groq = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount("perso", "groq@groq.com", listOf(NamedApiKey("main", realGroq, "")))
                    )
                )
            )
        )
        val yaml2 = with(anonymizer) { config2.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: gemini + huggingface + mistral + grok + groq")
        logger.lifecycle(yaml2)

        listOf(realGemini, realHf, realMistral, realGrok, realGroq, "mst-spare-key").forEach { k ->
            check(k !in yaml2) { "FAIL case 2: real key '$k' is visible!" }
        }
        check("g@gmail.com" in yaml2) { "FAIL case 2: gemini email lost" }
        check("2026-12-31" in yaml2) { "FAIL case 2: expiresAt lost" }
        check("spare" in yaml2) { "FAIL case 2: spare label lost" }

        logger.lifecycle("✅ case 2 OK — all providers masked, metadata preserved")

        // ── case 3: ollama — baseUrl non masquée, pas de clés ─────────────────
        val config3 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                ollama = LlmProviderConfig(
                    baseUrl = "http://localhost:11434",
                    models = listOf("llama3.2", "codellama"),
                    accounts = listOf(LlmAccount("local", "", emptyList()))
                )
            )
        )
        val yaml3 = with(anonymizer) { config3.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: ollama baseUrl non masquée ──────────────")
        logger.lifecycle(yaml3)

        check("http://localhost:11434" in yaml3) { "FAIL case 3: ollama baseUrl was masked!" }
        check("llama3.2" in yaml3) { "FAIL case 3: ollama model lost" }
        check("codellama" in yaml3) { "FAIL case 3: ollama model codellama lost" }

        logger.lifecycle("✅ case 3 OK — ollama baseUrl preserved (not a secret)")

        // ── case 4: clés vides → pas de masque ───────────────────────────────
        val config4 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount("perso", "p@g.com", listOf(NamedApiKey("main", "", "")))
                    )
                )
            )
        )
        val yaml4 = with(anonymizer) { config4.toAnonymizedYaml(mapper) }
        val maskCount = yaml4.lines().count { anonymizer.TOKEN_MASK in it }

        logger.lifecycle("── case 4: clés vides — pas de masque ──────────────")
        logger.lifecycle(yaml4)

        check(maskCount == 0) { "FAIL case 4: expected 0 masks for blank keys, got $maskCount" }

        logger.lifecycle("✅ case 4 OK — blank keys produce no spurious masks")

        // ── case 5: active + chatbot préservés ───────────────────────────────
        val config5 = CodebaseConfiguration(
            active = ActiveSelection(provider = "mistral", account = "pro", key = "prod"),
            chatbot = ChatbotConfig(port = 9090, defaultProvider = "mistral")
        )
        val yaml5 = with(anonymizer) { config5.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 5: active + chatbot préservés ──────────────")
        logger.lifecycle(yaml5)

        check("mistral" in yaml5) { "FAIL case 5: active.provider lost" }
        check("pro" in yaml5) { "FAIL case 5: active.account lost" }
        check("prod" in yaml5) { "FAIL case 5: active.key lost" }
        check("9090" in yaml5) { "FAIL case 5: chatbot.port lost" }

        logger.lifecycle("✅ case 5 OK — active + chatbot config preserved")

        // ── case 6: idempotency — original must not be mutated ────────────────
        val config6 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "p@g.com", listOf(
                                NamedApiKey("chatbot", "sk-ant-idempotent", "2027-01-01")
                            )
                        )
                    )
                ),
                gemini = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "g@g.com", listOf(
                                NamedApiKey("main", "AIza-idempotent", "")
                            )
                        )
                    )
                )
            )
        )
        with(anonymizer) { config6.toAnonymizedYaml(mapper) }
        val yaml6b = with(anonymizer) { config6.toAnonymizedYaml(mapper) }

        check("sk-ant-idempotent" !in yaml6b) { "FAIL case 6: anthropic key visible on 2nd call — mutation!" }
        check("AIza-idempotent" !in yaml6b) { "FAIL case 6: gemini key visible on 2nd call — mutation!" }
        check(anonymizer.TOKEN_MASK in yaml6b) { "FAIL case 6: token mask not found on 2nd call" }

        logger.lifecycle("✅ case 6 OK — original not mutated, idempotent")

        // ── case 7: resolveActiveKey — sélection via active + CLI override ────
        val localConfig = CodebaseYmlConfig()
        val config7 = CodebaseConfiguration(
            active = ActiveSelection(provider = "anthropic", account = "perso", key = "chatbot"),
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    defaultAccount = "perso",
                    defaultKey = "chatbot",
                    accounts = listOf(
                        LlmAccount(
                            "perso", "p@g.com", listOf(
                                NamedApiKey("chatbot", "sk-ant-chatbot", "2027-06-01"),
                                NamedApiKey("ci", "sk-ant-ci", "")
                            )
                        ),
                        LlmAccount(
                            "pro", "pro@c.com", listOf(
                                NamedApiKey("prod", "sk-ant-prod", "2025-01-01") // expirée
                            )
                        )
                    )
                )
            )
        )

        val resolved = localConfig.resolveActiveKey(config7, logger)
        check(resolved?.key == "sk-ant-chatbot") { "FAIL case 7: expected chatbot key, got ${resolved?.key}" }
        check(resolved.label == "chatbot") { "FAIL case 7: expected label 'chatbot', got ${resolved?.label}" }

        val resolvedCli = localConfig.resolveActiveKey(
            config7, logger,
            cliProvider = "anthropic", cliAccount = "pro", cliKey = "prod"
        )
        check(resolvedCli?.key == "sk-ant-prod") {
            "FAIL case 7: CLI override failed, got ${resolvedCli?.key}"
        }

        val resolvedCliKey = localConfig.resolveActiveKey(
            config7, logger,
            cliKey = "ci"
        )
        check(resolvedCliKey?.key == "sk-ant-ci") { "FAIL case 7: CLI key override failed, got ${resolvedCliKey?.key}" }

        logger.lifecycle("✅ case 7 OK — resolveActiveKey: active, CLI override, expired key warning")

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ verifyCodebaseToAnonymizedYaml — all cases passed")
    }
}

/**
 * Scaffolds codebase.yml with empty fields and registers it in .gitignore.
 *
 * - Warning + skip if codebase.yml already exists (no overwrite).
 * - Appends codebase.yml to .gitignore if not already present.
 *
 * Usage: ./gradlew scaffoldCodebaseYml
 */
tasks.register("scaffoldCodebaseYml") {
    group = "codebase"
    description = "Generates codebase.yml scaffold and registers it in .gitignore"

    doLast {
        val projectDir = layout.projectDirectory.asFile
        val target = projectDir.resolve("codebase.yml")

        if (target.exists()) {
            logger.warn("⚠️  codebase.yml already exists — scaffold skipped (delete it manually to regenerate)")
            return@doLast
        }

        val scaffold = """
        # codebase.yml — LLM provider credentials and chatbot configuration
        # ⚠️  This file contains secrets. It is listed in .gitignore — NEVER commit it.
        #
        # Structure:
        #   ai.<provider>.accounts[]  → liste de comptes (name + email)
        #   account.keys[]            → liste de clés nommées (label + key + expiresAt)
        #   active                    → sélection active globale
        #
        # CLI override: ./gradlew <task> -Pcodebase.provider=gemini -Pcodebase.account=pro -Pcodebase.key=prod

        active:
          provider: anthropic
          account: ""
          key: ""

        ai:
          anthropic:
            defaultAccount: ""
            defaultKey: ""
            models:
              - claude-opus-4-5
              - claude-sonnet-4-5
              - claude-haiku-4-5
            defaultModel: claude-opus-4-5
            accounts:
              - name: ""
                email: ""
                keys:
                  - label: ""
                    key: ""
                    expiresAt: ""

          gemini:
            defaultAccount: ""
            defaultKey: ""
            models:
              - gemini-2.5-pro
              - gemini-2.5-flash
            defaultModel: gemini-2.5-pro
            accounts:
              - name: ""
                email: ""
                keys:
                  - label: ""
                    key: ""
                    expiresAt: ""

          huggingface:
            defaultAccount: ""
            defaultKey: ""
            models:
              - mistralai/Mistral-7B-Instruct-v0.3
              - meta-llama/Llama-3.1-8B-Instruct
            defaultModel: mistralai/Mistral-7B-Instruct-v0.3
            accounts:
              - name: ""
                email: ""
                keys:
                  - label: ""
                    key: ""
                    expiresAt: ""

          mistral:
            defaultAccount: ""
            defaultKey: ""
            models:
              - mistral-large-latest
              - mistral-small-latest
              - codestral-latest
            defaultModel: mistral-large-latest
            accounts:
              - name: ""
                email: ""
                keys:
                  - label: ""
                    key: ""
                    expiresAt: ""

          ollama:
            baseUrl: "http://localhost:11434"
            models:
              - llama3.2
              - codellama
              - mistral
            defaultModel: llama3.2
            accounts:
              - name: local
                email: ""
                keys: []

          grok:
            defaultAccount: ""
            defaultKey: ""
            models:
              - grok-3
              - grok-3-mini
            defaultModel: grok-3
            accounts:
              - name: ""
                email: ""
                keys:
                  - label: ""
                    key: ""
                    expiresAt: ""

          groq:
            defaultAccount: ""
            defaultKey: ""
            models:
              - llama-3.3-70b-versatile
              - llama-3.1-8b-instant
              - mixtral-8x7b-32768
            defaultModel: llama-3.3-70b-versatile
            accounts:
              - name: ""
                email: ""
                keys:
                  - label: ""
                    key: ""
                    expiresAt: ""

        chatbot:
          port: 7070
          defaultProvider: anthropic
    """.trimIndent()

        target.writeText(scaffold)
        logger.lifecycle("✅ codebase.yml generated at ${target.absolutePath}")

        val gitignore = projectDir.resolve(".gitignore")
        val entry = "codebase.yml"

        when {
            !gitignore.exists() -> {
                gitignore.writeText("# codebase — LLM secrets\n$entry\n")
                logger.lifecycle("✅ .gitignore created with entry '$entry'")
            }

            gitignore.readLines().none { it.trim() == entry } -> {
                gitignore.appendText("\n# codebase — LLM secrets\n$entry\n")
                logger.lifecycle("✅ '$entry' appended to existing .gitignore")
            }

            else ->
                logger.lifecycle("ℹ️  '$entry' already present in .gitignore")
        }
    }
}

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
) {
    companion object
}

/**
 * Loader pour embeds.yml — même pattern que les autres YmlConfig.
 * Fallback sur liste vide si le fichier est absent.
 */
class EmbedsYmlConfig {
    @Suppress("PrivatePropertyName")
    private val MAPPER = ObjectMapper(YAMLFactory()).registerKotlinModule()

    @Suppress("PropertyName")
    val CONFIG_FILE_NAME = "embeds.yml"

    fun EmbedsConfig.Companion.load(projectDir: File): EmbedsConfig {
        val f = File(projectDir, CONFIG_FILE_NAME)
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

// ── setupSidebar ──────────────────────────────────────────────────────────────
//
// Sidebar commune aux deux vues.
// Rendu dans Jt.SIDEBAR — appelée en tête de chatbotApp, avant le dispatch.
// "New Chat" et les liens de navigation sont statiques (Javelit ne supporte
// pas de routing multi-page fiable sans switchPage).
//
fun setupSidebar() {
    val sidebar = Jt.SIDEBAR
    val state = Jt.sessionState()

    // ── Logo ──────────────────────────────────────────────────────────────────
    Jt.html(
        """
        <div style="padding:18px 14px 10px 14px;">
          <div style="display:flex;align-items:center;gap:10px;">
            <div style="background:#1a3a6b;border-radius:8px;width:34px;height:34px;
                display:flex;align-items:center;justify-content:center;
                color:white;font-size:1.1em;font-weight:700;">✦</div>
            <span style="font-weight:700;font-size:1.05em;color:#1a1a2e;
                letter-spacing:0.01em;">Assistant</span>
          </div>
        </div>
    """.trimIndent()
    ).use(sidebar)

    // ── New Conversation — form actif ─────────────────────────────────────────
    val newConvForm = Jt.form().key("sidebar-new-conv").use(sidebar)
    Jt.formSubmitButton("➕ New Conversation").use(newConvForm)

    Jt.markdown("---").use(sidebar)

    // ── Navigation statique ───────────────────────────────────────────────────
    // "New Chat" surligné si page == "chat"
    val currentPage = state.getString("page") ?: "chat"
    val newChatBg = if (currentPage == "chat") "#e8f0fe" else "transparent"
    Jt.html(
        """
        <div style="background:$newChatBg;border-radius:6px;padding:7px 10px;
            margin:2px 0;display:flex;align-items:center;gap:8px;
            font-size:0.93em;color:#1a1a2e;cursor:default;">
          <span style="color:#1a3a6b;font-weight:600;">＋</span> New Chat
        </div>
        <div style="border-radius:6px;padding:7px 10px;margin:2px 0;
            display:flex;align-items:center;gap:8px;
            font-size:0.93em;color:#444;cursor:default;">
          🕐 History
        </div>
        <div style="border-radius:6px;padding:7px 10px;margin:2px 0;
            display:flex;align-items:center;gap:8px;
            font-size:0.93em;color:#444;cursor:default;">
          📖 Knowledge Base
        </div>
        <div style="border-radius:6px;padding:7px 10px;margin:2px 0;
            display:flex;align-items:center;gap:8px;
            font-size:0.93em;color:#444;cursor:default;">
          ⚙️ Settings
        </div>
    """.trimIndent()
    ).use(sidebar)

    // ── Handler New Conversation ──────────────────────────────────────────────
    if (Jt.componentsState()["sidebar-new-conv"] == true) {
        @Suppress("UNCHECKED_CAST")
        val history = state["history"] as? MutableList<Triple<String, String, String>>
        history?.clear()
        state["page"] = "chat"
        state["pendingMessage"] = ""
        state["wordCount"] = 0
        state["pendingToolCall"] = ""
        state["toolCallArgs"] = ""
        state["searchOpen"] = false
        state["searchTerm"] = ""
        state["searchIndex"] = 0
        state["enrichRawPrompt"] = ""
        state["enrichedPrompt"] = ""
        state["enrichEmbeds"] = mutableListOf<String>()
        state["playwrightStatus"] = "idle"
        Jt.rerun()
    }
}

// ── chatbotApp ────────────────────────────────────────────────────────────────
//
// Deux vues pilotées par sessionState["page"] :
//   "chat"   → vue conversation principale
//   "enrich" → vue enrichissement du prompt
//
// Règles Javelit : pas de switchPage — rendu conditionnel if/else sur "page".
//
fun chatbotApp(
    cfg: CodebaseConfiguration,
    providerName: String
) {
    val state = Jt.sessionState()

    // ── Session state — init ──────────────────────────────────────────────────
    state.putIfAbsent("history", mutableListOf<Triple<String, String, String>>())
    state.putIfAbsent("page", "chat")
    state.putIfAbsent("pendingMessage", "")
    state.putIfAbsent("wordCount", 0)
    state.putIfAbsent("playwrightStatus", "idle")
    state.putIfAbsent("pendingToolCall", "")
    state.putIfAbsent("toolCallArgs", "")
    state.putIfAbsent("searchOpen", false)
    state.putIfAbsent("searchTerm", "")
    state.putIfAbsent("searchIndex", 0)
    state.putIfAbsent("enrichRawPrompt", "")
    state.putIfAbsent("enrichedPrompt", "")
    state.putIfAbsent("enrichProvider", "ollama")
    state.putIfAbsent("enrichModel", "")
    state.putIfAbsent("enrichEmbeds", mutableListOf<String>())

    @Suppress("UNCHECKED_CAST")
    val history = state.get("history") as MutableList<Triple<String, String, String>>

    val currentPage = state.getString("page") ?: "chat"

    val models = availableModels(cfg, providerName)
    val defaultModel = models.firstOrNull() ?: "no-model"
    state.putIfAbsent("selectedModel", defaultModel)
    val currentModel = state.getString("selectedModel") ?: defaultModel
    val currentIndex = models.indexOf(currentModel).takeIf { it >= 0 } ?: 0

    val busy = (state.getString("playwrightStatus") ?: "idle") == "waiting"

    // ── Sidebar — rendue avant le dispatch ────────────────────────────────────
    setupSidebar()

    // ── Dispatch vue ──────────────────────────────────────────────────────────
    if (currentPage == "enrich") {
        enrichView(cfg, history)
        return
    }

    // ════════════════════════════════════════════════════════════════════════
    // VUE "chat"
    // ════════════════════════════════════════════════════════════════════════

    val searchOpen = state["searchOpen"] as? Boolean ?: false
    val searchTerm = state.getString("searchTerm") ?: ""
    val searchIndex = (state["searchIndex"] as? Int) ?: 0

    // ── Header ────────────────────────────────────────────────────────────────
    val headerBar = Jt.columns(2)
        .widths(listOf(0.85, 0.15))
        .gap(ColumnsComponent.Gap.NONE)
        .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
        .use()

    Jt.html(
        """
        <div style="display:flex;align-items:center;gap:12px;padding:6px 0;">
          <span style="font-size:1.5em;font-weight:700;color:#1a1a2e;">Chat</span>
          <span style="background:#e8f0fe;border-radius:12px;padding:3px 12px;
              font-size:0.82em;font-weight:600;color:#1a3a6b;white-space:nowrap;">
            ● $currentModel
          </span>
        </div>
    """.trimIndent()
    ).use(headerBar.col(0))

    val searchClicked = Jt.button("🔍").key("search-toggle").use(headerBar.col(1))
    if (searchClicked) {
        state["searchOpen"] = !searchOpen
        Jt.rerun()
    }

    Jt.markdown("---").use()

    // ── Bloc recherche — conditionnel ─────────────────────────────────────────
    val searchContainer = Jt.container().key("search-container").use()
    if (searchOpen) {
        val searchForm = Jt.form().key("search-form").use(searchContainer)
        val searchInputBar = Jt.columns(2)
            .widths(listOf(0.88, 0.12))
            .gap(ColumnsComponent.Gap.SMALL)
            .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
            .use(searchForm)

        val typedTerm = Jt.textInput("Rechercher")
            .placeholder("Terme de recherche…")
            .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
            .value(searchTerm)
            .use(searchInputBar.col(0))

        Jt.formSubmitButton("🔍").use(searchInputBar.col(1))

        if (Jt.componentsState()["search-form"] == true) {
            state["searchTerm"] = typedTerm.trim()
            state["searchIndex"] = 0
            Jt.rerun()
        }

        if (searchTerm.isNotBlank()) {
            data class Hit(val msgIndex: Int, val role: String, val content: String)

            val hits = history.mapIndexedNotNull { i, (role, content, _) ->
                if (content.contains(searchTerm, ignoreCase = true))
                    Hit(i, role, content) else null
            }
            val total = hits.size
            val safeIndex = if (total == 0) 0 else searchIndex.coerceIn(0, total - 1)

            if (total == 0) {
                Jt.warning("Aucune occurrence pour « $searchTerm »").use(searchContainer)
            } else {
                val navForm = Jt.form().key("search-nav-form").use(searchContainer)
                val navBar = Jt.columns(3)
                    .widths(listOf(0.12, 0.76, 0.12))
                    .gap(ColumnsComponent.Gap.NONE)
                    .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
                    .use(navForm)

                Jt.formSubmitButton("◀").key("search-prev").use(navBar.col(0))
                Jt.markdown("${safeIndex + 1} / $total  · « **$searchTerm** »")
                    .use(navBar.col(1))
                Jt.formSubmitButton("▶").key("search-next").use(navBar.col(2))

                val navCs = Jt.componentsState()
                if (navCs["search-prev"] == true) {
                    state["searchIndex"] = (safeIndex - 1 + total) % total; Jt.rerun()
                }
                if (navCs["search-next"] == true) {
                    state["searchIndex"] = (safeIndex + 1) % total; Jt.rerun()
                }

                Jt.markdown("---").use(searchContainer)

                hits.forEachIndexed { hitIdx, hit ->
                    val isCurrent = hitIdx == safeIndex
                    val bgColor = if (hit.role == "👤") "#e8f4fd" else "#f8f9ff"
                    val border = if (isCurrent) "2px solid #FF9800" else "1px solid #ddd"
                    val shadow = if (isCurrent) "box-shadow:0 0 0 2px #FF980044;" else ""
                    val highlighted = hit.content.replace(
                        Regex("(?i)(${Regex.escape(searchTerm)})"),
                        "<mark style=\"background:#FFF176;border-radius:2px;\">$1</mark>"
                    )
                    Jt.html(
                        """
                        <div style="background:$bgColor;border:$border;border-radius:6px;
                            padding:8px 12px;margin:4px 0;font-size:0.91em;$shadow">
                          <strong>${hit.role}</strong>
                          ${if (isCurrent) " <span style=\"color:#FF9800;font-size:0.83em;\">◀ occ.${hitIdx + 1}</span>" else ""}
                          <br/>${highlighted.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use(searchContainer)
                }
            }
        }
        Jt.markdown("---").use()
    }

    // ── Historique ────────────────────────────────────────────────────────────
    if (history.isEmpty()) {
        Jt.html(
            """
            <div style="text-align:center;color:#bdbdbd;padding:56px 0 40px 0;
                font-size:0.97em;">
              Démarrez la conversation…
            </div>
        """.trimIndent()
        ).use()
    } else {
        history.forEachIndexed { i, (_, content, type) ->
            when (type) {
                "user" -> {
                    Jt.html(
                        """
                        <div style="display:flex;justify-content:flex-end;margin:6px 0;">
                          <div style="background:#ffffff;border-left:4px solid #1a3a6b;
                              border-radius:6px;padding:10px 14px;max-width:82%;
                              font-size:0.94em;
                              box-shadow:0 1px 3px rgba(0,0,0,0.07);
                              color:#1a1a2e;">
                            ${content.replace("\n", "<br/>")}
                          </div>
                        </div>
                    """.trimIndent()
                    ).use()
                }

                "wait" -> {
                    Jt.html(
                        """
                        <div style="background:#fff8e1;border-left:4px solid #FF9800;
                            border-radius:6px;padding:10px 14px;margin:6px 0;
                            font-size:0.94em;color:#7c6400;">
                          ⏳ ${content.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use()
                }

                "tool" -> {
                    Jt.html(
                        """
                        <div style="background:#fce4ec;border-left:4px solid #E91E63;
                            border-radius:6px;padding:10px 14px;margin:6px 0;
                            font-size:0.93em;color:#880e4f;">
                          <strong>🔧 Tool</strong><br/>
                          ${content.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use()
                }

                "system" -> {
                    Jt.html(
                        """
                        <div style="background:#f5f5f5;border-left:4px solid #9E9E9E;
                            border-radius:6px;padding:8px 14px;margin:6px 0;
                            font-size:0.88em;color:#616161;">
                          ${content.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use()
                }

                else -> {
                    // "assistant" | "ollama"
                    val msgBar = Jt.columns(2)
                        .widths(listOf(0.86, 0.14))
                        .gap(ColumnsComponent.Gap.SMALL)
                        .verticalAlignment(ColumnsComponent.VerticalAlignment.TOP)
                        .use()

                    Jt.html(
                        """
                        <div style="background:#f8f9ff;border-left:4px solid #1a3a6b;
                            border-radius:6px;padding:14px 16px;margin:4px 0;
                            font-size:0.94em;color:#1a1a2e;">
                          <div style="display:flex;align-items:center;gap:6px;
                              margin-bottom:8px;color:#1a3a6b;font-size:0.78em;
                              font-weight:700;text-transform:uppercase;
                              letter-spacing:0.06em;">
                            <span>✦</span> ASSISTANT INSIGHT
                          </div>
                          ${content.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use(msgBar.col(0))

                    val enrichMsgForm = Jt.form().key("enrich-msg-$i").use(msgBar.col(1))
                    Jt.formSubmitButton("⚡ Enrich").use(enrichMsgForm)

                    if (Jt.componentsState()["enrich-msg-$i"] == true) {
                        state["enrichRawPrompt"] = content
                        state["enrichedPrompt"] = content
                        state["page"] = "enrich"
                        Jt.rerun()
                    }
                }
            }
        }
    }

    Jt.markdown("---").use()

    // ── Bloc tool call — conditionnel ─────────────────────────────────────────
    val pendingTool = state.getString("pendingToolCall") ?: ""
    if (pendingTool.isNotBlank()) {
        Jt.markdown("🔧 **Tool call détecté** : `$pendingTool`").use()
        Jt.textInput("Arguments")
            .value(state.getString("toolCallArgs") ?: "")
            .onChange { state["toolCallArgs"] = it }
            .use()

        val toolBar = Jt.columns(2)
            .widths(listOf(0.50, 0.50))
            .gap(ColumnsComponent.Gap.SMALL)
            .use()

        val toolAllowForm = Jt.form().key("tool-allow-form").use(toolBar.col(0))
        Jt.formSubmitButton("✅ Autoriser").use(toolAllowForm)
        val toolDenyForm = Jt.form().key("tool-deny-form").use(toolBar.col(1))
        Jt.formSubmitButton("❌ Refuser").use(toolDenyForm)

        val toolCs = Jt.componentsState()
        if (toolCs["tool-allow-form"] == true) {
            state["pendingToolCall"] = ""
            Jt.rerun()
        }
        if (toolCs["tool-deny-form"] == true) {
            history.add(Triple("🔧", "Tool `$pendingTool` refusé.", "system"))
            state["pendingToolCall"] = ""
            Jt.rerun()
        }
        Jt.markdown("---").use()
    }

    // ── Barre de saisie ───────────────────────────────────────────────────────
    //
    // ARCHITECTURE : un seul form "input-form" englobe les deux boutons ET le
    // textArea. Deux formSubmitButton avec clés distinctes ("enrich-submit" /
    // "direct-submit") permettent de discriminer l'action dans componentsState.
    //
    // Raison : textArea hors form → onChange non garanti au même cycle que le
    // submit → pendingMessage lu dans les handlers = valeur N-1 (cycle précédent).
    // Avec textArea dans le form → use() retourne la valeur courante (String) ✅
    //
    val inputGroup = Jt.container().key("input-group").use()

    val wordCount = (state["wordCount"] as? Int) ?: 0

    // ── Ligne 1 : expander modèle ─────────────────────────────────────────────
    val modelExpander = Jt.expander("$currentModel ▾")
        .expanded(false)
        .use(inputGroup)
    Jt.markdown("#### Modèles disponibles").use(modelExpander)
    Jt.markdown("---").use(modelExpander)
    val pickedModel = Jt.radio("Modèle", models)
        .index(currentIndex)
        .labelVisibility(JtComponent.LabelVisibility.COLLAPSED)
        .use(modelExpander)
    if (pickedModel != currentModel) {
        state["selectedModel"] = pickedModel; Jt.rerun()
    }
    Jt.markdown("---").use(modelExpander)
    Jt.button("➕ Ajouter un provider / modèle").use(modelExpander)

    // ── Lignes 2 + 3 : form unique englobant boutons + textArea ──────────────
    val inputForm = Jt.form().key("input-form").use(inputGroup)

    val btnBar = Jt.columns(3)
        .widths(listOf(0.15, 0.55, 0.30))
        .gap(ColumnsComponent.Gap.SMALL)
        .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
        .use(inputForm)

    // col(0) — 📁 attach statique (pas encore fonctionnel)
    Jt.html(
        """
        <div style="display:flex;align-items:center;justify-content:center;
            padding:4px 0;opacity:0.40;cursor:not-allowed;font-size:1.1em;"
             title="Attach (bientôt disponible)">📁</div>
    """.trimIndent()
    ).use(btnBar.col(0))

    // col(1) — ⚡ Enrich
    Jt.formSubmitButton("⚡ Enrich")
        .key("enrich-submit")
        .disabled(busy)
        .use(btnBar.col(1))

    // col(2) — ⬆️ Send direct
    Jt.formSubmitButton("⬆️")
        .key("direct-submit")
        .disabled(busy)
        .use(btnBar.col(2))

    // Compteur mots
    if (wordCount > 0) {
        Jt.html(
            """
            <span style="font-size:0.78em;color:#9e9e9e;padding:2px 0;
                display:block;">Mots : <strong>$wordCount</strong></span>
        """.trimIndent()
        ).use(inputForm)
    }

    // textArea dans le form → use() retourne String du cycle courant ✅
    val typedMessage: String = Jt.textArea("Message")
        .placeholder(if (busy) "En attente de la réponse…" else "Type your message here…")
        .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
        .height(120)
        .disabled(busy)
        .value(state.getString("pendingMessage") ?: "")
        .onChange { txt ->
            state["pendingMessage"] = txt
            state["wordCount"] = if (txt.isBlank()) 0 else txt.trim().split(Regex("\\s+")).size
        }
        .use(inputForm)

    // ── Handlers ──────────────────────────────────────────────────────────────
    val inputCs = Jt.componentsState()

    if (inputCs["enrich-submit"] == true) {
        val pending = typedMessage.trim()
        println("[DEBUG enrich-submit] fired — pending='$pending'")
        if (pending.isNotBlank()) history.add(Triple("👤", pending, "user"))
        state["enrichRawPrompt"] = pending
        state["enrichedPrompt"] = pending
        state["pendingMessage"] = ""
        state["wordCount"] = 0
        state["page"] = "enrich"
        Jt.rerun()
    }

    if (inputCs["direct-submit"] == true) {
        val pending = typedMessage.trim()
        println("[DEBUG direct-submit] fired — pending='$pending'")
        if (pending.isNotBlank()) {
            history.add(Triple("👤", pending, "user"))
            history.add(Triple("⏳", "En attente de Claude...", "wait"))
            state["playwrightStatus"] = "waiting"
            state["pendingMessage"] = ""
            state["wordCount"] = 0
            // TODO : envoyer via activePage (étape Playwright)
            Jt.rerun()
        }
    }
}

// ── enrichView ────────────────────────────────────────────────────────────────
//
// Vue d'enrichissement du prompt — rendu conditionnel depuis chatbotApp.
// Pilotée par sessionState["page"] == "enrich".
//
fun enrichView(
    cfg: CodebaseConfiguration,
    history: MutableList<Triple<String, String, String>>
) {
    val state = Jt.sessionState()
    val projectDir = File(System.getProperty("user.dir"))
    val rawPrompt = state.getString("enrichRawPrompt") ?: ""
    val enrichedPrompt = state.getString("enrichedPrompt") ?: rawPrompt

    // ── Header "← Back to Chat   Enrichir" ───────────────────────────────────
    val headerBar = Jt.columns(2)
        .widths(listOf(0.80, 0.20))
        .gap(ColumnsComponent.Gap.NONE)
        .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
        .use()

    Jt.html(
        """
        <div style="display:flex;align-items:center;gap:18px;padding:6px 0;">
          <span style="color:#1a3a6b;font-size:0.88em;font-weight:500;">
            ← Back to Chat
          </span>
          <span style="font-size:1.35em;font-weight:700;color:#1a1a2e;
              border-bottom:2px solid #1a3a6b;padding-bottom:2px;">
            Enrichir
          </span>
        </div>
    """.trimIndent()
    ).use(headerBar.col(0))

    val cancelHeaderForm = Jt.form().key("cancel-header-form").use(headerBar.col(1))
    Jt.formSubmitButton("← Back to Chat").use(cancelHeaderForm)

    Jt.markdown("---").use()

    // ── Bloc intro "Optimisation du Prompt" ───────────────────────────────────
    Jt.html(
        """
        <div style="background:#f8f9ff;border:1px solid #dde3f5;border-radius:8px;
            padding:16px 20px;margin:0 0 18px 0;">
          <div style="font-size:1.15em;font-weight:700;color:#1a1a2e;margin-bottom:5px;">
            Optimisation du Prompt
          </div>
          <div style="font-size:0.87em;color:#666;line-height:1.5;">
            Affinage de l'intelligence contextuelle pour des résultats de haute
            précision via le moteur Enrichir AI.
          </div>
        </div>
    """.trimIndent()
    ).use()

    // ── Deux colonnes : Language Model | Select Resources ────────────────────
    val allProviders = listOf(
        "anthropic", "gemini", "huggingface", "mistral", "ollama", "grok", "groq"
    )
    val currentEnrichProvider = state.getString("enrichProvider")
        ?.takeIf { it.isNotBlank() } ?: "ollama"
    val providerIndex = allProviders.indexOf(currentEnrichProvider)
        .takeIf { it >= 0 } ?: 4

    val enrichModels = availableModels(cfg, currentEnrichProvider)
        .ifEmpty { listOf("$currentEnrichProvider-default") }
    val currentEnrichModel = state.getString("enrichModel")
        ?.takeIf { it.isNotBlank() } ?: enrichModels.first()
    val enrichModelIndex = enrichModels.indexOf(currentEnrichModel)
        .takeIf { it >= 0 } ?: 0

    val twoColBar = Jt.columns(2)
        .widths(listOf(0.48, 0.52))
        .gap(ColumnsComponent.Gap.MEDIUM)
        .use()

    // ── col(0) Language Model ─────────────────────────────────────────────────
    Jt.html(
        """
        <div style="font-size:0.85em;font-weight:600;color:#1a3a6b;
            margin-bottom:8px;display:flex;align-items:center;gap:6px;">
          🌐 Language Model
        </div>
    """.trimIndent()
    ).use(twoColBar.col(0))

    val pickedProvider = Jt.selectbox("Provider", allProviders)
        .index(providerIndex)
        .labelVisibility(JtComponent.LabelVisibility.COLLAPSED)
        .width("stretch")
        .use(twoColBar.col(0))

    if (pickedProvider != currentEnrichProvider) {
        state["enrichProvider"] = pickedProvider
        state["enrichModel"] = ""
        Jt.rerun()
    }

    val pickedEnrichModel = Jt.selectbox("Modèle", enrichModels)
        .index(enrichModelIndex)
        .labelVisibility(JtComponent.LabelVisibility.COLLAPSED)
        .width("stretch")
        .use(twoColBar.col(0))

    if (pickedEnrichModel != currentEnrichModel) {
        state["enrichModel"] = pickedEnrichModel
        Jt.rerun()
    }

    Jt.html(
        """
        <div style="font-size:0.79em;color:#9e9e9e;margin-top:6px;line-height:1.4;">
          Selected model handles complex reasoning and creative generation.
        </div>
    """.trimIndent()
    ).use(twoColBar.col(0))

    // ── col(1) Select Resources (embeds RAG) ──────────────────────────────────
    val embedsCfg = with(embedsYmlConfig) { EmbedsConfig.load(projectDir) }
    val embedNames = embedsCfg.embeds.map { it.name }

    @Suppress("UNCHECKED_CAST")
    val selectedEmbeds = state["enrichEmbeds"] as? MutableList<String>
        ?: mutableListOf<String>().also { state["enrichEmbeds"] = it }

    Jt.html(
        """
        <div style="font-size:0.85em;font-weight:600;color:#1a3a6b;
            margin-bottom:8px;display:flex;align-items:center;gap:6px;">
          📂 Select Resources
        </div>
    """.trimIndent()
    ).use(twoColBar.col(1))

    // Tags pills des embeds sélectionnés
    if (selectedEmbeds.isNotEmpty()) {
        val tagsHtml = selectedEmbeds.joinToString("") { name ->
            val embed = embedsCfg.embeds.firstOrNull { it.name == name }
            val pathInfo = embed?.path ?: "?"
            """<span style="display:inline-flex;align-items:center;gap:4px;
                background:#f0f4ff;border:1px solid #c5cae9;border-radius:4px;
                padding:2px 8px;margin:2px 2px;font-size:0.81em;color:#1a3a6b;">
              📄 $pathInfo
            </span>"""
        }
        Jt.html("""<div style="margin-bottom:6px;flex-wrap:wrap;">$tagsHtml</div>""")
            .use(twoColBar.col(1))
    }

    if (embedNames.isEmpty()) {
        Jt.info("Aucun embed — créez embeds.yml à la racine.").use(twoColBar.col(1))
    } else {
        val embedIndex = selectedEmbeds
            .mapNotNull { name -> embedNames.indexOf(name).takeIf { it >= 0 } }
            .firstOrNull() ?: 0

        val pickedEmbed = Jt.selectbox("Embed RAG", embedNames)
            .index(embedIndex)
            .labelVisibility(JtComponent.LabelVisibility.COLLAPSED)
            .width("stretch")
            .use(twoColBar.col(1))

        val addEmbedForm = Jt.form().key("add-embed-form").use(twoColBar.col(1))
        Jt.formSubmitButton("＋ Attach File").use(addEmbedForm)

        if (Jt.componentsState()["add-embed-form"] == true) {
            if (pickedEmbed !in selectedEmbeds) {
                selectedEmbeds.add(pickedEmbed); Jt.rerun()
            }
        }

        selectedEmbeds.toList().forEachIndexed { i, name ->
            val removeForm = Jt.form().key("remove-embed-$i").use(twoColBar.col(1))
            Jt.formSubmitButton("✕ $name").use(removeForm)
            if (Jt.componentsState().get("remove-embed-$i") == true) {
                selectedEmbeds.remove(name); Jt.rerun()
            }
        }
    }

    Jt.markdown("---").use()

    // ── Prompt Editor ─────────────────────────────────────────────────────────
    //
    // Header avec toolbar B/I/<>/🔗 simulée en HTML statique + compteur mots.
    // Contexte injecté visible uniquement si enrichedPrompt ≠ rawPrompt.
    //
    val wordCount = enrichedPrompt.trim()
        .let { if (it.isBlank()) 0 else it.split(Regex("\\s+")).size }

    Jt.html(
        """
        <div style="background:#fff;border:1px solid #dde3f5;
            border-radius:8px 8px 0 0;padding:10px 16px;
            display:flex;align-items:center;justify-content:space-between;">
          <div style="display:flex;align-items:center;gap:8px;
              font-weight:600;font-size:0.92em;color:#1a1a2e;">
            <span style="color:#e65100;font-size:1.05em;">≡⚡</span>
            Prompt Editor
          </div>
          <div style="display:flex;align-items:center;gap:12px;">
            <span style="display:flex;gap:10px;color:#555;font-size:0.93em;
                align-items:center;">
              <strong style="cursor:default;">B</strong>
              <em style="cursor:default;">I</em>
              <span style="cursor:default;font-weight:700;">•≡</span>
              <span style="font-family:monospace;cursor:default;">&lt;/&gt;</span>
              <span style="cursor:default;">🔗</span>
            </span>
            <span style="font-size:0.79em;color:#9e9e9e;">
              Words: <strong>$wordCount</strong>
            </span>
          </div>
        </div>
    """.trimIndent()
    ).use()

    // Bloc contexte injecté (fond orange, style maquette)
    if (enrichedPrompt.isNotBlank() && enrichedPrompt != rawPrompt) {
        val injected = enrichedPrompt.removePrefix(rawPrompt).trim()
        if (injected.isNotBlank()) {
            Jt.html(
                """
                <div style="background:#fff3e0;border-left:4px solid #e65100;
                    padding:9px 14px;font-size:0.86em;color:#5d3a00;
                    font-style:italic;border-radius:0;">
                  [CONTEXTE_AUTOMATIQUE] ${injected.replace("\n", "<br/>")}
                </div>
            """.trimIndent()
            ).use()
        }
    }

    Jt.textArea("Prompt enrichi")
        .value(enrichedPrompt)
        .height(220)
        .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
        .onChange { state["enrichedPrompt"] = it }
        .use()

    Jt.markdown("---").use()

    // ── Actions ───────────────────────────────────────────────────────────────
    val actionsBar = Jt.columns(3)
        .widths(listOf(0.42, 0.32, 0.26))
        .gap(ColumnsComponent.Gap.SMALL)
        .use()

    val sendForm = Jt.form().key("enrich-send-form").use(actionsBar.col(0))
    Jt.formSubmitButton("⚡ Enrichir AI").use(sendForm)

    val reenrichForm = Jt.form().key("enrich-reenrich-form").use(actionsBar.col(1))
    Jt.formSubmitButton("♻️ Re-enrichir").use(reenrichForm)

    val cancelForm = Jt.form().key("enrich-cancel-form").use(actionsBar.col(2))
    Jt.formSubmitButton("❌ Annuler").use(cancelForm)

    val actionCs = Jt.componentsState()

    if (actionCs.get("enrich-send-form") == true) {
        val finalPrompt = state.getString("enrichedPrompt") ?: rawPrompt
        history.add(
            Triple(
                "⚡",
                "Prompt enrichi → $currentEnrichModel (${finalPrompt.length} car.)",
                "ollama"
            )
        )
        history.add(Triple("⏳", "En attente de Claude...", "wait"))
        state["playwrightStatus"] = "waiting"
        state["page"] = "chat"
        // TODO : envoyer finalPrompt via activePage (étape Playwright)
        Jt.rerun()
    }

    if (actionCs["enrich-reenrich-form"] == true) {
        val mocked = "[$currentEnrichModel + ${selectedEmbeds.size} embed(s)] $rawPrompt"
        state["enrichedPrompt"] = mocked
        Jt.rerun()
    }

    val cancelPressed = actionCs["enrich-cancel-form"] == true
            || actionCs["cancel-header-form"] == true
    if (cancelPressed) {
        if (history.isNotEmpty()) history.removeLastOrNull()
        state["enrichedPrompt"] = ""
        state["enrichRawPrompt"] = ""
        state["page"] = "chat"
        Jt.rerun()
    }
}

/**
 * Démarre un serveur Javelit embarqué exposant le chatbot sur le port configuré
 * dans codebase.yml (défaut : 7070).
 *
 * Le LLM est mocké — seule l'UI et le cycle démarrage/arrêt sont validés ici.
 * L'intégration réelle (LangChain4j + resolveActiveKey) est planifiée en roadmap.
 *
 * Surcharge CLI :
 *   ./gradlew --no-daemon chatbot
 *   ./gradlew --no-daemon chatbot -Pcodebase.port=8080
 *   ./gradlew --no-daemon chatbot -Pcodebase.provider=ollama
 *
 * Arrêt propre :
 *   Ctrl+C → shutdown hook → latch.countDown() → server.stop() → Runtime.halt(0)
 *
 * Note : --no-daemon est obligatoire. Sans lui, Gradle exécute la tâche dans
 *         le Gradle Daemon — Runtime.halt(0) ne tue pas le daemon, le serveur
 *         Javelit reste accessible après Ctrl+C. Gradle 9.x ne permet pas de
 *         désactiver le daemon programmatiquement depuis le build script
 *         (StartParameter.isNoDaemonBuild supprimé en Gradle 9).
 *
 * Usage: ./gradlew --no-daemon chatbot
 */
tasks.register("chatbot") {
    group = "codebase"
    description = "Starts an embedded Javelit chatbot (LLM mocked) on port from codebase.yml"

    doLast {
        val projectDir = layout.projectDirectory.asFile
        val taskLogger = logger

        // ── Config ────────────────────────────────────────────────────────────
        val localConfig = CodebaseYmlConfig()
        val cfg = with(localConfig) { CodebaseConfiguration.load(projectDir) }

        val cliProvider = project.findProperty("codebase.provider") as String?
        val cliPort = (project.findProperty("codebase.port") as String?)?.toIntOrNull()

        val providerName = (cliProvider?.takeIf { it.isNotBlank() }
            ?: cfg.active.provider.takeIf { it.isNotBlank() }
            ?: "anthropic").lowercase()

        val port = cliPort ?: cfg.chatbot.port

        taskLogger.lifecycle("[chatbot] Port     : $port")
        taskLogger.lifecycle("[chatbot] Provider : $providerName (mocké)")
        taskLogger.lifecycle("[chatbot] Modèles  : ${availableModels(cfg, providerName)}")

        // ── Démarrage serveur Javelit (non-bloquant) ──────────────────────────
        val server = Server.builder({ chatbotApp(cfg, providerName) }, port).build()
        server.start()

        taskLogger.lifecycle("✅ Chatbot démarré → http://localhost:$port")
        taskLogger.lifecycle("   Ctrl+C pour arrêter")

        // ── Latch + shutdown hook ──────────────────────────────────────────
        val latch = CountDownLatch(1)

        Runtime.getRuntime().addShutdownHook(Thread {
            taskLogger.lifecycle("[chatbot] Arrêt en cours…")
            latch.countDown()
        })

        latch.await()

        taskLogger.lifecycle("[chatbot] Arrêté proprement.")
        Runtime.getRuntime().halt(0)
    }
}

/**
 * Generates snapshot.adoc — a full AsciiDoc snapshot of the project sources.
 * Replaces any existing snapshot.adoc at the project root.
 * snapshot.adoc is the write target only — its content is never collected.
 * readme.yml, slider-context.yml, site.yml and codebase.yml are rendered
 * with anonymized content.
 *
 * Usage: ./gradlew snapshot
 */
tasks.register("snapshot") {
    group = "codebase"
    description = "Generates snapshot.adoc with project tree and all source files"

    val rootDir = layout.projectDirectory.asFile

    outputs.file(layout.projectDirectory.file("snapshot.adoc"))

    doLast {
        val taskLogger = logger
        val manager = SnapshotManager()
        with(manager) {
            rootDir.generate(taskLogger)
        }
    }
}