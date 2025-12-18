package com.bazik.reminder.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * Основная модель задачи (Task)
 * Переименовано из Reminder для соответствия спецификации
 */
@Serializable
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String,
    val reminderDateTime: String, // ISO format: 2024-12-17T15:30:00 - время сервера
    val recurrence: TaskRecurrence? = null, // Повторение: DAILY, WEEKLY, MONTHLY
    val importance: TaskImportance = TaskImportance.MEDIUM,
    val isCompleted: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class TaskImportance {
    LOW, MEDIUM, HIGH, URGENT
}

@Serializable
enum class TaskRecurrence {
    DAILY, WEEKLY, MONTHLY
}

@Serializable
enum class TaskStatus {
    ACTIVE, COMPLETED, ARCHIVED
}

@Serializable
data class NotificationSchedule(
    val id: Long = 0,
    val intervalMinutes: Int, // Интервал в минутах для отправки summary
    val isEnabled: Boolean = true,
    val lastSentAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class TaskSummary(
    val totalTasks: Int,
    val activeTasks: Int,
    val completedTasks: Int,
    val overdueTasks: Int,
    val upcomingTasks: List<Task>,
    val highPriorityTasks: List<Task>,
    val generatedAt: String
)