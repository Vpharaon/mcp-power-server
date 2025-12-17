package com.bazik.reminder.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class Reminder(
    val id: Long = 0,
    val title: String,
    val description: String,
    val dueDate: String? = null, // ISO format: 2024-12-17T15:30:00
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
enum class ReminderPriority {
    LOW, MEDIUM, HIGH, URGENT
}

@Serializable
enum class ReminderStatus {
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
data class ReminderSummary(
    val totalReminders: Int,
    val activeReminders: Int,
    val completedReminders: Int,
    val overdueReminders: Int,
    val upcomingReminders: List<Reminder>,
    val highPriorityReminders: List<Reminder>,
    val generatedAt: String
)