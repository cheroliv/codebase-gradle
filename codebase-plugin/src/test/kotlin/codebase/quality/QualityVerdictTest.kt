package codebase.quality

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityVerdictTest {

    @Test
    fun `severity order is correct`() {
        assertEquals(0, QualityVerdict.PASS.severity)
        assertEquals(1, QualityVerdict.NEEDS_FIX.severity)
        assertEquals(2, QualityVerdict.FAIL.severity)
    }

    @Test
    fun `PASS is higher priority than FAIL`() {
        val worse = QualityVerdict.worse(QualityVerdict.PASS, QualityVerdict.FAIL)
        assertEquals(QualityVerdict.FAIL, worse)
    }

    @Test
    fun `NEEDS_FIX is worse than PASS`() {
        val worse = QualityVerdict.worse(QualityVerdict.PASS, QualityVerdict.NEEDS_FIX)
        assertEquals(QualityVerdict.NEEDS_FIX, worse)
    }

    @Test
    fun `FAIL is worse than NEEDS_FIX`() {
        val worse = QualityVerdict.worse(QualityVerdict.NEEDS_FIX, QualityVerdict.FAIL)
        assertEquals(QualityVerdict.FAIL, worse)
    }

    @Test
    fun `worse of same verdict is itself`() {
        assertEquals(QualityVerdict.PASS, QualityVerdict.worse(QualityVerdict.PASS, QualityVerdict.PASS))
        assertEquals(QualityVerdict.FAIL, QualityVerdict.worse(QualityVerdict.FAIL, QualityVerdict.FAIL))
    }

    @Test
    fun `worst of PASS list is PASS`() {
        val verdicts = listOf(QualityVerdict.PASS, QualityVerdict.PASS, QualityVerdict.PASS)
        assertEquals(QualityVerdict.PASS, QualityVerdict.worst(verdicts))
    }

    @Test
    fun `worst of mixed list is FAIL`() {
        val verdicts = listOf(QualityVerdict.PASS, QualityVerdict.NEEDS_FIX, QualityVerdict.FAIL)
        assertEquals(QualityVerdict.FAIL, QualityVerdict.worst(verdicts))
    }

    @Test
    fun `worst of PASS and NEEDS_FIX is NEEDS_FIX`() {
        val verdicts = listOf(QualityVerdict.PASS, QualityVerdict.NEEDS_FIX, QualityVerdict.PASS)
        assertEquals(QualityVerdict.NEEDS_FIX, QualityVerdict.worst(verdicts))
    }

    @Test
    fun `worst of empty list is PASS`() {
        assertEquals(QualityVerdict.PASS, QualityVerdict.worst(emptyList()))
    }
}
