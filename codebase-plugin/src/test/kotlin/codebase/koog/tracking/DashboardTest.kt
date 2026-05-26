package codebase.koog.tracking

import codebase.koog.session.SessionRecord
import codebase.koog.session.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSessionRepository : SessionRepository {
    private val sessions = mutableListOf<SessionRecord>()

    fun add(session: SessionRecord) {
        sessions.add(session)
    }

    override suspend fun allSessions(): List<SessionRecord> = sessions.toList()

    override suspend fun sessionsSince(since: Instant): List<SessionRecord> =
        sessions.filter { (it.endTime ?: it.startTime) >= since }

    override suspend fun countSessions(): Int = sessions.size

    override suspend fun countSessionsSince(since: Instant): Int = sessionsSince(since).size
}

/**
 * Factory function for test data — maps the old 9-arg shape to the canonical 17-arg constructor.
 * [model] is discarded (SessionRecord has no model field); use [estimatedCost] for cost tracking.
 */
private fun session(
    id: String,
    intention: String?,
    startTime: Instant,
    endTime: Instant?,
    promptTokens: Long,
    completionTokens: Long,
    model: String? = null,
    estimatedCost: Double? = null,
    confidentialityLevel: String? = "PUBLIC"
) = SessionRecord(
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

class DashboardTest {

    private val fakeRepo = FakeSessionRepository()
    private val dashboard = Dashboard(fakeRepo)

    @Test
    fun `totalCost sums estimated costs across all sessions`() = runBlocking {
        fakeRepo.add(session("s1", "task1", Instant.now(), Instant.now(), 1000, 500, "deepseek-v4-pro", 0.01, "public"))
        fakeRepo.add(session("s2", "task2", Instant.now(), Instant.now(), 2000, 1000, "deepseek-v4-pro", 0.02, "public"))
        val cost = dashboard.totalCost()
        assertTrue(cost > 0.0)
        assertEquals(0.03, cost, 0.0001)
    }

    @Test
    fun `totalSessions returns count of all sessions`() = runBlocking {
        fakeRepo.add(session("s1", "task1", Instant.now(), Instant.now(), 100, 50, "deepseek-v4-flash", 0.0, "public"))
        fakeRepo.add(session("s2", "task2", Instant.now(), Instant.now(), 200, 100, "deepseek-v4-flash", 0.0, "public"))
        fakeRepo.add(session("s3", "task3", Instant.now(), Instant.now(), 300, 150, "deepseek-v4-flash", 0.0, "public"))
        assertEquals(3, dashboard.totalSessions())
    }

    @Test
    fun `sessionsInLastDays filters by time range`() = runBlocking {
        val recent = session("recent", "recent", Instant.now().minus(2, ChronoUnit.DAYS), Instant.now(), 100, 50, "model", 0.0, "public")
        val old = session("old", "old", Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().minus(10, ChronoUnit.DAYS), 100, 50, "model", 0.0, "public")
        fakeRepo.add(recent)
        fakeRepo.add(old)
        assertEquals(2, dashboard.sessionsInLastDays(30))
        assertEquals(1, dashboard.sessionsInLastDays(5))
    }

    @Test
    fun `averageCostPerSession computes correctly`() = runBlocking {
        fakeRepo.add(session("s1", "t1", Instant.now(), Instant.now(), 1000, 500, "deepseek-v4-pro", 0.01, "public"))
        fakeRepo.add(session("s2", "t2", Instant.now(), Instant.now(), 2000, 1000, "deepseek-v4-pro", 0.03, "public"))
        val avg = dashboard.averageCostPerSession()
        assertEquals(0.02, avg, 0.0001)
    }

    @Test
    fun `topExpensiveSessions returns top N by cost`() = runBlocking {
        fakeRepo.add(session("s1", "low", Instant.now(), Instant.now(), 100, 50, "model", 0.001, "public"))
        fakeRepo.add(session("s2", "high", Instant.now(), Instant.now(), 10000, 5000, "model", 0.05, "public"))
        fakeRepo.add(session("s3", "mid", Instant.now(), Instant.now(), 2000, 1000, "model", 0.01, "public"))
        val top = dashboard.topExpensiveSessions(2)
        assertEquals(2, top.size)
        assertEquals("s2", top[0].id)
        assertEquals("s3", top[1].id)
    }

    @Test
    fun `summary includes all aggregated fields`() = runBlocking {
        fakeRepo.add(session("s1", "t1", Instant.now().minus(2, ChronoUnit.DAYS), Instant.now(), 1000, 500, "deepseek-v4-pro", 0.01, "public"))
        fakeRepo.add(session("s2", "t2", Instant.now().minus(40, ChronoUnit.DAYS), Instant.now().minus(40, ChronoUnit.DAYS), 2000, 1000, "deepseek-v4-pro", 0.02, "confidential"))
        val summary = dashboard.summary()
        assertEquals(2, summary.totalSessions)
        assertEquals(0.03, summary.totalCost, 0.0001)
        assertEquals(3000L, summary.totalPromptTokens)
        assertEquals(1500L, summary.totalCompletionTokens)
        assertEquals(0.015, summary.averageCostPerSession, 0.0001)
        assertEquals(1, summary.sessionsLast7Days)
        assertEquals(1, summary.sessionsLast30Days)
        assertNotNull(summary.mostExpensiveSession)
        assertEquals("s2", summary.mostExpensiveSession!!.id)
        assertNotNull(summary.lastSession)
        assertEquals("s1", summary.lastSession!!.id)
        assertEquals(2, summary.confidentialityCosts.size)
        assertEquals(2, summary.confidentialitySessions.size)
    }

    @Test
    fun `mostExpensiveSession is null for empty repository`() = runBlocking {
        val summary = dashboard.summary()
        assertNull(summary.mostExpensiveSession)
        assertNull(summary.lastSession)
        assertEquals(0, summary.totalSessions)
        assertEquals(0.0, summary.totalCost)
    }

    @Test
    fun `costByConfidentialityLevel groups costs correctly`() = runBlocking {
        fakeRepo.add(session("s1", "t1", Instant.now(), Instant.now(), 1000, 500, "model", 0.01, "public"))
        fakeRepo.add(session("s2", "t2", Instant.now(), Instant.now(), 2000, 1000, "model", 0.02, "confidential"))
        fakeRepo.add(session("s3", "t3", Instant.now(), Instant.now(), 3000, 1500, "model", 0.03, "public"))
        val result = dashboard.costByConfidentialityLevel()
        assertEquals(0.04, result["public"] ?: 0.0, 0.0001)
        assertEquals(0.02, result["confidential"] ?: 0.0, 0.0001)
    }

    @Test
    fun `sessionsByConfidentialityLevel groups counts correctly`() = runBlocking {
        fakeRepo.add(session("s1", "t1", Instant.now(), Instant.now(), 100, 50, "model", 0.0, "PUBLIC"))
        fakeRepo.add(session("s2", "t2", Instant.now(), Instant.now(), 100, 50, "model", 0.0, "INTERNAL"))
        fakeRepo.add(session("s3", "t3", Instant.now(), Instant.now(), 100, 50, "model", 0.0, "INTERNAL"))
        fakeRepo.add(session("s4", "t4", Instant.now(), Instant.now(), 100, 50, "model", 0.0, "CONFIDENTIAL"))
        val result = dashboard.sessionsByConfidentialityLevel()
        assertEquals(1, result["PUBLIC"] ?: 0)
        assertEquals(2, result["INTERNAL"] ?: 0)
        assertEquals(1, result["CONFIDENTIAL"] ?: 0)
    }
}
