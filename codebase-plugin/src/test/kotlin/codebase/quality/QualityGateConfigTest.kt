package codebase.quality

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualityGateConfigTest {

    @Test
    fun `default config has sensible values`() {
        val config = QualityGateConfig()
        assertEquals(0.60, config.minAcceptableScore)
        assertEquals(3, config.maxRetries)
        assertTrue(config.enableSentimentCheck)
        assertTrue(config.enableOffTopicCheck)
        assertTrue(config.enablePiiCheck)
        assertEquals("Tu as été rappelé à l'ordre pour hors-sujet. Concentre-toi.", config.retryFeedbackPrefix)
    }

    @Test
    fun `custom config overrides all defaults`() {
        val config = QualityGateConfig(
            minAcceptableScore = 0.80,
            maxRetries = 2,
            enableSentimentCheck = false,
            enableOffTopicCheck = true,
            enablePiiCheck = true,
            retryFeedbackPrefix = "CORRECTION: "
        )
        assertEquals(0.80, config.minAcceptableScore)
        assertEquals(2, config.maxRetries)
        assertFalse(config.enableSentimentCheck)
        assertTrue(config.enableOffTopicCheck)
        assertTrue(config.enablePiiCheck)
        assertEquals("CORRECTION: ", config.retryFeedbackPrefix)
    }

    @Test
    fun `minAcceptableScore must be between 0 and 1`() {
        QualityGateConfig(minAcceptableScore = 0.0)
        QualityGateConfig(minAcceptableScore = 1.0)
        QualityGateConfig(minAcceptableScore = 0.75)
    }

    @Test
    fun `maxRetries must be positive`() {
        QualityGateConfig(maxRetries = 1)
        QualityGateConfig(maxRetries = 5)
    }
}
