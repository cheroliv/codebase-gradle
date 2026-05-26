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
    constructor(
        id: String,
        intention: String?,
        startTime: Instant,
        endTime: Instant?,
        promptTokens: Long,
        completionTokens: Long,
        model: String?,
        estimatedCost: Double?,
        confidentialityLevel: String?
    ) : this(
        id = id,
        parentSessionId = null,
        workspaceRoot = "",
        intention = intention ?: "",
        dryRun = false,
        maxActions = 10,
        classification = "",
        planJson = null,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        cost = estimatedCost ?: 0.0,
        error = null,
        finished = endTime != null,
        iterationCount = 0,
        confidentialityLevel = confidentialityLevel ?: "INTERNAL",
        createdAt = startTime,
        updatedAt = endTime ?: startTime
    )

    val startTime: Instant get() = createdAt
    val endTime: Instant? get() = if (finished) updatedAt else null
    val model: String? get() = null
    val estimatedCost: Double? get() = cost
}
