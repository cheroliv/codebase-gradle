package codebase.rag

object YamlConfigAnonymizer {

    private val sensitiveFragments = listOf(
        "key", "token", "password", "pass", "pwd",
        "secret", "credential", "authorization", "auth"
    )

    fun anonymizeYaml(text: String): String = text.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("#") -> line
            trimmed.contains(':') -> {
                val colonIdx = line.indexOf(':')
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim()
                val isSensitive = sensitiveFragments.any { fragment ->
                    key.contains(fragment, ignoreCase = true)
                }
                if (isSensitive && value.isNotEmpty() && value != "***" && value != "\"\"" && value != "[]" && value != "{}") {
                    line.substring(0, colonIdx + 1) + " ***"
                } else line
            }
            else -> line
        }
    }

    fun anonymizeJson(text: String): String = text.replace(
        Regex(""""([^"]*)"\s*:\s*"([^"]*)"""")
    ) { match ->
        val key = match.groupValues[1]
        val value = match.groupValues[2]
        val isSensitive = sensitiveFragments.any { fragment ->
            key.contains(fragment, ignoreCase = true)
        }
        if (isSensitive && value.isNotEmpty() && value != "***") {
            "\"${key}\": \"***\""
        } else match.value
    }

    fun anonymize(text: String, extension: String): String = when (extension.lowercase()) {
        "yml", "yaml" -> anonymizeYaml(text)
        "json" -> anonymizeJson(text)
        else -> text
    }

    fun verifyNoSecrets(text: String): List<String> {
        val findings = mutableListOf<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            val colonIdx = line.indexOf(':')
            if (colonIdx < 0) continue
            val key = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim()
            val isSensitive = sensitiveFragments.any { fragment ->
                key.contains(fragment, ignoreCase = true)
            }
            if (isSensitive && value.isNotEmpty() && value != "***" && value.length > 2 && !value.startsWith("#")) {
                findings.add("line: $trimmed")
            }
        }
        return findings
    }
}
