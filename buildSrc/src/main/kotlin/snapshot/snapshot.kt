package snapshot

import codebase.CodebaseYmlAnonymizer
import codebase.CodebaseYmlConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.logging.Logger
import readme.GitConfig
import readme.ReadmeYmlAnonymizer
import readme.ReadmeYmlConfig
import readme.resolvedToken
import site.SiteYmlAnonymizer
import site.SiteYmlConfig
import slider.SliderYmlAnonymizer
import slider.SliderYmlConfig
import java.io.File

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
     * All Config/Anonymizer instances are provided by the caller (no global singletons).
     */
    fun ObjectMapper.renderFileSection(
        file: File,
        root: File,
        readmeCfg: ReadmeYmlConfig,
        readmeAnon: ReadmeYmlAnonymizer,
        sliderCfg: SliderYmlConfig,
        sliderAnon: SliderYmlAnonymizer,
        siteCfg: SiteYmlConfig,
        siteAnon: SiteYmlAnonymizer,
        codebaseCfg: CodebaseYmlConfig,
        codebaseAnon: CodebaseYmlAnonymizer
    ): String = buildString {
        val relPath = file.relativeTo(root).path
        appendLine("== $relPath")
        appendLine()
        appendLine("[source,${file.asciidocLang()}]")
        appendLine("----")
        val content = when (file.name) {
            readmeCfg.CONFIG_FILE_NAME -> {
                val cfg = with(readmeCfg) { root.loadReadmeConfiguration() }
                with(readmeAnon) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            sliderCfg.CONFIG_FILE_NAME -> {
                val cfg = with(sliderCfg) { root.loadSliderConfiguration() }
                with(sliderAnon) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            siteCfg.CONFIG_FILE_NAME -> {
                val cfg = with(siteCfg) { root.loadSiteConfiguration() }
                with(siteAnon) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            codebaseCfg.CONFIG_FILE_NAME -> {
                val cfg = with(codebaseCfg) { root.loadCodebaseConfiguration() }
                with(codebaseAnon) { cfg.toAnonymizedYaml(this@renderFileSection) }
            }

            else -> file.readText()
        }
        appendLine(content)
        appendLine("----")
        appendLine()
    }

    /**
     * Assembles the full AsciiDoc document from [root].
     * All Config/Anonymizer instances are created locally — no global singletons.
     */
    fun ObjectMapper.buildAdoc(
        root: File,
        readmeCfg: ReadmeYmlConfig,
        readmeAnon: ReadmeYmlAnonymizer,
        sliderCfg: SliderYmlConfig,
        sliderAnon: SliderYmlAnonymizer,
        siteCfg: SiteYmlConfig,
        siteAnon: SiteYmlAnonymizer,
        codebaseCfg: CodebaseYmlConfig,
        codebaseAnon: CodebaseYmlAnonymizer
    ): String {
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
            sourceFiles.forEach {
                append(
                    renderFileSection(
                        it, root,
                        readmeCfg, readmeAnon,
                        sliderCfg, sliderAnon,
                        siteCfg, siteAnon,
                        codebaseCfg, codebaseAnon
                    )
                )
            }
        }
    }

    /**
     * Entry point — generates snapshot.adoc under [this] root directory.
     * All Config/Anonymizer instances are created locally in doLast scope —
     * no global singletons, no cascade initialization at package load time.
     */
    fun File.generate(logger: Logger): List<File> {
        val mapper       = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val readmeCfg    = ReadmeYmlConfig()
        val readmeAnon   = ReadmeYmlAnonymizer()
        val sliderCfg    = SliderYmlConfig()
        val sliderAnon   = SliderYmlAnonymizer()
        val siteCfg      = SiteYmlConfig()
        val siteAnon     = SiteYmlAnonymizer()
        val codebaseCfg  = CodebaseYmlConfig()
        val codebaseAnon = CodebaseYmlAnonymizer()

        val sourceFiles = collectFiles()

        logger.lifecycle("── project tree ────────────────────────────────────")
        logger.lifecycle(buildTreeView())
        logger.lifecycle("── collected files (${sourceFiles.size}) ──────────────────────────")
        sourceFiles.forEach { logger.lifecycle("  ${it.relativeTo(this).path}") }

        File(this, "snapshot.adoc").writeText(
            mapper.buildAdoc(
                this,
                readmeCfg, readmeAnon,
                sliderCfg, sliderAnon,
                siteCfg, siteAnon,
                codebaseCfg, codebaseAnon
            )
        )

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ snapshot.adoc generated — ${sourceFiles.size} file(s) captured")
        logger.lifecycle("   → ${File(this, "snapshot.adoc").absolutePath}")

        return sourceFiles
    }
}

val snapshotManager = SnapshotManager()