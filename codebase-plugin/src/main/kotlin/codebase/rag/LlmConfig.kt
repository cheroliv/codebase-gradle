package codebase.rag

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Configuration LLM chargée depuis un fichier YAML.
 * Pattern envVar/keyRef : le YAML référence une variable d'environnement,
 * jamais une clé API en dur. Les vraies clés vivent dans le shell (~/.bashrc)
 * ou dans configuration/settings/ (cercle 1, hors git).
 */
data class LlmConfig(
    @JsonProperty("ai")
    val ai: AiConfig = AiConfig()
) {
    companion object {
        private val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

        fun fromYaml(yaml: String): LlmConfig = mapper.readValue(yaml)
    }
}

data class AiConfig(
    @JsonProperty("ollama")
    val ollama: OllamaConfig = OllamaConfig(),

    @JsonProperty("gemini")
    val gemini: GeminiConfig = GeminiConfig()
)

data class OllamaConfig(
    @JsonProperty("baseUrl")
    val baseUrl: String = "http://localhost:11437",

    @JsonProperty("model")
    val model: String = "deepseek-v4-pro:cloud",

    @JsonProperty("envVar")
    val envVar: String? = null  // OLLAMA_BASE_URL, OLLAMA_MODEL
) {
    fun resolveBaseUrl(): String = resolveEnvVar(envVar, baseUrl)

    fun resolveModel(): String = System.getenv("OLLAMA_MODEL") ?: model
}

data class GeminiConfig(
    @JsonProperty("envVar")
    val envVar: String = "GEMINI_API_KEY",

    @JsonProperty("model")
    val model: String = "gemini-1.5-flash",

    @JsonProperty("baseUrl")
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
) {
    fun resolveApiKey(): String =
        System.getenv(envVar) ?: error("$envVar environment variable not set")

    fun resolveModel(): String = model
}

private fun resolveEnvVar(envVar: String?, default: String): String {
    if (envVar == null) return default
    return System.getenv(envVar) ?: default
}
