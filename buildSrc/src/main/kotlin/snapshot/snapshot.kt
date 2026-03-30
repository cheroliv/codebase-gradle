package snapshot

import codebase.codebaseYmlAnonymizer
import codebase.codebaseYmlConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.logging.Logger
import readme.GitConfig
import readme.readmeYmlAnonymizer
import readme.readmeYmlConfig
import site.siteYmlAnonymizer
import site.siteYmlConfig
import slider.sliderYmlAnonymizer
import slider.sliderYmlConfig
import java.io.File
import kotlin.collections.forEach

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
                val cfg = with(readmeYmlConfig) { root.loadReadmeConfiguration() }
                with(readmeYmlAnonymizer) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            sliderYmlConfig.CONFIG_FILE_NAME -> {
                val cfg = with(sliderYmlConfig) { root.loadSliderConfiguration() }
                with(sliderYmlAnonymizer) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            siteYmlConfig.CONFIG_FILE_NAME -> {
                val cfg = with(siteYmlConfig) { root.loadSiteConfiguration() }
                with(siteYmlAnonymizer) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            codebaseYmlConfig.CONFIG_FILE_NAME -> {
                val cfg = with(codebaseYmlConfig) { root.loadCodebaseConfiguration() }
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

