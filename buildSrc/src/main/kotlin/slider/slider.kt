package slider

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File


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
)

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
    fun File.loadSliderConfiguration(): SliderConfiguration {
        val configFile = File(this, CONFIG_FILE_NAME)

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
