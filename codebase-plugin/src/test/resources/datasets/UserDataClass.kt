package codebase.model

data class UserDataClass(
    val id: Long,
    val username: String,
    val email: String,
    val role: UserRole,
    val createdAt: String,
    val isActive: Boolean = true
)

enum class UserRole {
    ADMIN,
    EDITOR,
    VIEWER
}

fun UserDataClass.displayName(): String = "$username <$email>"

fun UserDataClass.hasRole(role: UserRole): Boolean = this.role == role

fun List<UserDataClass>.activeUsers(): List<UserDataClass> =
    filter { it.isActive }
