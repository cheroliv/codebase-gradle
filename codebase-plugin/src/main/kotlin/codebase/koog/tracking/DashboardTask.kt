package codebase.koog.tracking

import codebase.koog.session.SessionRepository
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory

/**
 * Tâche Gradle vibecodingDashboard — expose le tableau de bord des sessions vibecoding.
 *
 * Usage :
 * ```
 * ./gradlew vibecodingDashboard
 * ```
 *
 * Requiert un ConnectionFactory injecté (R2DBC PostgreSQL).
 * Produit un résumé console + format structuré via dashboard.summary().
 */
@DisableCachingByDefault(because = "Dashboard queries live data — non-cacheable")
abstract class DashboardTask : DefaultTask() {

    private val log = LoggerFactory.getLogger(DashboardTask::class.java)

    /** ConnectionFactory injectable pour le SessionRepository. */
    var connectionFactory: ConnectionFactory? = null

    init {
        group = "tracking"
        description = "Dashboard vibecoding — sessions, coûts, confidentialité"
    }

    @Console
    @TaskAction
    fun executeDashboard() {
        val cf = connectionFactory
            ?: throw IllegalStateException(
                "DashboardTask requires an injected ConnectionFactory. " +
                "Ensure R2DBC connection pool is available."
            )

        val repo = SessionRepository(cf)
        runBlocking {
            repo.initSchema()
            val dashboard = Dashboard(repo)
            val summary = dashboard.summary()

            log.info("╔══════════════════════════════════════════════╗")
            log.info("║   Vibecoding Dashboard                       ║")
            log.info("╠══════════════════════════════════════════════╣")
            log.info("║ Sessions totales  : ${summary.totalSessions}")
            log.info("║ Coût total        : \$%.4f".format(summary.totalCost))
            log.info("║ Tokens prompt     : ${summary.totalPromptTokens}")
            log.info("║ Tokens completion : ${summary.totalCompletionTokens}")
            log.info("║ Coût moyen/session: \$%.4f".format(summary.averageCostPerSession))
            log.info("║ Sessions 7 jours  : ${summary.sessionsLast7Days}")
            log.info("║ Sessions 30 jours : ${summary.sessionsLast30Days}")
            log.info("╠══════════════════════════════════════════════╣")
            log.info("║ Coûts par confidentialité :")
            for ((level, cost) in summary.confidentialityCosts.entries.sortedBy { it.key }) {
                val sessions = summary.confidentialitySessions[level] ?: 0
                log.info("║   %-12s : %3d sessions, \$%.4f".format(level, sessions, cost))
            }
            log.info("╠══════════════════════════════════════════════╣")
            summary.lastSession?.let {
                log.info("║ Dernière session  : ${it.id.take(8)}... — ${it.intention.take(40)}")
            }
            summary.mostExpensiveSession?.let {
                log.info("║ Plus chère session: ${it.id.take(8)}... — \$%.4f".format(it.estimatedCost ?: 0.0))
            }
            log.info("╚══════════════════════════════════════════════╝")
        }
    }
}
