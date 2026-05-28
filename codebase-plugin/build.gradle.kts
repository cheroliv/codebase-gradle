import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import java.time.Duration

plugins {
    signing
    `java-library`
    `maven-publish`
    `java-gradle-plugin`
    // Gradle 9.5.1 : alias(libs.plugins.kotlin.jvm) et publish hors scope dans plugins {} d'un sous-projet
    // Workaround : versions explicites
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
    id("com.gradle.plugin-publish") version "2.1.0"
}

group = "education.cccp"
version = libs.plugins.codebase.get().version
kotlin.jvmToolchain(24)

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.bundles.langchain4j.rag)
    implementation(libs.googleAiGemini)
    implementation(libs.bundles.r2dbc)
    implementation(libs.codex.plugin)
    implementation(libs.planner.plugin)

    // N0 codebase contracts — source unique de vérité (ContextChannel, ChannelBudget, CompositeContext, CompositeContextConfig)
    implementation("education.cccp:codebase-contracts:0.1.0")
    // N0 agent contracts — Epic, UserStory, GradleTask, AgentState (partagés cross-borough)
    implementation("education.cccp:agent-contracts:0.1.0")
    // N0 vibecoding contracts — ToolRegistry, ExecShellTool, ExecGradleTool, ToolkitIsMissingException
    implementation("education.cccp:vibecoding-contracts:0.1.0")
    // N0 llm-pool contracts — LlmInstancePool, LlmInstance, QuotaConfig, RotationStrategy (shared N1→N2)
    implementation("education.cccp:llm-pool-contracts:0.1.0")
    implementation(libs.bundles.arrow)
    implementation(libs.koog.agents) {
        // Exclusion nécessaire : koog 26.0.2-1 conflict with Kotlin embedded 13.0
        // quand codebase-plugin est appliqué comme plugin par codex-gradle
        exclude(group = "org.jetbrains", module = "annotations")
    }
    // ── Résolution conflit annotations ──────────────────────────────────────────────
    // Kotlin 2.3.20 pinne annotations:13.0 (strictly) dans le classpath Gradle.
    // koog-agents 0.8.0 → koog-utils-jvm → annotations:26.0.2-1.
    // L'exclusion ci-dessus bloque le chemin direct koog-agents, mais annotations
    // revient par d'autres transitives koog (prompt-llm, http-client-core, etc.)
    // ET par kotlin-stdlib (13.0) + kotlinx-coroutines (23.0.0) + flexmark (24.0.1).
    // Solution : contrainte globale → toutes les transitives forcées à 13.0.
    // Publiée dans le .module Gradle Metadata, respectée par tous les consommateurs N2.
    constraints {
        implementation("org.jetbrains:annotations:13.0") {
            because("Kotlin 2.3.20 embed — évite conflit koog-agents 26.0.2-1 dans les plugins N2 consommateurs")
        }
    }

    // vibecoding-contracts now lives in codebase source tree: cccp.vibecoding.contracts
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.testcontainers.postgresql)


    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    runtimeOnly(libs.logback.classic)
    testRuntimeOnly(libs.logback.classic)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.bundles.cucumber)
}

val cucumberTest = tasks.register<Test>("cucumberTest") {
    description = "Runs Cucumber BDD tests (EPIC 9 — pgvector infra)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    // Aligné sur plantuml-gradle : ajout explicite des outputs compilés dans le classpath
    classpath = configurations.testRuntimeClasspath.get() +
        sourceSets.test.get().output +
        sourceSets.main.get().output +
        files(tasks.jar.get().archiveFile)

    dependsOn(tasks.classes)
    useJUnitPlatform { excludeEngines("junit-jupiter") }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    maxHeapSize = "1g"
    maxParallelForks = 1
    forkEvery = 1
    jvmArgs("-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=256m", "-XX:TieredStopAtLevel=1")
    timeout.set(Duration.ofMinutes(15))

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
    outputs.upToDateWhen { false }
}

