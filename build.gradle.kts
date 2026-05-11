import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import java.time.Duration

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2")
        classpath("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
        classpath("jakarta.validation:jakarta.validation-api:3.1.0")
        classpath("org.hibernate.validator:hibernate-validator:8.0.1.Final")
        classpath("org.glassfish.expressly:expressly:5.0.0")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
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
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

dependencies {
    implementation(libs.bundles.langchain4j.rag)
    implementation(libs.bundles.r2dbc)
    implementation(libs.bundles.arrow)
    implementation(libs.jackson.module.kotlin)
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
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.findByPath("run")!!.group = "codebase"
tasks.findByPath("run")!!.description = "Launch plumbery then swing chatbot"
tasks.findByPath("run")!!.doFirst { println("Launch plumbery.") }

apply(from = "gradle/rag-bench.gradle.kts")
apply(from = "gradle/anonymization.gradle.kts")
