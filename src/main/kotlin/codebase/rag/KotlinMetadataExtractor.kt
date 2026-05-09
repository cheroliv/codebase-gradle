package codebase.rag

data class KotlinMetadata(
    val packageName: String?,
    val className: String?,
    val repoName: String
)

class KotlinMetadataExtractor(private val repoName: String) {

    fun extract(filePath: String, content: String): KotlinMetadata {
        val packageName = extractPackageName(content)
        val className = extractClassName(content)
            ?: java.io.File(filePath).nameWithoutExtension
        return KotlinMetadata(packageName = packageName, className = className, repoName = repoName)
    }

    private fun extractPackageName(content: String): String? {
        val packageRegex = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
        return packageRegex.find(content)?.groupValues?.get(1)
    }

    private fun extractClassName(content: String): String? {
        val classRegex = Regex("""^\s*(?:data\s+)?(?:sealed\s+)?(?:open\s+)?(?:abstract\s+)?(?:enum\s+)?(?:inner\s+)?class\s+(\w+)""", RegexOption.MULTILINE)
        val objectRegex = Regex("""^\s*(?:data\s+)?object\s+(\w+)""", RegexOption.MULTILINE)
        val interfaceRegex = Regex("""^\s*(?:fun\s+)?interface\s+(\w+)""", RegexOption.MULTILINE)

        val allMatches = mutableListOf<String>()
        classRegex.findAll(content).mapTo(allMatches) { it.groupValues[1] }
        objectRegex.findAll(content).mapTo(allMatches) { it.groupValues[1] }
        interfaceRegex.findAll(content).mapTo(allMatches) { it.groupValues[1] }

        return allMatches.lastOrNull()
    }
}
