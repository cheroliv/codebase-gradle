package codebase.rag

import org.slf4j.LoggerFactory
import java.io.File

object MetadataValidator {
    private val log = LoggerFactory.getLogger(MetadataValidator::class.java)

    sealed class ValidationResult {
        data class Valid(val metadata: Metadata) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    fun validate(
        file: File,
        expectedVersion: String? = null,
        expectedType: String? = null
    ): ValidationResult {
        if (!file.isFile) {
            return ValidationResult.Invalid("metadata.json introuvable : ${file.absolutePath}")
        }

        val metadata: Metadata = try {
            Metadata.fromFile(file)
        } catch (e: Exception) {
            return ValidationResult.Invalid("metadata.json illisible : ${e.message}")
        }

        if (expectedVersion != null) {
            val providedMajor = metadata.version.split(".").firstOrNull()?.toIntOrNull()
            val expectedMajor = expectedVersion.split(".").firstOrNull()?.toIntOrNull()
            if (providedMajor != null && expectedMajor != null && providedMajor != expectedMajor) {
                return ValidationResult.Invalid(
                    "Version incompatible : fourni=${metadata.version}, attendu majeure ${expectedVersion}"
                )
            }
        }

        if (expectedType != null && metadata.type != expectedType) {
            return ValidationResult.Invalid(
                "Type inattendu : fourni=${metadata.type}, attendu=$expectedType"
            )
        }

        for (dep in metadata.dependencies) {
            if (dep.isBlank()) {
                return ValidationResult.Invalid("Dépendance vide dans dependencies[]")
            }
        }

        log.info("MetadataValidator: {} validé — source={}, type={}, version={}, deps={}",
            file.name, metadata.source, metadata.type, metadata.version, metadata.dependencies.size)

        return ValidationResult.Valid(metadata)
    }

    fun isVersionCompatible(provided: String, expected: String): Boolean {
        val providedMajor = provided.split(".").firstOrNull()?.toIntOrNull() ?: return false
        val expectedMajor = expected.split(".").firstOrNull()?.toIntOrNull() ?: return false
        return providedMajor == expectedMajor
    }
}