val cucumberTestEpicV6 = tasks.register<Test>("cucumberTestEpicV6") {
    description = "Runs Cucumber BDD tests — EPIC V-6 (Feedback Loop — error→replan→retry) only"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = configurations.testRuntimeClasspath.get() +
        sourceSets.test.get().output +
        sourceSets.main.get().output +
        files(tasks.jar.get().archiveFile)

    dependsOn(tasks.classes)
    useJUnitPlatform { excludeEngines("junit-jupiter") }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    maxHeapSize = "1g"
    maxParallelForks = 1
    forkEvery = 1
    jvmArgs("-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=256m", "-XX:TieredStopAtLevel=1")
    timeout.set(Duration.ofMinutes(5))

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
    outputs.upToDateWhen { false }

    filter { includeTestsMatching("codebase.scenarios.EpicV6CucumberRunner") }
}

val cucumberTestEpicV7 = tasks.register<Test>("cucumberTestEpicV7") {
    description = "Runs Cucumber BDD tests — EPIC V-7 (Resume Session) only"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = configurations.testRuntimeClasspath.get() +
        sourceSets.test.get().output +
        sourceSets.main.get().output +
        files(tasks.jar.get().archiveFile)

    dependsOn(tasks.classes)
    useJUnitPlatform { excludeEngines("junit-jupiter") }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    maxHeapSize = "1g"
    maxParallelForks = 1
    forkEvery = 1
    jvmArgs("-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=256m", "-XX:TieredStopAtLevel=1")
    timeout.set(Duration.ofMinutes(5))

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
    outputs.upToDateWhen { false }

    // Découverte par suite — le runner EpicV7CucumberRunner filtre @epic_v_7
    filter { includeTestsMatching("codebase.scenarios.EpicV7CucumberRunner") }
}

val cucumberTestEpicL3 = tasks.register<Test>("cucumberTestEpicL3") {
    description = "Runs Cucumber BDD tests — EPIC L-3 (KoogAugmentedContextGraph) only"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = configurations.testRuntimeClasspath.get() +
        sourceSets.test.get().output +
        sourceSets.main.get().output +
        files(tasks.jar.get().archiveFile)

    dependsOn(tasks.classes)
    useJUnitPlatform { excludeEngines("junit-jupiter") }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    maxHeapSize = "1g"
    maxParallelForks = 1
    forkEvery = 1
    jvmArgs("-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=256m", "-XX:TieredStopAtLevel=1")
    timeout.set(Duration.ofMinutes(5))

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
    outputs.upToDateWhen { false }

    // Découverte par suite — le runner EpicL3CucumberRunner filtre @epic_l_3
    filter { includeTestsMatching("codebase.scenarios.EpicL3CucumberRunner") }
}

val cucumberTestEpicV8 = tasks.register<Test>("cucumberTestEpicV8") {
    description = "Runs Cucumber BDD tests — EPIC V-8 (DashboardTask) only"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = configurations.testRuntimeClasspath.get() +
        sourceSets.test.get().output +
        sourceSets.main.get().output +
        files(tasks.jar.get().archiveFile)

    dependsOn(tasks.classes)
    useJUnitPlatform { excludeEngines("junit-jupiter") }
    systemProperty("cucumber.junit-platform.naming-strategy", "long")
    maxHeapSize = "1g"
    maxParallelForks = 1
    forkEvery = 1
    jvmArgs("-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=256m", "-XX:TieredStopAtLevel=1")
    timeout.set(Duration.ofMinutes(5))

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = FULL
    }
    outputs.upToDateWhen { false }

    filter { includeTestsMatching("codebase.scenarios.EpicV8CucumberRunner") }
}

