package codebase.scenarios

import codebase.rag.*
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime

class StimulusCascadeSteps {

    private val log = LoggerFactory.getLogger(StimulusCascadeSteps::class.java)
    private var tmpDir: File? = null
    private var dilutionResult: DilutionResult? = null
    private var dilutionRecord: DilutionRecord? = null
    private var detector: StimulusDetector? = null
    private var activeStimuli: List<ActiveStimulus> = emptyList()
    private var staleStimuli: List<ActiveStimulus> = emptyList()
    private lateinit var initialContent: String
    private lateinit var docFile: File

    @Given("a temporary workspace directory for STIMULUS tests")
    fun createTempWorkspace() {
        val path = Files.createTempDirectory("graphify-stimulus-test")
        tmpDir = path.toFile()
        log.info("Temporary workspace: {}", tmpDir!!.absolutePath)
    }

    @Given("the target document {string} already contains the section {string}")
    fun createDocWithSection(filename: String, section: String) {
        docFile = File(tmpDir, filename)
        val content = """
= Test Document
:jbake-status: draft

== Introduction

First section.

$section

=== Stimuli diluted in this document

[cols="1,1,3"]
|===
| Stimulus | Date | Enriched sections
| STIMULUS_EXISTANT.adoc | 10 May 2026 | Existing section
|===

""".trimStart()
        docFile.writeText(content, Charsets.UTF_8)
        log.info("Document created: {} ({} characters)", docFile.absolutePath, docFile.length())
    }

    @Given("the target document {string} does not contain a traceability table")
    fun createDocWithoutTraceability(filename: String) {
        docFile = File(tmpDir, filename)
        val content = """
= Document Without Traceability
:jbake-status: draft

== Introduction

Initial content.

== Nouveau Test

""".trimStart()
        docFile.writeText(content, Charsets.UTF_8)
    }

    @Given("the target document {string} has initial content")
    fun createDocWithInitialContent(filename: String) {
        docFile = File(tmpDir, filename)
        val content = """
= Initial Document
:jbake-status: draft

== Architecture

Important original content.

== Principes

Nine founding principles.

""".trimStart()
        docFile.writeText(content, Charsets.UTF_8)
        initialContent = content
    }

    @When("a VISION section is diluted into {string} in section {string}")
    fun diluteVisionSection(filename: String, section: String) {
        val executor = DilutionExecutor(tmpDir!!.absolutePath)
        val targetDoc = TargetDocument.entries.find { it.path == filename } ?: TargetDocument.WORKSPACE_VISION

        dilutionRecord = DilutionRecord(
            sectionId = "S1",
            sectionTitle = "Section de test",
            content = "This is the test section content.\n\nContent enriched by STIMULUS.",
            classification = ContentClassification.VISION,
            confidence = 0.95,
            dilutionTarget = DilutionTarget(
                targetDocument = targetDoc,
                suggestedSection = section,
                rationale = "Test routing rationale"
            ),
            classificationRationale = "Test classification rationale",
            timestamp = LocalDateTime.now()
        )

        dilutionResult = dilutionRecord?.let { executor.execute(it) }
        log.info("Dilution result: success={} error={}", dilutionResult?.success, dilutionResult?.error)
    }

    @When("a VISION section is diluted in DRY RUN mode into {string}")
    fun diluteVisionDryRun(filename: String) {
        val executor = DilutionExecutor(tmpDir!!.absolutePath, dryRun = true)

        dilutionRecord = DilutionRecord(
            sectionId = "S3",
            sectionTitle = "Section dry run",
            content = "Content that must not be injected.",
            classification = ContentClassification.VISION,
            confidence = 0.90,
            dilutionTarget = DilutionTarget(
                targetDocument = TargetDocument.WORKSPACE_ORGANIZATION,
                suggestedSection = "== Principes",
                rationale = "Test dry run routing"
            ),
            classificationRationale = "Test dry run classification",
            timestamp = LocalDateTime.now()
        )

        dilutionResult = dilutionRecord?.let { executor.execute(it) }
    }

    @Then("the target document contains the section {string}")
    fun documentContainsSection(title: String) {
        val content = docFile.readText(Charsets.UTF_8)
        assert(content.contains(title)) {
            "Document ${docFile.name} does not contain section '$title'"
        }
    }

    @Then("the target document contains dilution metadata")
    fun documentContainsMetadata() {
        val content = docFile.readText(Charsets.UTF_8)
        assert(content.contains("Metadonnees de dilution")) {
            "Dilution metadata missing in ${docFile.name}"
        }
        assert(content.contains("Classification") && (content.contains("VISION") || content.contains("ContentClassification.VISION"))) {
            "Classification missing or incorrect in ${docFile.name}"
        }
    }

