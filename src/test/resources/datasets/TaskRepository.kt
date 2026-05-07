package codebase.repository

import java.sql.Connection
import java.sql.ResultSet

data class TaskEntity(
    val id: Long,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val completed: Boolean
)

enum class TaskPriority { LOW, MEDIUM, HIGH, CRITICAL }

class TaskRepository(private val connection: Connection) {

    fun findAll(): List<TaskEntity> {
        val results = mutableListOf<TaskEntity>()
        connection.prepareStatement("SELECT id, title, description, priority, completed FROM tasks ORDER BY priority DESC")
            .use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) results.add(mapRow(rs))
                }
            }
        return results
    }

    fun findById(id: Long): TaskEntity? {
        connection.prepareStatement("SELECT id, title, description, priority, completed FROM tasks WHERE id = ?")
            .use { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
    }

    fun findByPriority(priority: TaskPriority): List<TaskEntity> {
        val results = mutableListOf<TaskEntity>()
        connection.prepareStatement("SELECT id, title, description, priority, completed FROM tasks WHERE priority = ?")
            .use { stmt ->
                stmt.setString(1, priority.name)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) results.add(mapRow(rs))
                }
            }
        return results
    }

    fun insert(task: TaskEntity): TaskEntity {
        connection.prepareStatement(
            "INSERT INTO tasks (title, description, priority, completed) VALUES (?, ?, ?, ?)",
            java.sql.Statement.RETURN_GENERATED_KEYS
        ).use { stmt ->
            stmt.setString(1, task.title)
            stmt.setString(2, task.description)
            stmt.setString(3, task.priority.name)
            stmt.setBoolean(4, task.completed)
            stmt.executeUpdate()
            stmt.generatedKeys.use { keys ->
                if (keys.next()) return task.copy(id = keys.getLong(1))
                throw IllegalStateException("No generated key returned")
            }
        }
    }

    fun deleteById(id: Long): Boolean {
        connection.prepareStatement("DELETE FROM tasks WHERE id = ?")
            .use { stmt ->
                stmt.setLong(1, id)
                return stmt.executeUpdate() > 0
            }
    }

    private fun mapRow(rs: ResultSet) = TaskEntity(
        id = rs.getLong("id"),
        title = rs.getString("title"),
        description = rs.getString("description"),
        priority = TaskPriority.valueOf(rs.getString("priority")),
        completed = rs.getBoolean("completed")
    )
}
