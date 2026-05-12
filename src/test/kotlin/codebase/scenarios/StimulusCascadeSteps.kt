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

    @Given("un repertoire de travail temporaire pour les tests STIMULUS")
    fun createTempWorkspace() {
        val path = Files.createTempDirectory("graphify-stimulus-test")
        tmpDir = path.toFile()
        log.info("Workspace temporaire: {}", tmpDir!!.absolutePath)
    }

    @Given("le document cible {string} contient déjà la section {string}")
    fun createDocWithSection(filename: String, section: String) {
        docFile = File(tmpDir, filename)
        val content = """
= Document De Test
:jbake-status: draft

== Introduction

Premiere section.

$section

=== Stimuli dilues dans ce document

[cols="1,1,3"]
|===
| Stimulus | Date | Sections enrichies
| STIMULUS_EXISTANT.adoc | 10 May 2026 | Section existante
|===

""".trimStart()
        docFile.writeText(content, Charsets.UTF_8)
        log.info("Document cree: {} ({} caracteres)", docFile.absolutePath, docFile.length())
    }

    @Given("le document cible {string} ne contient pas de table de tracabilite")
    fun createDocWithoutTraceability(filename: String) {
        docFile = File(tmpDir, filename)
        val content = """
= Document Sans Traceability
:jbake-status: draft

== Introduction

Contenu initial.

== Nouveau Test

""".trimStart()
        docFile.writeText(content, Charsets.UTF_8)
    }

    @Given("le document cible {string} a un contenu initial")
    fun createDocWithInitialContent(filename: String) {
        docFile = File(tmpDir, filename)
        val content = """
= Document Initial
:jbake-status: draft

== Architecture

Contenu important original.

== Principes

Neuf principes fondateurs.

""".trimStart()
        docFile.writeText(content, Charsets.UTF_8)
        initialContent = content
    }

    @When("une section VISION est diluee vers {string} dans la section {string}")
    fun diluteVisionSection(filename: String, section: String) {
        val executor = DilutionExecutor(tmpDir!!.absolutePath)

        dilutionRecord = DilutionRecord(
            sectionId = "S1",
            sectionTitle = "Section de test",
            content = "Ceci est le contenu de la section de test.\n\nContenu enrichi par STIMULUS.",
            classification = ContentClassification.VISION,
            confidence = 0.95,
            dilutionTarget = DilutionTarget(
                targetDocument = TargetDocument.WORKSPACE_VISION,
                suggestedSection = section,
                rationale = "Test rationale routage"
            ),
            classificationRationale = "Test rationale classification",
            timestamp = LocalDateTime.now()
        )

        dilutionResult = dilutionRecord?.let { executor.execute(it) }
        log.info("Dilution result: success={} error={}", dilutionResult?.success, dilutionResult?.error)
    }

    @When("une section VISION est diluee en mode DRY RUN vers {string}")
    fun diluteVisionDryRun(filename: String) {
        val executor = DilutionExecutor(tmpDir!!.absolutePath, dryRun = true)

        dilutionRecord = DilutionRecord(
            sectionId = "S3",
            sectionTitle = "Section dry run",
            content = "Contenu qui ne doit pas etre injecte.",
            classification = ContentClassification.VISION,
            confidence = 0.90,
            dilutionTarget = DilutionTarget(
                targetDocument = TargetDocument.WORKSPACE_ORGANIZATION,
                suggestedSection = "== Principes",
                rationale = "Test dry run routage"
            ),
            classificationRationale = "Test dry run classification",
            timestamp = LocalDateTime.now()
        )

        dilutionResult = dilutionRecord?.let { executor.execute(it) }
    }

    @Then("le document cible contient la section {string}")
    fun documentContainsSection(title: String) {
        val content = docFile.readText(Charsets.UTF_8)
        assert(content.contains(title)) {
            "Le document ${docFile.name} ne contient pas la section '$title'"
        }
    }

    @Then("le document cible contient les metadonnees de dilution")
    fun documentContainsMetadata() {
        val content = docFile.readText(Charsets.UTF_8)
        assert(content.contains("Metadonnees de dilution")) {
            "Metadonnees de dilution absentes dans ${docFile.name}"
        }
        assert(content.contains("Classification") && content.contains("ContentClassification.VISION")) {
            "Classification absente ou incorrecte dans ${docFile.name}"
        }
    }

    @Then("une sauvegarde de securite a ete creee")
    fun backupWasCreated() {
        assert(dilutionResult?.success == true) { "Dilution a echoue" }
        assert(dilutionResult?.backupPath != null) { "Pas de backup cree: ${dilutionResult?.error}" }
    }

    @Then("le document cible contient la table de tracabilite {string}")
    fun documentContainsTraceabilityTable(tableHeader: String) {
        val content = docFile.readText(Charsets.UTF_8)
        assert(content.contains(tableHeader)) {
            "Table de tracabilite '$tableHeader' absente dans ${docFile.name}"
        }
    }

    @Then("le document cible a conserve son contenu initial intact")
    fun documentPreservedContent() {
        val content = docFile.readText(Charsets.UTF_8)
        assert(content.contains("Contenu important original")) {
            "Le contenu original a ete modifie par le DRY RUN"
        }
        assert(content.contains("Section dry run").not()) {
            "Le contenu de test ne devrait pas etre dans le fichier en DRY RUN"
        }
    }

    @Given("un workspace de test avec {int} fichiers .adoc stimuli et {int} documents deja dilues")
    fun createWorkspaceWithStimuli(stimulusCount: Int, dilutedCount: Int) {
        tmpDir = Files.createTempDirectory("graphify-stimulus-ws").toFile()

        for (i in 1..stimulusCount) {
            val name = "STIMULUS_0${i}.adoc"
            File(tmpDir, name).writeText(
                "= Brain Dump $i\n\n== Section de test\n\nContenu du stimulus $i.",
                Charsets.UTF_8
            )
            log.info("Stimulus cree: {}", name)
        }

        for (i in 1..dilutedCount) {
            val name = "WORKSPACE_VISION.adoc"
            if (i <= 1) {
                File(tmpDir, name).writeText(
                    """
= Workspace Vision

== Introduction

=== Stimuli dilues dans ce document

[cols="1,1,3"]
|===
| Stimulus | Date | Sections enrichies
| STIMULUS_01.adoc | 12 May 2026 | Test
|===
""".trimStart(),
                    Charsets.UTF_8
                )
            }
        }
    }

    @When("le StimulusDetector scanne le workspace")
    fun scanWorkspace() {
        detector = StimulusDetector(tmpDir!!.absolutePath)
        val files = detector!!.scan()
        log.info("Fichiers scannes: ${files.size}")
        files.forEach { f -> log.info("  {}", f.name) }
        activeStimuli = detector!!.detectActive()
    }

    @Then("{int} stimuli actifs sont detectes")
    fun activeStimuliDetected(expectedCount: Int) {
        assert(activeStimuli.size == expectedCount) {
            "Attendu $expectedCount stimuli actifs, got ${activeStimuli.size}: ${activeStimuli.map { it.file.name }}"
        }
    }

    @Then("aucun stimulus n'est stale (tous modifies recemment)")
    fun noStaleStimuli() {
        val stale = activeStimuli.filter { it.stale }
        assert(stale.isEmpty()) {
            "Stimuli stale inattendus: ${stale.map { it.file.name }}"
        }
    }

    @Given("un workspace de test avec {int} stimulus recent et {int} stimulus vieux de {int} jours")
    fun createWorkspaceWithAgedStimuli(recentCount: Int, oldCount: Int, days: Int) {
        tmpDir = Files.createTempDirectory("graphify-stimulus-aged").toFile()

        for (i in 1..recentCount) {
            val name = "STIMULUS_RECENT_${i}.adoc"
            val f = File(tmpDir, name)
            f.writeText("= Brain Dump Recent $i\n\nContenu recent.", Charsets.UTF_8)
            f.setLastModified(System.currentTimeMillis())
        }

        for (i in 1..oldCount) {
            val name = "STIMULUS_OLD_${i}.adoc"
            val f = File(tmpDir, name)
            f.writeText("= Brain Dump Vieux $i\n\nContenu vieux.", Charsets.UTF_8)
            val oldTime = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            f.setLastModified(oldTime)
        }
    }

    @When("le StimulusDetector detecte les stimuli stale")
    fun detectStaleStimuli() {
        detector = StimulusDetector(tmpDir!!.absolutePath)
        activeStimuli = detector!!.detectActive()
        staleStimuli = detector!!.detectStale()
        log.info("Actifs: {}, Stale: {}", activeStimuli.size, staleStimuli.size)
    }

    @Then("{int} stimulus stale est detecte")
    fun staleStimulusDetected(expectedCount: Int) {
        assert(staleStimuli.size == expectedCount) {
            "Attendu $expectedCount stimuli stale, got ${staleStimuli.size}"
        }
    }

    @Then("{int} stimuli actifs sont detectes au total")
    fun totalActiveStimuliDetected(expectedCount: Int) {
        assert(activeStimuli.size == expectedCount) {
            "Attendu $expectedCount stimuli actifs au total, got ${activeStimuli.size}"
        }
    }

    @After
    fun cleanupTempDir() {
        tmpDir?.let { dir ->
            dir.deleteRecursively()
            log.info("Workspace temporaire nettoye: {}", dir.absolutePath)
        }
    }
}
