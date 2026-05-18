package codebase.langgraph

data class Plan(
    val title: String,
    val epics: List<Epic>,
    val totalPoints: Int,
    val estimatedSessions: String
)

data class Epic(
    val name: String,
    val description: String,
    val points: Int,
    val userStories: List<UserStory>
)

data class UserStory(
    val description: String,
    val tasks: List<Task>
)

data class Task(
    val description: String,
    val gradleTask: String
)
