import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version "2.3.20"
    application
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

application.mainClass = "chatbot.ChatbotFrame"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

dependencies {
    implementation(libs.bundles.langchain4j.rag)
    implementation(libs.bundles.r2dbc)
    implementation(libs.bundles.arrow)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.testcontainers.postgresql)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)

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
    classpath = sourceSets.test.get().runtimeClasspath

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
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.findByPath("run")!!.group = "codebase"
tasks.findByPath("run")!!.description = "Launch plumbery then swing chatbot"
tasks.findByPath("run")!!.doFirst { println("Launch plumbery.") }

apply(from = "gradle/rag-bench.gradle.kts")
apply(from = "gradle/anonymization.gradle.kts")
