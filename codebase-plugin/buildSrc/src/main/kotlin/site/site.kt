package site

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import slider.GitPushConfiguration
import java.io.File

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
)

// ── site.SiteYmlConfig ─────────────────────────────────────────────────────────────
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
    fun File.loadSiteConfiguration(): SiteConfiguration {
        val configFile = File(this, CONFIG_FILE_NAME)

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

