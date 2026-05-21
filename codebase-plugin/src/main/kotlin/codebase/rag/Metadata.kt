package codebase.rag

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Instant

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = QuizMetadata::class, name = "Quiz"),
    JsonSubTypes.Type(value = PlanMetadata::class, name = "Plan"),
    JsonSubTypes.Type(value = PDFMetadata::class, name = "PDF")
)
sealed class Metadata {
    abstract val source: String
    abstract val type: String
    abstract val version: String
    abstract val generatedAt: String  // ISO 8601 string
    abstract val model: String
    abstract val dependencies: List<String>

    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun fromJson(json: String): Metadata {
            return try {
                mapper.readValue(json)
            } catch (e: Exception) {
                // Fallback for unknown metadata types from other boroughs
                val node = mapper.readTree(json)
                UnknownMetadata(
                    source = node.get("source")?.textValue() ?: "",
                    version = node.get("version")?.textValue() ?: "",
                    generatedAt = node.get("generatedAt")?.textValue() ?: "",
                    model = node.get("model")?.textValue() ?: "",
                    dependencies = node.get("dependencies")?.map { it.textValue() } ?: emptyList(),
                    type = node.get("type")?.textValue() ?: "Unknown"
                )
            }
        }
        fun fromFile(file: java.io.File): Metadata = fromJson(file.readText())

        fun now(): String = Instant.now().toString()
    }
}

data class QuizMetadata(
    override val source: String,
    override val version: String,
    override val generatedAt: String,
    override val model: String,
    override val dependencies: List<String>,
    val questions: Int,
    val bareme: Int = 20,
    val dureeMax: String = "30min"
) : Metadata() {
    override val type: String = "Quiz"
}

data class PlanMetadata(
    override val source: String,
    override val version: String,
    override val generatedAt: String,
    override val model: String,
    override val dependencies: List<String>,
    val epics: Int,
    val totalPoints: Int,
    val classification: String,
    val estimatedSessions: String
) : Metadata() {
    override val type: String = "Plan"
}

data class PDFMetadata(
    override val source: String,
    override val version: String,
    override val generatedAt: String,
    override val model: String,
    override val dependencies: List<String>,
    val pages: Int,
    val taille: String = "",
    val sourceCorpus: String = ""
) : Metadata() {
    override val type: String = "PDF"
}

data class UnknownMetadata(
    override val source: String,
    override val version: String,
    override val generatedAt: String,
    override val model: String,
    override val dependencies: List<String>,
    override val type: String
) : Metadata()
