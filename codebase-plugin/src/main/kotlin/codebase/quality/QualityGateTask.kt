package codebase.quality

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

abstract class QualityGateTask : DefaultTask() {

    @get:Input
    @get:Optional
    @get:Option(option = "output", description = "Expert text output to validate")
    abstract val output: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "domain", description = "Expert domain: CDA or FPA")
    abstract val domain: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "minAcceptableScore", description = "Minimum acceptable quality score (0.0–1.0)")
    abstract val minAcceptableScore: Property<Double>

    @get:Input
    @get:Optional
    @get:Option(option = "enableSentiment", description = "Enable sentiment analysis check")
    abstract val enableSentimentCheck: Property<Boolean>

    @get:Input
    @get:Optional
    @get:Option(option = "enableOffTopic", description = "Enable off-topic detection check")
    abstract val enableOffTopicCheck: Property<Boolean>

    @get:Input
    @get:Optional
    @get:Option(option = "enablePii", description = "Enable PII residual detection check")
    abstract val enablePiiCheck: Property<Boolean>

    @get:Internal
    var lastResult: QualityAssessment? = null
        private set

    init {
        group = "validate"
        description = "Quality gate task — validates expert outputs against sentiment, off-topic, and PII checks"
        output.convention("")
        domain.convention("")
        minAcceptableScore.convention(0.60)
        enableSentimentCheck.convention(true)
        enableOffTopicCheck.convention(true)
        enablePiiCheck.convention(true)
    }

    @TaskAction
    fun executeQualityGate() {
        val text = output.get()
        val dom = parseDomain(domain.get())
        val config = QualityGateConfig(
            minAcceptableScore = minAcceptableScore.get(),
            enableSentimentCheck = enableSentimentCheck.get(),
            enableOffTopicCheck = enableOffTopicCheck.get(),
            enablePiiCheck = enablePiiCheck.get()
        )

        val gate = QualityGate(config = config)
        val assessment = gate.evaluate(text, dom)
        lastResult = assessment

        if (!assessment.passed) {
            val failingNames = assessment.failingCheckerNames.joinToString(", ")
            throw RuntimeException("Quality gate failed: ${assessment.overallVerdict} — $failingNames")
        }
    }

    private fun parseDomain(domainStr: String): Domain {
        return try {
            Domain.valueOf(domainStr.uppercase())
        } catch (_: IllegalArgumentException) {
            logger.warn("Unknown domain '$domainStr', defaulting to CDA")
            Domain.CDA
        }
    }
}
