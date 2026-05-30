package codebase

import codebase.koog.VibecodingTask
import codebase.koog.tracking.DashboardTask
import codebase.ocr.CodebaseOcrExtension
import codebase.ocr.OcrTask
import codebase.quality.QualityGateTask
import codebase.rag.AssembleWorkspaceContextTask
import codebase.rag.CodebaseCompositeContextTask
import codebase.rag.PlanIntentionTask
import codebase.rag.PrepareContextTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodebasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ocrExt = project.extensions.create("codebaseOcr", CodebaseOcrExtension::class.java)
        ocrExt.ocrProvider.convention("gemini")
        ocrExt.geminiModel.convention("gemini-2.5-flash")
        ocrExt.ocrLanguage.convention("fr")
        ocrExt.outputFormat.convention("asciidoc")
        ocrExt.ocrEnabled.convention(false)
        ocrExt.maxTokens.convention(8192)
        ocrExt.inputDir.convention(project.layout.projectDirectory)

        project.tasks.register(
            "collectFromCodebase",
            PrepareContextTask::class.java
        ) {
            it.group = "collect"
            it.description = "Collects per-borough augmented context [REGLES_EAGER]/[CONTEXTE_RAG]/[RELATIONS_GRAPHIFY] into build/context/{name}.context.txt"
            it.workspaceRoot.set(project.rootDir)
            it.projectName.set(project.rootDir.name)
            it.ragQuestion.set(project.providers.gradleProperty("ragQuestion").orElse("architecture du workspace"))
            it.outputFile.set(project.layout.buildDirectory.file("context/${project.rootDir.name}.context.txt"))
        }

        project.tasks.register(
            "collectCompositeContext",
            AssembleWorkspaceContextTask::class.java
        ) {
            it.group = "collect"
            it.description = "Assembles all build/context/*.context.txt from foundry/ boroughs into a workspace-level composite"
            it.foundryDir.set(project.rootDir.parentFile.resolve("foundry/public"))
            it.outputFile.set(project.layout.buildDirectory.file("context/workspace-context.txt"))
        }

        project.tasks.register(
            "generatePlan",
            PlanIntentionTask::class.java
        ) {
            it.group = "generate"
            it.description = "Augmented Planning — classifies intention (pro/flash) then decomposes into EPICs/UserStories/Tasks using CompositeContext (EAGER+RAG+Graphify)"
            it.intention.set(project.providers.gradleProperty("intention").orElse(""))
            it.workspaceRoot.set(project.rootDir)
            it.ragQuestion.set(project.providers.gradleProperty("ragQuestion").orElse("architecture du workspace"))
            it.outputFile.set(project.layout.buildDirectory.file("plans/${project.rootDir.name}-plan.txt"))
        }

        project.tasks.register(
            "vibecode",
            VibecodingTask::class.java
        ) {
            it.group = "generate"
            it.description = "Vibecoding agent — koog autonomous loop (context → plan → execute → audit)"
            it.workspaceRoot.set(project.rootDir)
            it.intention.set(project.providers.gradleProperty("intention").orElse(""))
            it.dryRun.set(project.providers.gradleProperty("dryRun").map { it.toBoolean() }.orElse(false))
            it.maxActions.set(project.providers.gradleProperty("maxActions").map { it.toInt() }.orElse(10))
        }

        project.tasks.register(
            "vibecodingDashboard",
            DashboardTask::class.java
        ) {
            it.group = "tracking"
            it.description = "Dashboard vibecoding — résumé sessions, coûts tokens, filtres confidentialité"
        }

        project.tasks.register(
            "qualityGate",
            QualityGateTask::class.java
        ) {
            it.group = "validate"
            it.description = "Quality gate — validates expert LLM outputs (sentiment + off-topic + PII residual checks)"
            it.output.set(project.providers.gradleProperty("output").orElse(""))
            it.domain.set(project.providers.gradleProperty("domain").orElse("CDA"))
            it.minAcceptableScore.set(project.providers.gradleProperty("minScore").map { it.toDouble() }.orElse(0.60))
            it.enableSentimentCheck.set(project.providers.gradleProperty("enableSentiment").map { it.toBoolean() }.orElse(true))
            it.enableOffTopicCheck.set(project.providers.gradleProperty("enableOffTopic").map { it.toBoolean() }.orElse(true))
            it.enablePiiCheck.set(project.providers.gradleProperty("enablePii").map { it.toBoolean() }.orElse(true))
        }

        val trainingPluginDir = project.rootDir.parentFile.parentFile
            .resolve("private/training-gradle/training-plugin")
        project.tasks.register(
            "generateCompositeContext",
            CodebaseCompositeContextTask::class.java
        ) { task ->
            task.group = "generate"
            task.description = "Contexte composite N1+N2 : CodexVectorStore (codex) + training-gradle (AFNOR/REAC) → composite-context.json"
            task.query.set(project.providers.gradleProperty("query").orElse("architecture du workspace"))
            task.topK.set(project.providers.gradleProperty("topK").orElse("10"))
            task.trainingProjectDir.set(trainingPluginDir.absolutePath)
            task.outputFile.set(project.layout.buildDirectory.file("codebase/composite-context.json"))
        }

        project.tasks.register(
            "ocrDocument",
            OcrTask::class.java
        ) { task ->
            task.group = "collect"
            task.description = "OCR assisté IA — extrait le texte structuré d'un document scanné via Gemini Vision"
            task.ocrProvider.set(
                project.providers.gradleProperty("ocrProvider").orElse(ocrExt.ocrProvider)
            )
            task.ocrLanguage.set(
                project.providers.gradleProperty("ocrLanguage").orElse(ocrExt.ocrLanguage)
            )
            task.geminiModel.set(
                project.providers.gradleProperty("geminiModel").orElse(ocrExt.geminiModel)
            )
            task.maxTokens.set(
                project.providers.gradleProperty("maxTokens").map { it.toInt() }.orElse(ocrExt.maxTokens)
            )
            task.outputFormat.set(
                project.providers.gradleProperty("outputFormat").orElse(ocrExt.outputFormat)
            )
        }
    }
}
