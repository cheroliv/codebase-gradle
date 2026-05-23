package codebase.quality

enum class QualityVerdict(val severity: Int) {
    PASS(0),
    NEEDS_FIX(1),
    FAIL(2);

    companion object {
        fun worse(a: QualityVerdict, b: QualityVerdict): QualityVerdict =
            if (a.severity >= b.severity) a else b

        fun worst(verdicts: List<QualityVerdict>): QualityVerdict =
            verdicts.maxByOrNull { it.severity } ?: PASS
    }
}
