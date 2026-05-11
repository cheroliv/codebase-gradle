import benchmark.*
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec

val jav = project.extensions.getByType(JavaPluginExtension::class.java)
val runtime = jav.sourceSets.getByName("main").runtimeClasspath

val pgUrl = project.providers.gradleProperty("pgvector.jdbc.url")
    .orElse(project.providers.environmentVariable("PGVECTOR_JDBC_URL"))
    .orElse("jdbc:postgresql://localhost:5432/codebase_rag")
val pgUser = project.providers.gradleProperty("pgvector.user")
    .orElse(project.providers.environmentVariable("PGVECTOR_USER"))
    .orElse("codebase")
val pgPass = project.providers.gradleProperty("pgvector.password")
    .orElse(project.providers.environmentVariable("PGVECTOR_PASSWORD"))
    .orElse("codebase")

project.tasks.register<JavaExec>("indexCodebase") {
    group = "codebase"
    description = "Indexes project source files into pgvector for RAG augmentation"
    classpath = runtime
    mainClass = "codebase.rag.CodebaseIndexerMain"
    doFirst {
        environment("PGVECTOR_JDBC_URL", pgUrl.get())
        environment("PGVECTOR_USER", pgUser.get())
        environment("PGVECTOR_PASSWORD", pgPass.get())
    }
}

project.tasks.register<JavaExec>("queryCodebase") {
    group = "codebase"
    description = "Queries pgvector for semantically relevant chunks"
    classpath = runtime
    mainClass = "codebase.rag.VectorQueryMain"
    doFirst {
        environment("PGVECTOR_JDBC_URL", pgUrl.get())
        environment("PGVECTOR_USER", pgUser.get())
        environment("PGVECTOR_PASSWORD", pgPass.get())
    }
    val query = project.providers.gradleProperty("query")
        .orElse(project.providers.environmentVariable("VECTOR_QUERY"))
        .orElse("")
    val topK = project.providers.gradleProperty("topK").map { it.toIntOrNull() ?: 10 }.orElse(10)
    val fileType = project.providers.gradleProperty("fileType").map { it.ifBlank { "" } }
        .orElse(project.providers.environmentVariable("FILE_TYPE"))
        .orElse("")
    args(query.get(), topK.get().toString(), fileType.get())
}

project.tasks.register<JavaExec>("runBenchmark") {
    group = "codebase"
    description = "Runs EPIC 4 spatial perception benchmark for a given scenario (default: BASELINE)"
    classpath = runtime
    mainClass = "codebase.benchmark.BenchmarkRunnerMain"
    doFirst {
        environment("PGVECTOR_JDBC_URL", pgUrl.get())
        environment("PGVECTOR_USER", pgUser.get())
        environment("PGVECTOR_PASSWORD", pgPass.get())
    }
    val scenario = project.providers.gradleProperty("scenario").orElse("BASELINE")
    args(scenario.get())
}

project.tasks.register<JavaExec>("generateGraph") {
    group = "codebase"
    description = "Generates graph.json knowledge graph of the workspace"
    classpath = runtime
    mainClass = "codebase.benchmark.GraphGeneratorMain"
    val outputFile = project.providers.gradleProperty("graphOutput").orElse("build/graph.json")
    args(outputFile.get())
}

project.tasks.register<JavaExec>("exportBenchmarkReport") {
    group = "codebase"
    description = "Converts an existing benchmark JSON report to AsciiDoc"
    classpath = runtime
    mainClass = "codebase.benchmark.BenchmarkReportExportMain"
    val scenario = project.providers.gradleProperty("scenario").orElse("BASELINE")
    val inputFile = project.providers.gradleProperty("inputFile")
        .orElse("build/benchmark-reports/report-${scenario.get()}.json")
    args(scenario.get(), inputFile.get())
}

project.tasks.register<JavaExec>("augmentContext") {
    group = "codebase"
    description = "Builds composite context (EAGER + RAG pgvector + Graphify) for opencode augmentation"
    classpath = runtime
    mainClass = "codebase.rag.CompositeContextBuilderMain"
    doFirst {
        environment("PGVECTOR_JDBC_URL", pgUrl.get())
        environment("PGVECTOR_USER", pgUser.get())
        environment("PGVECTOR_PASSWORD", pgPass.get())
    }
    val ragQuestion = project.providers.gradleProperty("ragQuestion")
        .orElse("architecture du workspace")
    args(ragQuestion.get())
}

project.tasks.register<JavaExec>("injectOpencode") {
    group = "codebase"
    description = "Injects composite context with [REGLES_EAGER]/[CONTEXTE_RAG]/[RELATIONS_GRAPHIFY] headers into /tmp/opencode-context.txt"
    classpath = runtime
    mainClass = "codebase.rag.OpencodeInjectorMain"
    doFirst {
        environment("PGVECTOR_JDBC_URL", pgUrl.get())
        environment("PGVECTOR_USER", pgUser.get())
        environment("PGVECTOR_PASSWORD", pgPass.get())
    }
    val ragQuestion = project.providers.gradleProperty("ragQuestion")
        .orElse("architecture du workspace")
    args(ragQuestion.get())
}

project.tasks.register<JavaExec>("augmentOpencode") {
    group = "codebase"
    description = "Full pipeline: walk→index→query→format→/tmp/opencode-context.txt for opencode augmentation"
    classpath = runtime
    mainClass = "codebase.rag.AugmentOpencodeMain"
    doFirst {
        environment("PGVECTOR_JDBC_URL", pgUrl.get())
        environment("PGVECTOR_USER", pgUser.get())
        environment("PGVECTOR_PASSWORD", pgPass.get())
    }
    val ragQuestion = project.providers.gradleProperty("ragQuestion")
        .orElse("architecture du workspace")
    args(ragQuestion.get())
}

project.tasks.register<JavaExec>("benchmarkPertinence") {
    group = "codebase"
    description = "US-9.13 Benchmarks pertinence des reponses LLM avec/sans vecteur composite (gate MVP0)"
    classpath = runtime
    mainClass = "codebase.rag.PertinenceBenchmarkMain"
    doFirst {
        environment("PGVECTOR_JDBC_URL", pgUrl.get())
        environment("PGVECTOR_USER", pgUser.get())
        environment("PGVECTOR_PASSWORD", pgPass.get())
    }
    val outputDir = project.providers.gradleProperty("outputDir").orElse("build/pertinence-reports")
    args(outputDir.get())
}

project.tasks.register<JavaExec>("classifyVisionOpinion") {
    group = "codebase"
    description = "US-9.14 Classifies 10 test sections as VISION or OPINION via LLM prompt engineering"
    classpath = runtime
    mainClass = "codebase.rag.VisionOpinionClassifierMain"
    val outputDir = project.providers.gradleProperty("outputDir").orElse("build/vision-opinion-reports")
    args(outputDir.get())
}

project.tasks.register("benchmarkProtocol") {
    group = "codebase"
    description = "Displays EPIC 4 measurement protocol"
    doLast {
        val cfg = BenchmarkConfig()
        println("═══ EPIC 4 — Benchmark de perception spatiale LLM ═══")
        BenchmarkProtocol.CIRCLE_LABELS.forEach { (_, label) -> println("  $label") }
        cfg.thresholds.forEach { t -> println("  ${t.label} — ${t.size} tokens") }
        cfg.scenarios.forEach { s ->
            val channels = if (s.channels.isEmpty()) "(baseline brute, zéro canal)" else s.channels.joinToString(" + ")
            println("  ${s.id} | $channels | ${s.description}")
        }
        println("── Output : build/benchmark-reports/")
    }
}
