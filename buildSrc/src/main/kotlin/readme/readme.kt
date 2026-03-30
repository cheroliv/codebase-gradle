package readme

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.validation.Validation
import jakarta.validation.constraints.Email
import java.io.File


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
    fun File.loadReadmeConfiguration(): ReadmePlantUmlConfig {
        val configFile = File(this, CONFIG_FILE_NAME)

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
