package codebase.rag

import vibecoding.contracts.plan.Plan
import vibecoding.contracts.state.AugmentedState
import codebase.koog.KoogAugmentedContextGraph
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "LLM output is probabilistic — never cache")
abstract class PlanIntentionTask : DefaultTask() {

    @get:Input
    abstract val intention: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workspaceRoot: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val ragQuestion: Property<String>

    /**
     * L-3 : délégué à [KoogAugmentedContextGraph] — le pipeline buildContext → classify → plan
     * est exécuté nativement par le wrapper koog+langchain4j. Zéro duplication avec les nœuds du graphe.
     */
    @TaskAction
    fun execute() {
        val intent = intention.get()
        val rootDir = workspaceRoot.get().asFile
        val question = ragQuestion.getOrElse(intent)
        val output = outputFile.get().asFile

        logger.lifecycle("[generatePlan] Intention : {}", intent)
        logger.lifecycle("[generatePlan] Workspace : {}", rootDir.absolutePath)

        // Construire l'état initial
        val initialState = AugmentedState(
            intention = intent,
            workspaceRoot = rootDir.absolutePath
        )

        // Exécuter le pipeline KoogAugmentedContextGraph (buildContext → classify → plan)
        val graph = KoogAugmentedContextGraph()
        val resultState = graph.execute(initialState)

        logger.lifecycle(
            "[generatePlan] Classification : {} (error={}, planError={})",
            resultState.classification,
            resultState.error ?: "none",
            resultState.planError ?: "none"
        )

        // Générer le metadata.json du plan (méta-communication typée Niveau 2)
        val planMetadata = resultState.toPlanMetadata(rootDir.name)
        if (planMetadata != null) {
            val metadataFile = output.parentFile.resolve("${rootDir.name}-plan-metadata.json")
            metadataFile.parentFile.mkdirs()
            val jsonMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
            metadataFile.writeText(jsonMapper.writeValueAsString(planMetadata))
            logger.lifecycle("[generatePlan] metadata.json écrit dans {}", metadataFile.absolutePath)

            // Vérifier le contrat
            val validation = MetadataValidator.validate(metadataFile, expectedVersion = "1.0", expectedType = "Plan")
            when (validation) {
                is MetadataValidator.ValidationResult.Valid -> logger.lifecycle("[generatePlan] Contrat metadata.json validé ✅")
                is MetadataValidator.ValidationResult.Invalid -> logger.warn("[generatePlan] Contrat metadata.json INVALIDE ⚠️ : {}", validation.reason)
            }
        }

        // Formater la sortie
        val formattedOutput = formatOutput(intent, resultState)
        output.parentFile.mkdirs()
        output.writeText(formattedOutput)

        logger.lifecycle("[generatePlan] Plan écrit dans {} ({} bytes)", output.absolutePath, output.length())

        // Afficher un résumé sur stdout
        println()
        println("=" .repeat(60))
        println("PLAN — $intent")
        println("=" .repeat(60))
        println(formattedOutput)
        println("=" .repeat(60))
    }

    private fun formatOutput(intention: String, result: AugmentedState): String {
        val sb = StringBuilder()
        sb.appendLine("=" .repeat(60))
        sb.appendLine("PLAN — $intention")
        sb.appendLine("Classification : ${result.classification}")
        sb.appendLine("=" .repeat(60))
        sb.appendLine()

        if (result.error != null && result.plan == null) {
            sb.appendLine("ERREUR : ${result.error}")
            sb.appendLine()
            sb.appendLine("JSON brut reçu du LLM :")
            sb.appendLine(result.planJson)
        } else {
            val plan = result.plan
            if (plan != null) {
            sb.appendLine("Titre    : ${plan.title}")
            sb.appendLine("EPICs    : ${plan.epics.size}")
            sb.appendLine("Points   : ${plan.totalPoints}")
            sb.appendLine("Sessions : ${plan.estimatedSessions}")
            sb.appendLine()
            for ((epicIdx, epic) in plan.epics.withIndex()) {
                sb.appendLine("--- EPIC ${epicIdx + 1} : ${epic.name} (${epic.points} pts) ---")
                sb.appendLine("  ${epic.description}")
                sb.appendLine()
                for ((usIdx, us) in epic.userStories.withIndex()) {
                    sb.appendLine("  US-${epicIdx + 1}.${usIdx + 1} : ${us.description}")
                    for (task in us.tasks) {
                        sb.appendLine("    - ${task.description}")
                        sb.appendLine("      ${task.gradleTask}")
                    }
                    sb.appendLine()
                }
            }
            } else {
            sb.appendLine("Aucun plan généré (ni erreur, ni résultat).")
            if (result.planError != null) {
                sb.appendLine("Cause : ${result.planError}")
            }
            }
        }
        return sb.toString()
    }

    private fun indexWorkspaceSources(rootDir: File, store: VectorStore, boroughName: String) {
        logger.lifecycle("[generatePlan] Indexation des sources de {}...", boroughName)
        val walker = codebase.walker.WorkspaceWalker(rootDir)
        val sourceExtensions = listOf("kt", "adoc", "kts")
        val configExtensions = listOf("yml", "yaml", "json")
        val files = walker.walk().filter { it.extension in sourceExtensions + configExtensions }

        val metadataExtractor = KotlinMetadataExtractor(repoName = boroughName)
        var totalChunks = 0

        for (wf in files) {
            val file = File(wf.filePath)
            val rawText = try {
                file.readText()
            } catch (e: Exception) {
                logger.warn("Cannot read {}: {}", wf.filePath, e.message)
                continue
            }
            val text = if (wf.extension in configExtensions) {
                YamlConfigAnonymizer.anonymize(rawText, wf.extension)
            } else rawText

            val chunks = ChunkTokenizer.splitIntoSentenceLevelChunks(text)
            val metadata = if (wf.extension == "kt") {
                metadataExtractor.extract(wf.filePath, rawText)
            } else {
                KotlinMetadata(packageName = null, className = null, repoName = boroughName)
            }
            store.insertDocument(wf.fileName, wf.filePath, wf.fileSize, chunks, metadata.packageName, metadata.className, metadata.repoName)
            totalChunks += chunks.size
        }

        if (totalChunks > 0) {
            val allRecords = store.fetchAllChunks()
            val pipeline = EmbeddingPipeline(store)
            pipeline.embedAll(allRecords)
        }
        logger.lifecycle("[generatePlan] Indexés : {} documents, {} chunks", store.countDocuments(), store.countChunks())
    }
}
