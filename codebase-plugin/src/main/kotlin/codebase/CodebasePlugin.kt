package codebase

import codebase.koog.VibecodingTask
import codebase.rag.AssembleWorkspaceContextTask
import codebase.rag.PlanIntentionTask
import codebase.rag.PrepareContextTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodebasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
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
    }
}
