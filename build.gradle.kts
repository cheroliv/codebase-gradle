// build.gradle.kts
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.javelit.core.Jt
import io.javelit.core.Server
import jakarta.validation.Validation
import jakarta.validation.constraints.Email
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import org.gradle.api.logging.Logger

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
    val dir:         String = ".",
    val defaultLang: String = "en"
)

data class OutputConfig(
    val imgDir: String = ".github/workflows/readmes/images"
)

data class GitConfig(
    val userName:        String       = "github-actions[bot]",
    val userEmail:       String       = "github-actions[bot]@users.noreply.github.com",
    val commitMessage:   String       = "chore: generate readme [skip ci]",
    val token:           String       = "",
    val watchedBranches: List<String> = listOf("main", "master")
)

data class ReadmePlantUmlConfig(
    val source: SourceConfig = SourceConfig(),
    val output: OutputConfig = OutputConfig(),
    val git:    GitConfig    = GitConfig()
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

    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

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

    val TOKEN_MASK         = "***"
    val ANONYMOUS_USERNAME = "anonymous"
    val ACME_DOMAIN        = "acme.com"

    /** Carrier used solely to trigger Bean Validation @Email on a string. */
    private data class EmailHolder(@field:Email val value: String)

    /**
     * Builds a valid anonymized email: "<ANONYMOUS_USERNAME>@<ACME_DOMAIN>".
     * Throws [IllegalStateException] if the generated address fails @Email validation.
     */
    fun anonymizedEmail(): String {
        val candidate  = "$ANONYMOUS_USERNAME@$ACME_DOMAIN"
        val validator  = Validation.buildDefaultValidatorFactory().validator
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
                token     = TOKEN_MASK,
                userName  = ANONYMOUS_USERNAME,
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
    val name:        String                = "",
    val repository:  String                = "",
    val credentials: RepositoryCredentials = RepositoryCredentials()
)

data class GitPushConfiguration(
    val from:    String                  = "",
    val to:      String                  = "",
    val repo:    RepositoryConfiguration = RepositoryConfiguration(),
    val branch:  String                  = "",
    val message: String                  = ""
)

data class AiConfiguration(
    val gemini:      List<String> = emptyList(),
    val huggingface: List<String> = emptyList(),
    val mistral:     List<String> = emptyList()
)

data class SliderConfiguration(
    val srcPath:    String?               = null,
    val pushSlider: GitPushConfiguration? = null,
    val ai:         AiConfiguration?      = null
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

    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

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

    val TOKEN_MASK         = "***"
    val ANONYMOUS_USERNAME = "anonymous"
    val REPO_MASK          = "https://github.com/anonymous/anonymous.git"
    val BRANCH_MASK        = "main"

    private fun List<String>.maskAll(): List<String> =
        if (isEmpty()) emptyList() else List(size) { TOKEN_MASK }

    /**
     * Returns an anonymized copy of the receiver.
     * The original is never mutated (data class copy semantics).
     */
    fun SliderConfiguration.anonymize(): SliderConfiguration = copy(
        pushSlider = pushSlider?.copy(
            branch = BRANCH_MASK,
            repo   = pushSlider.repo.copy(
                repository  = REPO_MASK,
                credentials = pushSlider.repo.credentials.copy(
                    username = ANONYMOUS_USERNAME,
                    password = TOKEN_MASK
                )
            )
        ),
        ai = ai?.copy(
            gemini      = ai.gemini.maskAll(),
            huggingface = ai.huggingface.maskAll(),
            mistral     = ai.mistral.maskAll()
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
    val srcPath:     String = "",
    val destDirPath: String = "",
    val cname:       String = ""
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
    val name:       String,
    val columns:    List<SupabaseColumn>,
    val rlsEnabled: Boolean
)

data class SupabaseDatabaseSchema(
    val contacts: SupabaseTable,
    val messages: SupabaseTable
)

data class SupabaseRpcFunction(
    val name:   String,
    val params: List<SupabaseParam>
)

data class SupabaseProjectInfo(
    val url:       String,
    val publicKey: String
)

data class SupabaseContactFormConfig(
    val project: SupabaseProjectInfo,
    val schema:  SupabaseDatabaseSchema,
    val rpc:     SupabaseRpcFunction
)

data class SiteConfiguration(
    val bake:         BakeConfiguration         = BakeConfiguration(),
    val pushPage:     GitPushConfiguration       = GitPushConfiguration(),
    val pushMaquette: GitPushConfiguration       = GitPushConfiguration(),
    val pushSource:   GitPushConfiguration?      = null,
    val pushTemplate: GitPushConfiguration?      = null,
    val supabase:     SupabaseContactFormConfig? = null
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

    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()

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

    val TOKEN_MASK         = "***"
    val ANONYMOUS_USERNAME = "anonymous"
    val REPO_MASK          = "https://github.com/anonymous/anonymous.git"
    val BRANCH_MASK        = "main"
    val URL_MASK           = "https://anonymous.supabase.co"

    /**
     * Anonymizes one [GitPushConfiguration] — shared by all push* fields.
     */
    private fun GitPushConfiguration.anonymize(): GitPushConfiguration = copy(
        branch = BRANCH_MASK,
        repo   = repo.copy(
            repository  = REPO_MASK,
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
        pushPage     = pushPage.anonymize(),
        pushMaquette = pushMaquette.anonymize(),
        pushSource   = pushSource?.anonymize(),
        pushTemplate = pushTemplate?.anonymize(),
        supabase     = supabase?.copy(
            project = supabase.project.copy(
                url       = URL_MASK,
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
    val label:     String = "",
    val key:       String = "",
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
    val name:  String           = "",
    val email: String           = "",
    val keys:  List<NamedApiKey> = emptyList()
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
    val defaultAccount: String          = "",
    val defaultKey:     String          = "",
    val baseUrl:        String          = "",
    val models:         List<String>    = emptyList(),
    val defaultModel:   String          = "",
    val accounts:       List<LlmAccount> = emptyList()
)

/**
 * Regroupe tous les providers LLM configurés dans codebase.yml.
 */
data class AiProvidersConfig(
    val anthropic:   LlmProviderConfig = LlmProviderConfig(),
    val gemini:      LlmProviderConfig = LlmProviderConfig(),
    val huggingface: LlmProviderConfig = LlmProviderConfig(),
    val mistral:     LlmProviderConfig = LlmProviderConfig(),
    val ollama:      LlmProviderConfig = LlmProviderConfig(baseUrl = "http://localhost:11434"),
    val grok:        LlmProviderConfig = LlmProviderConfig(),
    val groq:        LlmProviderConfig = LlmProviderConfig()
)

/**
 * Sélection active globale — surcharge possible via paramètres CLI Gradle :
 *   -Pcodebase.provider=gemini -Pcodebase.account=pro -Pcodebase.key=prod
 */
data class ActiveSelection(
    val provider: String = "anthropic",
    val account:  String = "",
    val key:      String = ""
)

/**
 * Configuration du chatbot Javelit embarqué.
 */
data class ChatbotConfig(
    val port:            Int    = 7070,
    val defaultProvider: String = "anthropic"
)

/**
 * Racine de codebase.yml — centralise credentials AI et config chatbot.
 */
data class CodebaseConfiguration(
    val active:  ActiveSelection  = ActiveSelection(),
    val ai:      AiProvidersConfig = AiProvidersConfig(),
    val chatbot: ChatbotConfig     = ChatbotConfig()
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

    private val MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

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
        cfg:         CodebaseConfiguration,
        logger:      Logger,
        cliProvider: String? = null,
        cliAccount:  String? = null,
        cliKey:      String? = null
    ): NamedApiKey? {
        val providerName = (cliProvider?.takeIf { it.isNotBlank() }
            ?: cfg.active.provider.takeIf { it.isNotBlank() }
            ?: "anthropic").lowercase()

        // Réassignation en non-nullable après le when pour que le compilateur garde le smart cast
        val provider: LlmProviderConfig = when (providerName) {
            "anthropic"   -> cfg.ai.anthropic
            "gemini"      -> cfg.ai.gemini
            "huggingface" -> cfg.ai.huggingface
            "mistral"     -> cfg.ai.mistral
            "ollama"      -> cfg.ai.ollama
            "grok"        -> cfg.ai.grok
            "groq"        -> cfg.ai.groq
            else          -> {
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
            anthropic   = ai.anthropic.anonymized(),
            gemini      = ai.gemini.anonymized(),
            huggingface = ai.huggingface.anonymized(),
            mistral     = ai.mistral.anonymized(),
            ollama      = ai.ollama.anonymized(maskKeys = false), // baseUrl non masquée, pas de clés
            grok        = ai.grok.anonymized(),
            groq        = ai.groq.anonymized()
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

    val PRUNED_DIRS = setOf(
        "build", ".gradle", ".git", ".idea",
        "node_modules", ".kotlin", "__pycache__"
    )

    val COLLECTED_EXTENSIONS = setOf(
        "kt", "kts", "yml", "yaml", "properties", "toml", "adoc"
    )

    val COLLECTED_FILENAMES = setOf(
        "readme.yml",
        "slider-context.yml",
        "site.yml",
        "codebase.yml",
        "gradle.properties",
        "settings.gradle.kts",
        "build.gradle.kts"
    )

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
        "kt", "kts"   -> "kotlin"
        "toml"        -> "toml"
        "adoc"        -> "asciidoc"
        "yml", "yaml" -> "yaml"
        "properties"  -> "properties"
        else          -> "text"
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
                val isLast    = index == children.lastIndex
                val connector = if (isLast) "└── " else "├── "
                val childPfx  = if (isLast) "    " else "│   "
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
                    else              -> Unit
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
        val mapper      = ObjectMapper(YAMLFactory()).registerKotlinModule()
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
    group       = "codebase"
    description = "Verifies via logs that ReadmePlantUmlConfig anonymization masks sensitive fields"

    doLast {
        val mapper     = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = ReadmeYmlAnonymizer()

        // ── case 1: real token ───────────────────────────────────────────────
        val realToken  = "ghp_supersecret123"
        val configReal = ReadmePlantUmlConfig(
            source = SourceConfig(dir = "src", defaultLang = "fr"),
            git    = GitConfig(
                token           = realToken,
                userName        = "my-bot",
                userEmail       = "my-bot@company.com",
                watchedBranches = listOf("main", "develop")
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: real token ──────────────────────────────")
        logger.lifecycle(yamlReal)

        check(anonymizer.TOKEN_MASK in yamlReal)         { "FAIL case 1: token mask not found" }
        check(realToken !in yamlReal)                    { "FAIL case 1: real token is visible!" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1: userName not anonymized" }
        check("my-bot" !in yamlReal)                     { "FAIL case 1: real userName is visible!" }
        check(anonymizer.anonymizedEmail() in yamlReal)  { "FAIL case 1: anonymized email not found" }
        check("my-bot@company.com" !in yamlReal)         { "FAIL case 1: real email is visible!" }
        check("fr" in yamlReal)                          { "FAIL case 1: defaultLang lost" }
        check("src" in yamlReal)                         { "FAIL case 1: source.dir lost" }
        check("develop" in yamlReal)                     { "FAIL case 1: watchedBranches lost" }

        logger.lifecycle("✅ case 1 OK — token, userName, userEmail anonymized — other fields preserved")

        // ── case 2: placeholder token ────────────────────────────────────────
        val configPlaceholder = ReadmePlantUmlConfig(
            git = GitConfig(token = "<YOUR_GITHUB_PAT>")
        )
        val yamlPlaceholder = with(anonymizer) { configPlaceholder.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: placeholder token ───────────────────────")
        logger.lifecycle(yamlPlaceholder)

        check(anonymizer.TOKEN_MASK in yamlPlaceholder)        { "FAIL case 2: token mask not found" }
        check("<YOUR_GITHUB_PAT>" !in yamlPlaceholder)         { "FAIL case 2: placeholder is visible!" }
        check(anonymizer.anonymizedEmail() in yamlPlaceholder) { "FAIL case 2: anonymized email not found" }

        logger.lifecycle("✅ case 2 OK — placeholder anonymized")

        // ── case 3: empty token ──────────────────────────────────────────────
        val configEmpty = ReadmePlantUmlConfig(git = GitConfig(token = ""))
        val yamlEmpty   = with(anonymizer) { configEmpty.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: empty token ─────────────────────────────")
        logger.lifecycle(yamlEmpty)

        check(anonymizer.TOKEN_MASK in yamlEmpty)        { "FAIL case 3: token mask not found for empty token" }
        check(anonymizer.anonymizedEmail() in yamlEmpty) { "FAIL case 3: anonymized email not found" }

        logger.lifecycle("✅ case 3 OK — empty token anonymized")

        // ── case 4: idempotency — original object must not be mutated ────────
        val configIdempotent = ReadmePlantUmlConfig(
            git = GitConfig(token = "ghp_idempotence", userName = "real-user")
        )
        with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }
        val yaml2 = with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }

        check("ghp_idempotence" !in yaml2)            { "FAIL case 4: token visible on 2nd call — mutation detected!" }
        check("real-user" !in yaml2)                  { "FAIL case 4: userName visible on 2nd call — mutation detected!" }
        check(anonymizer.TOKEN_MASK in yaml2)         { "FAIL case 4: token mask not found on 2nd call" }
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
            } catch (e: IllegalStateException) {
                logger.lifecycle("✅ case 6 OK — resolvedToken throws on blank token")
            }

            // ── case 7: resolvedToken — placeholder token ─────────────────────
            val gitPlaceholder = GitConfig(token = "<YOUR_GITHUB_PAT>")
            try {
                gitPlaceholder.resolvedToken()
                error("FAIL case 7: resolvedToken should have thrown")
            } catch (e: IllegalStateException) {
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
    group       = "codebase"
    description = "Verifies via logs that SliderConfiguration anonymization masks sensitive fields"

    doLast {
        val mapper     = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = SliderYmlAnonymizer()

        // ── case 1: real credentials ─────────────────────────────────────────
        val realPassword = "ghp_EkAxg8TgUFBT2ihx8QH6vn0I6k4T"
        val realUsername = "cheroliv"
        val realRepo     = "https://github.com/foo/bar.git"
        val realBranch   = "production"

        val configReal = SliderConfiguration(
            srcPath    = "docs/asciidocRevealJs",
            pushSlider = GitPushConfiguration(
                from    = "/docs/asciidocRevealJs",
                to      = "cvs",
                branch  = realBranch,
                message = "slides show",
                repo    = RepositoryConfiguration(
                    name        = "slider-gradle",
                    repository  = realRepo,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword)
                )
            ),
            ai = AiConfiguration(
                gemini      = listOf("FAKE-gemini-key-for-test-only"),
                huggingface = listOf("FAKE-hf-key-for-test-only"),
                mistral     = listOf("FAKE-mistral-key-1", "FAKE-mistral-key-2")
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: real credentials ────────────────────────")
        logger.lifecycle(yamlReal)

        check(realPassword !in yamlReal)                             { "FAIL case 1: real password is visible!" }
        check(realUsername !in yamlReal)                             { "FAIL case 1: real username is visible!" }
        check(realRepo !in yamlReal)                                 { "FAIL case 1: real repo is visible!" }
        check(realBranch !in yamlReal)                               { "FAIL case 1: real branch is visible!" }
        check("FAKE-gemini-key-for-test-only" !in yamlReal)      { "FAIL case 1: real gemini key is visible!" }
        check("FAKE-hf-key-for-test-only" !in yamlReal) { "FAIL case 1: real huggingface key is visible!" }
        check("FAKE-mistral-key-1" !in yamlReal)                   { "FAIL case 1: real mistral key is visible!" }
        check("FAKE-mistral-key-2" !in yamlReal)                  { "FAIL case 1: real mistral key 2 is visible!" }
        check(anonymizer.TOKEN_MASK in yamlReal)                     { "FAIL case 1: token mask not found" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal)             { "FAIL case 1: anonymous username not found" }
        check(anonymizer.REPO_MASK in yamlReal)                      { "FAIL case 1: repo mask not found" }
        check(anonymizer.BRANCH_MASK in yamlReal)                    { "FAIL case 1: branch mask not found" }
        check("docs/asciidocRevealJs" in yamlReal)                   { "FAIL case 1: srcPath lost" }
        check("slides show" in yamlReal)                             { "FAIL case 1: message lost" }
        check("slider-gradle" in yamlReal)                           { "FAIL case 1: repo name lost" }

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
        check("cheroliv-bot" !in yamlEmptyAi)                     { "FAIL case 2: real username is visible!" }
        check("ghp_emptyAiSecret99" !in yamlEmptyAi)              { "FAIL case 2: real password is visible!" }
        check(anonymizer.REPO_MASK in yamlEmptyAi)                { "FAIL case 2: repo mask not found" }

        logger.lifecycle("✅ case 2 OK — empty AI lists handled correctly")

        // ── case 3: null pushSlider and null ai ───────────────────────────────
        val configNulls = SliderConfiguration(srcPath = "src/slides")
        val yamlNulls   = with(anonymizer) { configNulls.toAnonymizedYaml(mapper) }

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

        check("realuser" !in yaml2)        { "FAIL case 4: username visible on 2nd call — mutation detected!" }
        check("realpass" !in yaml2)        { "FAIL case 4: password visible on 2nd call — mutation detected!" }
        check("real-gemini-key" !in yaml2) { "FAIL case 4: gemini key visible on 2nd call — mutation detected!" }
        check(anonymizer.TOKEN_MASK in yaml2)         { "FAIL case 4: token mask not found on 2nd call" }
        check(anonymizer.ANONYMOUS_USERNAME in yaml2) { "FAIL case 4: anonymous username not found on 2nd call" }

        logger.lifecycle("✅ case 4 OK — original not mutated, idempotent")

        // ── case 5: multiple AI keys per provider ─────────────────────────────
        val configMultiKeys = SliderConfiguration(
            ai = AiConfiguration(
                gemini      = listOf("key1", "key2", "key3"),
                huggingface = listOf("hf1", "hf2"),
                mistral     = listOf("ms1")
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

        logger.lifecycle("✅ case 5 OK — all ${maskCount} AI keys masked across providers")

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
    group       = "codebase"
    description = "Verifies via logs that SiteConfiguration anonymization masks sensitive fields"

    doLast {
        val mapper     = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = SiteYmlAnonymizer()

        // ── case 1: real credentials sur pushPage et pushMaquette ────────────
        val realPassword1 = "ghoS9WycCyJ8pX24Ge9l"
        val realPassword2 = "ghoFRVZTNo8OycCyJ8pX24Ge9l"
        val realUsername  = "foo"
        val realRepo1     = "https://github.com/foo/bar.git"
        val realRepo2     = "https://github.com/baz/qux.git"
        val realBranch    = "production"

        val configReal = SiteConfiguration(
            bake = BakeConfiguration(srcPath = "site", destDirPath = "bake"),
            pushPage = GitPushConfiguration(
                from    = "bake",
                to      = "cvs",
                branch  = realBranch,
                message = "com.cheroliv.bakery",
                repo    = RepositoryConfiguration(
                    name        = "pages-content/bakery",
                    repository  = realRepo1,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword1)
                )
            ),
            pushMaquette = GitPushConfiguration(
                from    = "maquette",
                to      = "cvs",
                branch  = realBranch,
                message = "cheroliv-maquette",
                repo    = RepositoryConfiguration(
                    name        = "bakery-maquette",
                    repository  = realRepo2,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword2)
                )
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: pushPage + pushMaquette ─────────────────")
        logger.lifecycle(yamlReal)

        check(realPassword1 !in yamlReal)                { "FAIL case 1: real password1 is visible!" }
        check(realPassword2 !in yamlReal)                { "FAIL case 1: real password2 is visible!" }
        check(realUsername !in yamlReal)                 { "FAIL case 1: real username is visible!" }
        check(realRepo1 !in yamlReal)                    { "FAIL case 1: real repo1 is visible!" }
        check(realRepo2 !in yamlReal)                    { "FAIL case 1: real repo2 is visible!" }
        check(anonymizer.TOKEN_MASK in yamlReal)         { "FAIL case 1: token mask not found" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1: anonymous username not found" }
        check(anonymizer.REPO_MASK in yamlReal)          { "FAIL case 1: repo mask not found" }
        check(anonymizer.BRANCH_MASK in yamlReal)        { "FAIL case 1: branch mask not found" }
        check("site" in yamlReal)                        { "FAIL case 1: bake.srcPath lost" }
        check("bake" in yamlReal)                        { "FAIL case 1: bake.destDirPath lost" }
        check("com.cheroliv.bakery" in yamlReal)         { "FAIL case 1: pushPage.message lost" }
        check("cheroliv-maquette" in yamlReal)           { "FAIL case 1: pushMaquette.message lost" }
        check("pages-content/bakery" in yamlReal)        { "FAIL case 1: pushPage.repo.name lost" }
        check("bakery-maquette" in yamlReal)             { "FAIL case 1: pushMaquette.repo.name lost" }

        logger.lifecycle("✅ case 1 OK — pushPage + pushMaquette masked, non-sensitive fields preserved")

        // ── case 2: supabase url et publicKey ────────────────────────────────
        val realUrl       = "https://hkgrvjgukx.supabase.co"
        val realPublicKey = "eyJhbCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhrZ3lubGZtamh2cXpydmpndWt4Iiwicm9sZSI6ImFub24ifQ"

        val configSupabase = SiteConfiguration(
            pushPage     = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            supabase     = SupabaseContactFormConfig(
                project = SupabaseProjectInfo(url = realUrl, publicKey = realPublicKey),
                schema  = SupabaseDatabaseSchema(
                    contacts = SupabaseTable(
                        name = "public.contacts", rlsEnabled = true,
                        columns = listOf(
                            SupabaseColumn("id",         "uuid"),
                            SupabaseColumn("created_at", "timestamptz"),
                            SupabaseColumn("name",       "text"),
                            SupabaseColumn("email",      "text"),
                            SupabaseColumn("telephone",  "text")
                        )
                    ),
                    messages = SupabaseTable(
                        name = "public.messages", rlsEnabled = true,
                        columns = listOf(
                            SupabaseColumn("id",         "uuid"),
                            SupabaseColumn("created_at", "timestamptz"),
                            SupabaseColumn("contact_id", "uuid"),
                            SupabaseColumn("subject",    "text"),
                            SupabaseColumn("message",    "text")
                        )
                    )
                ),
                rpc = SupabaseRpcFunction(
                    name   = "public.handle_contact_form",
                    params = listOf(
                        SupabaseParam("p_name",    "text"),
                        SupabaseParam("p_email",   "text"),
                        SupabaseParam("p_subject", "text"),
                        SupabaseParam("p_message", "text")
                    )
                )
            )
        )
        val yamlSupabase = with(anonymizer) { configSupabase.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: supabase url + publicKey ────────────────")
        logger.lifecycle(yamlSupabase)

        check(realUrl !in yamlSupabase)                     { "FAIL case 2: real supabase url is visible!" }
        check(realPublicKey !in yamlSupabase)               { "FAIL case 2: real publicKey is visible!" }
        check(anonymizer.URL_MASK in yamlSupabase)          { "FAIL case 2: url mask not found" }
        check(anonymizer.TOKEN_MASK in yamlSupabase)        { "FAIL case 2: token mask not found" }
        check("public.contacts" in yamlSupabase)            { "FAIL case 2: schema.contacts.name lost" }
        check("public.messages" in yamlSupabase)            { "FAIL case 2: schema.messages.name lost" }
        check("public.handle_contact_form" in yamlSupabase) { "FAIL case 2: rpc.name lost" }

        logger.lifecycle("✅ case 2 OK — supabase url + publicKey masked, schema + rpc preserved")

        // ── case 3: pushSource et pushTemplate nulls ─────────────────────────
        val configNullPush = SiteConfiguration(
            pushPage     = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            pushSource   = null,
            pushTemplate = null
        )
        val yamlNullPush = with(anonymizer) { configNullPush.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: pushSource + pushTemplate nulls ─────────")
        logger.lifecycle(yamlNullPush)

        logger.lifecycle("✅ case 3 OK — null push fields handled safely (no NPE)")

        // ── case 4: pushSource et pushTemplate non-nulls ──────────────────────
        val realRepo3 = "https://github.com/secret/source.git"
        val configAllPush = SiteConfiguration(
            pushPage     = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            pushSource   = GitPushConfiguration(
                branch = "secret-branch",
                repo   = RepositoryConfiguration(
                    repository  = realRepo3,
                    credentials = RepositoryCredentials(username = "src-user", password = "src-pass")
                )
            ),
            pushTemplate = GitPushConfiguration(
                branch = "template-branch",
                repo   = RepositoryConfiguration(
                    repository  = "https://github.com/secret/template.git",
                    credentials = RepositoryCredentials(username = "tmpl-user", password = "tmpl-pass")
                )
            )
        )
        val yamlAllPush = with(anonymizer) { configAllPush.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 4: pushSource + pushTemplate non-nulls ──────")
        logger.lifecycle(yamlAllPush)

        check(realRepo3 !in yamlAllPush)                              { "FAIL case 4: real repo3 is visible!" }
        check("secret-branch" !in yamlAllPush)                        { "FAIL case 4: secret-branch is visible!" }
        check("src-user" !in yamlAllPush)                             { "FAIL case 4: src-user is visible!" }
        check("https://github.com/secret/template.git" !in yamlAllPush) { "FAIL case 4: template repo is visible!" }
        check(anonymizer.REPO_MASK in yamlAllPush)                    { "FAIL case 4: repo mask not found" }

        logger.lifecycle("✅ case 4 OK — pushSource + pushTemplate masked")

        // ── case 5: idempotency ───────────────────────────────────────────────
        val configIdempotent = SiteConfiguration(
            pushPage = GitPushConfiguration(
                branch = "prod",
                repo   = RepositoryConfiguration(
                    repository  = "https://github.com/real/repo.git",
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
    group       = "codebase"
    description = "Verifies via logs that CodebaseConfiguration anonymization masks API keys"

    doLast {
        val mapper     = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = CodebaseYmlAnonymizer()

        // ── case 1: anthropic — clé réelle ───────────────────────────────────
        val realAnthropic = "sk-ant-api03-supersecret"
        val config1 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    defaultAccount = "perso",
                    defaultKey     = "chatbot",
                    models         = listOf("claude-opus-4-5", "claude-sonnet-4-5"),
                    defaultModel   = "claude-opus-4-5",
                    accounts       = listOf(
                        LlmAccount("perso", "p@gmail.com", listOf(
                            NamedApiKey("chatbot", realAnthropic, "2026-12-31")
                        ))
                    )
                )
            )
        )
        val yaml1 = with(anonymizer) { config1.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: anthropic real key ──────────────────────")
        logger.lifecycle(yaml1)

        check(realAnthropic !in yaml1)           { "FAIL case 1: real anthropic key is visible!" }
        check(anonymizer.TOKEN_MASK in yaml1)    { "FAIL case 1: token mask not found" }
        check("perso" in yaml1)                  { "FAIL case 1: account name lost" }
        check("p@gmail.com" in yaml1)            { "FAIL case 1: account email lost" }
        check("chatbot" in yaml1)                { "FAIL case 1: key label lost" }
        check("2026-12-31" in yaml1)             { "FAIL case 1: expiresAt lost" }
        check("claude-opus-4-5" in yaml1)        { "FAIL case 1: model lost" }

        logger.lifecycle("✅ case 1 OK — anthropic key masked, metadata preserved")

        // ── case 2: tous les providers sauf ollama ────────────────────────────
        val realGemini  = "AIzaSyD-supersecret"
        val realHf      = "hf_supersecrettoken"
        val realMistral = "ms-supersecret-key"
        val realGrok    = "xai-supersecret"
        val realGroq    = "gsk_supersecret"

        val config2 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                gemini = LlmProviderConfig(accounts = listOf(
                    LlmAccount("perso", "g@gmail.com", listOf(
                        NamedApiKey("main", realGemini, "2026-12-31")
                    ))
                )),
                huggingface = LlmProviderConfig(accounts = listOf(
                    LlmAccount("perso", "hf@gmail.com", listOf(
                        NamedApiKey("main", realHf, "")
                    ))
                )),
                mistral = LlmProviderConfig(accounts = listOf(
                    LlmAccount("perso", "ms@gmail.com", listOf(
                        NamedApiKey("main",  realMistral,     "2027-03-01"),
                        NamedApiKey("spare", "mst-spare-key", "")
                    ))
                )),
                grok = LlmProviderConfig(accounts = listOf(
                    LlmAccount("perso", "grok@x.com", listOf(NamedApiKey("main", realGrok, "")))
                )),
                groq = LlmProviderConfig(accounts = listOf(
                    LlmAccount("perso", "groq@groq.com", listOf(NamedApiKey("main", realGroq, "")))
                ))
            )
        )
        val yaml2 = with(anonymizer) { config2.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: gemini + huggingface + mistral + grok + groq")
        logger.lifecycle(yaml2)

        listOf(realGemini, realHf, realMistral, realGrok, realGroq, "mst-spare-key").forEach { k ->
            check(k !in yaml2) { "FAIL case 2: real key '$k' is visible!" }
        }
        check("g@gmail.com" in yaml2)    { "FAIL case 2: gemini email lost" }
        check("2026-12-31" in yaml2)     { "FAIL case 2: expiresAt lost" }
        check("spare" in yaml2)          { "FAIL case 2: spare label lost" }

        logger.lifecycle("✅ case 2 OK — all providers masked, metadata preserved")

        // ── case 3: ollama — baseUrl non masquée, pas de clés ─────────────────
        val config3 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                ollama = LlmProviderConfig(
                    baseUrl  = "http://localhost:11434",
                    models   = listOf("llama3.2", "codellama"),
                    accounts = listOf(LlmAccount("local", "", emptyList()))
                )
            )
        )
        val yaml3 = with(anonymizer) { config3.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: ollama baseUrl non masquée ──────────────")
        logger.lifecycle(yaml3)

        check("http://localhost:11434" in yaml3) { "FAIL case 3: ollama baseUrl was masked!" }
        check("llama3.2" in yaml3)               { "FAIL case 3: ollama model lost" }
        check("codellama" in yaml3)              { "FAIL case 3: ollama model codellama lost" }

        logger.lifecycle("✅ case 3 OK — ollama baseUrl preserved (not a secret)")

        // ── case 4: clés vides → pas de masque ───────────────────────────────
        val config4 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(accounts = listOf(
                    LlmAccount("perso", "p@g.com", listOf(NamedApiKey("main", "", "")))
                ))
            )
        )
        val yaml4     = with(anonymizer) { config4.toAnonymizedYaml(mapper) }
        val maskCount = yaml4.lines().count { anonymizer.TOKEN_MASK in it }

        logger.lifecycle("── case 4: clés vides — pas de masque ──────────────")
        logger.lifecycle(yaml4)

        check(maskCount == 0) { "FAIL case 4: expected 0 masks for blank keys, got $maskCount" }

        logger.lifecycle("✅ case 4 OK — blank keys produce no spurious masks")

        // ── case 5: active + chatbot préservés ───────────────────────────────
        val config5 = CodebaseConfiguration(
            active  = ActiveSelection(provider = "mistral", account = "pro", key = "prod"),
            chatbot = ChatbotConfig(port = 9090, defaultProvider = "mistral")
        )
        val yaml5 = with(anonymizer) { config5.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 5: active + chatbot préservés ──────────────")
        logger.lifecycle(yaml5)

        check("mistral" in yaml5) { "FAIL case 5: active.provider lost" }
        check("pro" in yaml5)     { "FAIL case 5: active.account lost" }
        check("prod" in yaml5)    { "FAIL case 5: active.key lost" }
        check("9090" in yaml5)    { "FAIL case 5: chatbot.port lost" }

        logger.lifecycle("✅ case 5 OK — active + chatbot config preserved")

        // ── case 6: idempotency — original must not be mutated ────────────────
        val config6 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(accounts = listOf(
                    LlmAccount("perso", "p@g.com", listOf(
                        NamedApiKey("chatbot", "sk-ant-idempotent", "2027-01-01")
                    ))
                )),
                gemini = LlmProviderConfig(accounts = listOf(
                    LlmAccount("perso", "g@g.com", listOf(
                        NamedApiKey("main", "AIza-idempotent", "")
                    ))
                ))
            )
        )
        with(anonymizer) { config6.toAnonymizedYaml(mapper) }
        val yaml6b = with(anonymizer) { config6.toAnonymizedYaml(mapper) }

        check("sk-ant-idempotent" !in yaml6b) { "FAIL case 6: anthropic key visible on 2nd call — mutation!" }
        check("AIza-idempotent" !in yaml6b)   { "FAIL case 6: gemini key visible on 2nd call — mutation!" }
        check(anonymizer.TOKEN_MASK in yaml6b) { "FAIL case 6: token mask not found on 2nd call" }

        logger.lifecycle("✅ case 6 OK — original not mutated, idempotent")

        // ── case 7: resolveActiveKey — sélection via active + CLI override ────
        val localConfig = CodebaseYmlConfig()
        val config7 = CodebaseConfiguration(
            active = ActiveSelection(provider = "anthropic", account = "perso", key = "chatbot"),
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    defaultAccount = "perso",
                    defaultKey     = "chatbot",
                    accounts       = listOf(
                        LlmAccount("perso", "p@g.com", listOf(
                            NamedApiKey("chatbot", "sk-ant-chatbot", "2027-06-01"),
                            NamedApiKey("ci",      "sk-ant-ci",      "")
                        )),
                        LlmAccount("pro", "pro@c.com", listOf(
                            NamedApiKey("prod", "sk-ant-prod", "2025-01-01") // expirée
                        ))
                    )
                )
            )
        )

        // résolution normale
        val resolved = localConfig.resolveActiveKey(config7, logger)
        check(resolved?.key == "sk-ant-chatbot") { "FAIL case 7: expected chatbot key, got ${resolved?.key}" }
        check(resolved?.label == "chatbot")       { "FAIL case 7: expected label 'chatbot', got ${resolved?.label}" }

        // surcharge CLI provider+account+key
        val resolvedCli = localConfig.resolveActiveKey(config7, logger,
            cliProvider = "anthropic", cliAccount = "pro", cliKey = "prod"
        )
        check(resolvedCli?.key == "sk-ant-prod") { "FAIL case 7: CLI override failed, got ${resolvedCli?.key}" }
        // la clé est expirée → warning attendu dans les logs (pas d'erreur)

        // surcharge CLI key seule
        val resolvedCliKey = localConfig.resolveActiveKey(config7, logger,
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
    group       = "codebase"
    description = "Generates codebase.yml scaffold and registers it in .gitignore"

    doLast {
        val projectDir = layout.projectDirectory.asFile
        val target     = projectDir.resolve("codebase.yml")

        // ── Warning + skip si déjà présent ───────────────────────────────────
        if (target.exists()) {
            logger.warn("⚠️  codebase.yml already exists — scaffold skipped (delete it manually to regenerate)")
            return@doLast
        }

        // ── Génération du fichier scaffold ────────────────────────────────────
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

        // ── Ajout au .gitignore ───────────────────────────────────────────────
        val gitignore = projectDir.resolve(".gitignore")
        val entry     = "codebase.yml"

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
        "anthropic"   -> cfg.ai.anthropic
        "gemini"      -> cfg.ai.gemini
        "huggingface" -> cfg.ai.huggingface
        "mistral"     -> cfg.ai.mistral
        "ollama"      -> cfg.ai.ollama
        "grok"        -> cfg.ai.grok
        "groq"        -> cfg.ai.groq
        else          -> return emptyList()
    }
    return provider.models.ifEmpty { listOf("${providerName}-default") }
}

/**
 * Rendu Javelit du chatbot.
 * Appelé à chaque rerun par le serveur Javelit.
 *
 * Barre de saisie (style Claude) :
 *  ┌─────────────────────────────┬──────────────────┬──────────┐
 *  │  Votre message (textArea)   │  [ opus-4-5 ▾ ]  │  [ ➤ ]  │
 *  └─────────────────────────────┴──────────────────┴──────────┘
 *
 * Le bouton modèle ouvre un popover contenant :
 *  - la liste des modèles disponibles (radio)
 *  - un bouton "⚙ Settings" sans comportement pour l'instant
 *
 * Le LLM est mocké — seule l'UI est validée à ce stade.
 */
fun chatbotApp(
    cfg:          CodebaseConfiguration,
    providerName: String
) {
    // ── Session state ─────────────────────────────────────────────────────────
    Jt.sessionState().putIfAbsent("history", mutableListOf<Pair<String, String>>())
    @Suppress("UNCHECKED_CAST")
    val history = Jt.sessionState()
        .get("history") as MutableList<Pair<String, String>>

    val models       = availableModels(cfg, providerName)
    val defaultModel = models.firstOrNull() ?: "no-model"
    Jt.sessionState().putIfAbsent("selectedModel", defaultModel)
    val currentModel = Jt.sessionState().getString("selectedModel") ?: defaultModel

    // ── Titre + sous-titre provider ───────────────────────────────────────────
    Jt.title("🤖 Codebase Chatbot").use()
    Jt.markdown("_Provider :_ `$providerName` · _LLM mocké_").use()
    Jt.divider("header").use()

    // ── Historique ────────────────────────────────────────────────────────────
    if (history.isEmpty()) {
        Jt.info("Aucun message pour l'instant. Commencez la conversation !").use()
    } else {
        history.forEach { (role, content) ->
            val label = if (role == "user") "**Vous :**" else "**Assistant :**"
            Jt.markdown("$label $content").use()
        }
    }

    Jt.divider("before-form").use()

    // ── Barre de saisie : textArea | [modèle ▾] | [➤] ────────────────────────
    //
    // Layout 3 colonnes :
    //   col(0) 70% — textArea dans le form
    //   col(1) 18% — bouton modèle + popover (hors form)
    //   col(2) 12% — bouton submit dans le form
    //
    // Contrainte Javelit : form et popover ne peuvent pas partager la même colonne.

    val bar = Jt.columns(3)
        .widths(listOf(0.70, 0.18, 0.12))
        .use()

    // col(0) — zone de saisie
    val form      = Jt.form().use(bar.col(0))
    val userInput = Jt.textArea("").placeholder("Votre message…").height(80).use(form)

    // col(1) — bouton modèle abrégé + popover liste des modèles
    //
    // Abréviation : on garde les 3 derniers segments séparés par '-'
    // ex: "claude-opus-4-5" → "opus-4-5" | "gemini-2.5-pro" → "2.5-pro"
    val shortModel = currentModel.split('-').let { parts ->
        if (parts.size >= 3) parts.takeLast(3).joinToString("-") else currentModel
    }

    val modelPopover = Jt.popover("$shortModel ▾")
        .key("model-picker")
        .use(bar.col(1))

    // Contenu du popover
    Jt.markdown("#### Choisir un modèle").use(modelPopover)
    Jt.divider("pop-top").use(modelPopover)

    val pickedModel = Jt.radio("", models)
        .value(currentModel)
        .use(modelPopover)

    if (pickedModel != currentModel) {
        Jt.sessionState().put("selectedModel", pickedModel)
        Jt.rerun()
    }

    Jt.divider("pop-mid").use(modelPopover)

    // Bouton Settings — pas de comportement pour l'instant
    Jt.button("⚙ Settings")
        .key("settings-in-popover")
        .icon(":material/build:")
        .use(modelPopover)

    // col(2) — bouton submit
    val submitted = Jt.formSubmitButton("➤").use(bar.col(2))

    // ── Traitement du message soumis ──────────────────────────────────────────
    if (submitted && userInput.isNotBlank()) {
        val model = Jt.sessionState().getString("selectedModel") ?: defaultModel
        history += "user" to userInput.trim()

        // Mock LLM
        val mockResponse = "[mock] `$model` — reçu : « ${userInput.trim()} » " +
                "(${history.count { it.first == "user" }} msg)"
        history += "assistant" to mockResponse

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
 *   ./gradlew chatbot -Pcodebase.port=8080
 *   ./gradlew chatbot -Pcodebase.provider=ollama
 *
 * Arrêt propre :
 *   Ctrl+C → shutdown hook JVM → CountDownLatch.countDown() → thread Gradle libéré
 *
 * Usage: ./gradlew chatbot
 */
tasks.register("chatbot") {
    group       = "codebase"
    description = "Starts an embedded Javelit chatbot (LLM mocked) on port from codebase.yml"

    doLast {
        val projectDir = layout.projectDirectory.asFile
        val taskLogger = logger

        // ── Config ────────────────────────────────────────────────────────────
        val localConfig = CodebaseYmlConfig()
        val cfg = with(localConfig) { CodebaseConfiguration.load(projectDir) }

        val cliProvider = project.findProperty("codebase.provider") as String?
        val cliPort     = (project.findProperty("codebase.port") as String?)?.toIntOrNull()

        val providerName = (cliProvider?.takeIf { it.isNotBlank() }
            ?: cfg.active.provider.takeIf { it.isNotBlank() }
            ?: "anthropic").lowercase()

        val port = cliPort ?: cfg.chatbot.port

        taskLogger.lifecycle("[chatbot] Port     : $port")
        taskLogger.lifecycle("[chatbot] Provider : $providerName (mocké)")
        taskLogger.lifecycle("[chatbot] Modèles  : ${availableModels(cfg, providerName)}")

        // ── Latch pour bloquer le thread Gradle jusqu'à Ctrl+C ────────────────
        val latch = CountDownLatch(1)

        // ── Shutdown hook : signal propre à la JVM ────────────────────────────
        Runtime.getRuntime().addShutdownHook(Thread {
            taskLogger.lifecycle("[chatbot] Arrêt en cours…")
            latch.countDown()
        })

        // ── Démarrage serveur Javelit (non-bloquant) ──────────────────────────
        val server = Server.builder({ chatbotApp(cfg, providerName) }, port).build()
        server.start()

        taskLogger.lifecycle("✅ Chatbot démarré → http://localhost:$port")
        taskLogger.lifecycle("   Ctrl+C pour arrêter")

        // ── Blocage du thread Gradle ──────────────────────────────────────────
        latch.await()
        taskLogger.lifecycle("[chatbot] Arrêté proprement.")
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
    group       = "codebase"
    description = "Generates snapshot.adoc with project tree and all source files"

    val rootDir = layout.projectDirectory.asFile

    outputs.file(layout.projectDirectory.file("snapshot.adoc"))

    doLast {
        val taskLogger = logger
        val manager    = SnapshotManager()
        with(manager) {
            rootDir.generate(taskLogger)
        }
    }
}