    @Then("a safety backup was created")
    fun backupWasCreated() {
        assert(dilutionResult?.success == true) { "Dilution failed" }
        assert(dilutionResult?.backupPath != null) { "No backup created: ${dilutionResult?.error}" }
    }

    @Then("the target document contains the traceability table {string}")
    fun documentContainsTraceabilityTable(tableHeader: String) {
        val content = docFile.readText(Charsets.UTF_8)
        assert(content.contains(tableHeader)) {
            "Traceability table '$tableHeader' missing in ${docFile.name}"
        }
    }

    @Then("the target document keeps its initial content intact")
    fun documentPreservedContent() {
        val content = docFile.readText(Charsets.UTF_8)
        assert(content.contains("Important original content")) {
            "Original content was modified by DRY RUN"
        }
        assert(content.contains("Section dry run").not()) {
            "Test content should not be in the file during DRY RUN"
        }
    }

    @Given("a test workspace with {int} .adoc stimulus files and {int} already diluted documents")
    fun createWorkspaceWithStimuli(stimulusCount: Int, dilutedCount: Int) {
        tmpDir = Files.createTempDirectory("graphify-stimulus-ws").toFile()

        for (i in 1..stimulusCount) {
            val name = "STIMULUS_0${i}.adoc"
            File(tmpDir, name).writeText(
                "= Brain Dump $i\n\n== Test section\n\nStimulus content $i.",
                Charsets.UTF_8
            )
            log.info("Stimulus created: {}", name)
        }

        for (i in 1..dilutedCount) {
            val name = "WORKSPACE_VISION.adoc"
            if (i <= 1) {
                File(tmpDir, name).writeText(
                    """
= Workspace Vision

== Introduction

=== Stimuli diluted in this document

[cols="1,1,3"]
|===
| Stimulus | Date | Enriched sections
| STIMULUS_01.adoc | 12 May 2026 | Test
|===
""".trimStart(),
                    Charsets.UTF_8
                )
            }
        }
    }

    @When("the StimulusDetector scans the workspace")
    fun scanWorkspace() {
        detector = StimulusDetector(tmpDir!!.absolutePath)
        val files = detector!!.scan()
        log.info("Files scanned: ${files.size}")
        files.forEach { f -> log.info("  {}", f.name) }
        activeStimuli = detector!!.detectActive()
    }

    @Then("{int} active stimuli are detected")
    fun activeStimuliDetected(expectedCount: Int) {
        assert(activeStimuli.size == expectedCount) {
            "Expected $expectedCount active stimuli, got ${activeStimuli.size}: ${activeStimuli.map { it.file.name }}"
        }
    }

    @Then("no stimulus is stale - all modified recently")
    fun noStaleStimuli() {
        val stale = activeStimuli.filter { it.stale }
        assert(stale.isEmpty()) {
            "Unexpected stale stimuli: ${stale.map { it.file.name }}"
        }
    }

    @Given("a test workspace with {int} recent stimulus and {int} stimulus that is {int} days old")
    fun createWorkspaceWithAgedStimuli(recentCount: Int, oldCount: Int, days: Int) {
        tmpDir = Files.createTempDirectory("graphify-stimulus-aged").toFile()

        for (i in 1..recentCount) {
            val name = "STIMULUS_RECENT_${i}.adoc"
            val f = File(tmpDir, name)
            f.writeText("= Brain Dump Recent $i\n\nRecent content.", Charsets.UTF_8)
            f.setLastModified(System.currentTimeMillis())
        }

        for (i in 1..oldCount) {
            val name = "STIMULUS_OLD_${i}.adoc"
            val f = File(tmpDir, name)
            f.writeText("= Brain Dump Old $i\n\nOld content.", Charsets.UTF_8)
            val oldTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            f.setLastModified(oldTime)
        }
    }

    @When("the StimulusDetector detects stale stimuli")
    fun detectStaleStimuli() {
        detector = StimulusDetector(tmpDir!!.absolutePath)
        activeStimuli = detector!!.detectActive()
        staleStimuli = detector!!.detectStale()
        log.info("Active: {}, Stale: {}", activeStimuli.size, staleStimuli.size)
    }

    @Then("{int} stale stimulus is detected")
    fun staleStimulusDetected(expectedCount: Int) {
        assert(staleStimuli.size == expectedCount) {
            "Expected $expectedCount stale stimuli, got ${staleStimuli.size}"
        }
    }

    @Then("{int} active stimuli are detected in total")
    fun totalActiveStimuliDetected(expectedCount: Int) {
        assert(activeStimuli.size == expectedCount) {
            "Expected $expectedCount active stimuli total, got ${activeStimuli.size}"
        }
    }

    @After
    fun cleanupTempDir() {
        tmpDir?.let { dir ->
            dir.deleteRecursively()
            log.info("Temporary workspace cleaned: {}", dir.absolutePath)
        }
    }
}