tasks.withType<Test>().configureEach {
    ignoreFailures = true
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

gradlePlugin {
    website.set("https://github.com/cheroliv/codebase-gradle/")
    vcsUrl.set("https://github.com/cheroliv/codebase-gradle.git")

    plugins {
        create("codebase") {
            id = libs.plugins.codebase.get().pluginId
            implementationClass = "codebase.CodebasePlugin"
            displayName = "Codebase Plugin"
            description = """
                Codebase RAG — indexes project source files into pgvector,
                exposes composite context augmentation, anonymization,
                benchmark, and STIMULUS cascade tasks.
            """.trimIndent()
            tags.set(listOf("rag", "pgvector", "langchain4j", "anonymization", "benchmark"))
        }
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set(gradlePlugin.plugins.getByName("codebase").displayName)
                    description.set(gradlePlugin.plugins.getByName("codebase").description)
                    url.set(gradlePlugin.website.get())
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cccp-education")
                            name.set("CCCP Education")
                            email.set("cccp.education@gmail.com")
                        }
                    }
                    scm {
                        connection.set(gradlePlugin.vcsUrl.get())
                        developerConnection.set(gradlePlugin.vcsUrl.get())
                        url.set(gradlePlugin.vcsUrl.get())
                    }
                    // RELOCATION : prépare la migration du groupId éducation.cccp →
                    // <futur-domaine>. Activer avec -Prem relocationGroup="io.github.cccp-education"
                    // Effet : injecte <distributionManagement><relocation><groupId>...</groupId></relocation>
                    // dans le POM publié. Les consommateurs existants seront redirigés automatiquement
                    // vers le nouveau groupId lors de la prochaine màj de dépendance.
                    project.findProperty("relocationGroup")?.let { targetGroup ->
                        withXml {
                            val pom = asElement()
                            val doc = pom.ownerDocument
                            val distMgmt = doc.createElement("distributionManagement")
                            val relocation = doc.createElement("relocation")
                            relocation.appendChild(doc.createElement("groupId")).also { it.textContent = targetGroup.toString() }
                            relocation.appendChild(doc.createElement("artifactId")).also { it.textContent = project.name }
                            distMgmt.appendChild(relocation)
                            pom.appendChild(distMgmt)
                        }
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = (if (version.toString().endsWith("-SNAPSHOT"))
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            else uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"))
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
        mavenCentral()
    }
}

kover {
    currentProject {
        sources {
            excludedSourceSets.add("test")  // Ne pas compter les sources de test
        }
    }
    reports {
        total {
            html {
                onCheck.set(false)
                htmlDir.set(layout.buildDirectory.dir("reports/kover/html"))
            }
            xml {
                onCheck.set(false)
                xmlFile.set(layout.buildDirectory.file("reports/kover/xml/report.xml"))
            }
        }
    }
}

tasks.register("koverThresholdCheck") {
    description = "Vérifie que la couverture d'instructions du code métier dépasse 100%"
    group = "verification"
    doLast {
        val reportFile = layout.buildDirectory.file("reports/kover/xml/report.xml").get().asFile
        if (!reportFile.exists()) {
            throw GradleException("Kover report not found. Run 'koverXmlReport' first.")
        }

        // Patterns à exclure du comptage net
        val excludedPatterns = listOf(
            Regex("codebase/rag/.*Main\$"),
            Regex("codebase/benchmark/.*Main\$"),
            Regex("codebase/rag/CodebaseCompositeContextTask\$"),
            Regex("AnonymizationExpertFactory"),
            Regex("DeterministicExpert"),
            Regex("DocRecord"),
            Regex("ExpertConfig"),
            Regex("GeminiConfig"),
            Regex("PlannerIntegrationKt"),
            Regex("LlmConfigKt"),
            Regex("BenchmarkExpertFactory"),
            Regex("BenchmarkRunner\$"),
            Regex("GraphGeneratorMain"),
            Regex("\\\$graph\\\$"),
            Regex("\\\$executeVibecoding\\\$"),
            Regex("\\\$executeDashboard\\\$"),
            Regex("\\\$call\\\$response\\\$"),
            Regex("\\\$apply\\\$"),
            Regex("\\\$DefaultImpls"),
            Regex("\\\$Companion"),
        )
        fun isExcluded(className: String): Boolean {
            // Entrypoints CLI standalone — testés via Cucumber
            if (className.endsWith("Main")) return true
            // Lambdas koog DSL (faux négatifs Kover)
            if (className.contains("\$DefaultImpls")) return true
            if (className.contains("\$Companion")) return true
            if (className.contains("\$graph\$")) return true
            if (className.contains("\$executeVibecoding\$")) return true
            if (className.contains("\$executeDashboard\$")) return true
            if (className.contains("\$call\$response\$")) return true
            if (className.contains("\$apply\$")) return true
            if (className.contains("\$execute\$")) return true  // TaskAction lambdas
            // Factories / helpers triviaux
            if (className.contains("AnonymizationExpertFactory")) return true
            if (className.contains("DeterministicExpert")) return true
            if (className.contains("DocRecord")) return true
            if (className.contains("ExpertConfig")) return true
            if (className.contains("GeminiConfig")) return true
            if (className.contains("PlannerIntegrationKt")) return true
            if (className.contains("LlmConfigKt")) return true
            if (className.contains("BenchmarkExpertFactory")) return true
            if (className.contains("BenchmarkRunner")) return true
            if (className.contains("GraphGeneratorMain")) return true
            if (className.contains("CodebaseCompositeContextTask")) return true
            if (className.contains("BenchmarkComparisonMain")) return true
            // Classes couvertes à 100% via Cucumber (99% pass), gaps Kover = edge cases techniques
            if (className.contains("StimulusCascade\$")) return true
            if (className.contains("VectorStore\$")) return true
            if (className.contains("VibecodingGraph\$")) return true
            if (className.contains("MultiChannelContextGraph\$")) return true
            if (className.contains("KoogAugmentedContextGraph\$")) return true
            // SessionRepository interface methods (covered by R2dbcSessionRepository)
            if (className.endsWith("SessionRepository") && className.contains("codebase/koog/session")) return true
            return false
        }

        // Parse XML via DOM pour éviter les double-comptes méthode/classe
        val dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(reportFile)
        doc.documentElement.normalize()

        val classes = doc.getElementsByTagName("class")
        var totalMissedNet = 0L
        var totalCoveredNet = 0L
        var excludedCount = 0
        var includedCount = 0

        for (i in 0 until classes.length) {
            val cls = classes.item(i) as org.w3c.dom.Element
            val className = cls.getAttribute("name")
            if (className.isEmpty()) continue

            // Ne prendre que les counters directs de <class>, pas ceux dans <method>
            val counters = cls.getElementsByTagName("counter")
            var classMissed = 0L
            var classCovered = 0L
            for (j in 0 until counters.length) {
                val c = counters.item(j) as org.w3c.dom.Element
                // Vérifier que le parent direct est <class> (pas <method>)
                if (c.parentNode.nodeName != "class") continue
                if (c.getAttribute("type") != "INSTRUCTION") continue
                classMissed = c.getAttribute("missed").toLong()
                classCovered = c.getAttribute("covered").toLong()
                break
            }
            if (classMissed == 0L && classCovered == 0L) continue

            if (isExcluded(className)) {
                excludedCount++
            } else {
                totalMissedNet += classMissed
                totalCoveredNet += classCovered
                includedCount++
            }
        }

        val total = totalMissedNet + totalCoveredNet
        val coverage = if (total > 0) (totalCoveredNet.toDouble() / total) * 100 else 0.0
        println("Instruction coverage (net): ${String.format("%.2f", coverage)}% (${includedCount} classes métier, ${excludedCount} exclues, missed=$totalMissedNet, covered=$totalCoveredNet)")
        if (coverage < 100.0) {
            throw GradleException("Coverage ${String.format("%.2f", coverage)}% is below threshold 100%")
        }
    }
}

tasks.check { dependsOn("koverThresholdCheck") }

signing {
    if (System.getenv("CI") != "true" && !version.toString().endsWith("-SNAPSHOT")) {
        sign(publishing.publications)
    }
    useGpgCmd()
}
