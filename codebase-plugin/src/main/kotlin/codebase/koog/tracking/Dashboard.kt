package codebase.koog.tracking

import codebase.koog.session.SessionRecord
import codebase.koog.session.SessionRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class Dashboard(private val repository: SessionRepository) {
    suspend fun totalCost(): Double {
        return repository.allSessions().sumOf { it.estimatedCost ?: 0.0 }
    }

    suspend fun totalSessions(): Int {
        return repository.countSessions()
    }

    suspend fun sessionsInLastDays(days: Int): Int {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        return repository.countSessionsSince(since)
    }

    suspend fun averageCostPerSession(): Double {
        val sessions = repository.allSessions()
        if (sessions.isEmpty()) return 0.0
        return sessions.sumOf { it.estimatedCost ?: 0.0 } / sessions.size
    }

    suspend fun topExpensiveSessions(limit: Int = 5): List<SessionRecord> {
        return repository.allSessions()
            .sortedByDescending { it.estimatedCost ?: 0.0 }
            .take(limit)
    }

    suspend fun summary(): DashboardSummary {
        val sessions = repository.allSessions()
        val totalCost = sessions.sumOf { it.estimatedCost ?: 0.0 }
        val totalPrompt = sessions.sumOf { it.promptTokens }
        val totalCompletion = sessions.sumOf { it.completionTokens }
        val avgCost = if (sessions.isNotEmpty()) totalCost / sessions.size else 0.0
        val mostExpensive = sessions.maxByOrNull { it.estimatedCost ?: 0.0 }
        val lastSession = sessions.maxByOrNull { it.startTime }

        val sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS)
        val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)
        val sessionsLast7 = sessions.count { (it.endTime ?: it.startTime) >= sevenDaysAgo }
        val sessionsLast30 = sessions.count { (it.endTime ?: it.startTime) >= thirtyDaysAgo }

        val confidentialityCosts = costByConfidentialityLevel()
        val confidentialitySessions = sessionsByConfidentialityLevel()

        return DashboardSummary(
            totalSessions = sessions.size,
            totalCost = totalCost,
            totalPromptTokens = totalPrompt,
            totalCompletionTokens = totalCompletion,
            averageCostPerSession = avgCost,
            sessionsLast7Days = sessionsLast7,
            sessionsLast30Days = sessionsLast30,
            mostExpensiveSession = mostExpensive,
            lastSession = lastSession,
            confidentialityCosts = confidentialityCosts,
            confidentialitySessions = confidentialitySessions
        )
    }

    suspend fun listSessions(limit: Int = 50): List<SessionRecord> {
        return repository.allSessions().take(limit)
    }

    suspend fun costByConfidentialityLevel(): Map<String, Double> {
        return repository.allSessions()
            .groupBy { it.confidentialityLevel.ifEmpty { "unknown" } }
            .mapValues { (_, sessions) -> sessions.sumOf { s -> s.estimatedCost ?: 0.0 } }
    }

    suspend fun sessionsByConfidentialityLevel(): Map<String, Int> {
        return repository.allSessions()
            .groupBy { it.confidentialityLevel.ifEmpty { "unknown" } }
            .mapValues { (_, sessions) -> sessions.size }
    }
}

data class DashboardSummary(
    val totalSessions: Int,
    val totalCost: Double,
    val totalPromptTokens: Long,
    val totalCompletionTokens: Long,
    val averageCostPerSession: Double,
    val sessionsLast7Days: Int,
    val sessionsLast30Days: Int,
    val mostExpensiveSession: SessionRecord?,
    val lastSession: SessionRecord?,
    val confidentialityCosts: Map<String, Double>,
    val confidentialitySessions: Map<String, Int>
)
