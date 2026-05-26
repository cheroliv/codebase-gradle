package codebase.koog.session

import java.time.Instant

data class SessionRecord(
    val id: String,
    val parentSessionId: String?,
    val workspaceRoot: String,
    val intention: String,
    val dryRun: Boolean,
    val maxActions: Int,
    val classification: String,
    val planJson: String?,
    val promptTokens: Long,
    val completionTokens: Long,
    val cost: Double,
    val error: String?,
    val finished: Boolean,
    val iterationCount: Int,
    val confidentialityLevel: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    val startTime: Instant get() = createdAt
    val endTime: Instant? get() = if (finished) updatedAt else null
    val estimatedCost: Double? get() = cost
}
