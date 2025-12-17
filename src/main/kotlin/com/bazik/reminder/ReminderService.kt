package com.bazik.reminder

import com.bazik.reminder.models.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ReminderService(
    private val repository: ReminderRepository,
    private val schedulerService: SchedulerService
) {

    fun addReminder(
        title: String,
        description: String,
        dueDate: String? = null,
        priority: String = "MEDIUM"
    ): String {
        return try {
            // Validate priority
            val priorityEnum = try {
                ReminderPriority.valueOf(priority.uppercase())
            } catch (e: IllegalArgumentException) {
                return "Error: Invalid priority. Must be one of: LOW, MEDIUM, HIGH, URGENT"
            }

            // Validate due date if provided
            if (dueDate != null && dueDate.isNotBlank()) {
                try {
                    LocalDateTime.parse(dueDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                } catch (e: DateTimeParseException) {
                    return "Error: Invalid date format. Use ISO format: 2024-12-17T15:30:00"
                }
            }

            val reminder = repository.addReminder(
                title = title,
                description = description,
                dueDate = if (dueDate.isNullOrBlank()) null else dueDate,
                priority = priorityEnum
            )

            formatReminderCreated(reminder)
        } catch (e: Exception) {
            "Error adding reminder: ${e.message}"
        }
    }

    fun listReminders(status: String? = null): String {
        return try {
            val reminders = if (status != null) {
                val statusEnum = try {
                    ReminderStatus.valueOf(status.uppercase())
                } catch (e: IllegalArgumentException) {
                    return "Error: Invalid status. Must be one of: ACTIVE, COMPLETED, ARCHIVED"
                }
                repository.getRemindersByStatus(statusEnum)
            } else {
                repository.getAllReminders()
            }

            if (reminders.isEmpty()) {
                "No reminders found."
            } else {
                formatRemindersList(reminders)
            }
        } catch (e: Exception) {
            "Error listing reminders: ${e.message}"
        }
    }

    fun getReminderById(id: Long): String {
        return try {
            val reminder = repository.getReminderById(id)
            if (reminder == null) {
                "Reminder with ID $id not found."
            } else {
                formatReminderDetail(reminder)
            }
        } catch (e: Exception) {
            "Error getting reminder: ${e.message}"
        }
    }

    fun completeReminder(id: Long): String {
        return try {
            val success = repository.updateReminderStatus(id, ReminderStatus.COMPLETED)
            if (success) {
                "✅ Reminder #$id marked as completed"
            } else {
                "Reminder with ID $id not found."
            }
        } catch (e: Exception) {
            "Error completing reminder: ${e.message}"
        }
    }

    fun deleteReminder(id: Long): String {
        return try {
            val success = repository.deleteReminder(id)
            if (success) {
                "🗑️ Reminder #$id deleted successfully"
            } else {
                "Reminder with ID $id not found."
            }
        } catch (e: Exception) {
            "Error deleting reminder: ${e.message}"
        }
    }

    fun getSummary(): String {
        return try {
            val allReminders = repository.getAllReminders()
            val activeReminders = allReminders.filter { it.status == ReminderStatus.ACTIVE }
            val completedReminders = allReminders.filter { it.status == ReminderStatus.COMPLETED }
            val overdueReminders = repository.getOverdueReminders()
            val upcomingReminders = repository.getUpcomingReminders(24)
            val highPriorityReminders = repository.getHighPriorityReminders()

            val summary = ReminderSummary(
                totalReminders = allReminders.size,
                activeReminders = activeReminders.size,
                completedReminders = completedReminders.size,
                overdueReminders = overdueReminders.size,
                upcomingReminders = upcomingReminders,
                highPriorityReminders = highPriorityReminders,
                generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )

            formatSummary(summary)
        } catch (e: Exception) {
            "Error generating summary: ${e.message}"
        }
    }

    fun setNotificationSchedule(intervalMinutes: Int, enabled: Boolean = true): String {
        return try {
            if (intervalMinutes < 1) {
                return "Error: Interval must be at least 1 minute"
            }

            val schedule = repository.setNotificationSchedule(intervalMinutes, enabled)

            if (enabled && !schedulerService.isRunning()) {
                schedulerService.start()
            }

            formatScheduleSet(schedule)
        } catch (e: Exception) {
            "Error setting notification schedule: ${e.message}"
        }
    }

    fun getNotificationSchedule(): String {
        return try {
            val schedule = repository.getNotificationSchedule()
            if (schedule == null) {
                "No notification schedule configured. Use set_notification_schedule to create one."
            } else {
                formatScheduleInfo(schedule)
            }
        } catch (e: Exception) {
            "Error getting notification schedule: ${e.message}"
        }
    }

    suspend fun sendTestNotification(): String {
        return try {
            val result = schedulerService.sendSummaryNotification()
            result.fold(
                onSuccess = { "Test notification sent successfully:\n$it" },
                onFailure = { "Failed to send test notification: ${it.message}" }
            )
        } catch (e: Exception) {
            "Error sending test notification: ${e.message}"
        }
    }

    private fun formatReminderCreated(reminder: Reminder): String {
        return buildString {
            appendLine("✅ Reminder created successfully!")
            appendLine()
            appendLine("ID: ${reminder.id}")
            appendLine("Title: ${reminder.title}")
            appendLine("Description: ${reminder.description}")
            appendLine("Priority: ${reminder.priority}")
            if (reminder.dueDate != null) {
                appendLine("Due date: ${formatDate(reminder.dueDate)}")
            }
            appendLine("Status: ${reminder.status}")
            appendLine("Created at: ${formatDate(reminder.createdAt)}")
        }
    }

    private fun formatRemindersList(reminders: List<Reminder>): String {
        return buildString {
            appendLine("📋 Reminders (${reminders.size} total)")
            appendLine()

            reminders.forEach { reminder ->
                val overdueTag = if (isOverdue(reminder)) " ⚠️ OVERDUE" else ""
                appendLine("#${reminder.id} - ${reminder.title}$overdueTag")
                appendLine("  Priority: ${reminder.priority} | Status: ${reminder.status}")
                if (reminder.dueDate != null) {
                    appendLine("  Due: ${formatDate(reminder.dueDate)}")
                }
                appendLine("  ${reminder.description}")
                appendLine()
            }
        }
    }

    private fun formatReminderDetail(reminder: Reminder): String {
        return buildString {
            appendLine("📌 Reminder Details")
            appendLine()
            appendLine("ID: ${reminder.id}")
            appendLine("Title: ${reminder.title}")
            appendLine("Description: ${reminder.description}")
            appendLine("Priority: ${reminder.priority}")
            appendLine("Status: ${reminder.status}")
            if (reminder.dueDate != null) {
                appendLine("Due date: ${formatDate(reminder.dueDate)}")
                if (isOverdue(reminder)) {
                    appendLine("⚠️ This reminder is OVERDUE")
                }
            }
            appendLine("Created at: ${formatDate(reminder.createdAt)}")
            appendLine("Updated at: ${formatDate(reminder.updatedAt)}")
        }
    }

    private fun formatSummary(summary: ReminderSummary): String {
        return buildString {
            appendLine("📊 REMINDERS SUMMARY")
            appendLine("Generated at: ${formatDate(summary.generatedAt)}")
            appendLine()
            appendLine("Statistics:")
            appendLine("  • Total reminders: ${summary.totalReminders}")
            appendLine("  • Active: ${summary.activeReminders}")
            appendLine("  • Completed: ${summary.completedReminders}")
            appendLine("  • Overdue: ${summary.overdueReminders}")
            appendLine()

            if (summary.highPriorityReminders.isNotEmpty()) {
                appendLine("🔴 HIGH PRIORITY REMINDERS:")
                summary.highPriorityReminders.forEach { reminder ->
                    appendLine("  #${reminder.id} - [${reminder.priority}] ${reminder.title}")
                    if (reminder.dueDate != null) {
                        appendLine("    Due: ${formatDate(reminder.dueDate)}")
                    }
                }
                appendLine()
            }

            if (summary.upcomingReminders.isNotEmpty()) {
                appendLine("📅 UPCOMING REMINDERS (Next 24 hours):")
                summary.upcomingReminders.forEach { reminder ->
                    appendLine("  #${reminder.id} - ${reminder.title}")
                    if (reminder.dueDate != null) {
                        appendLine("    Due: ${formatDate(reminder.dueDate)}")
                    }
                }
                appendLine()
            }

            if (summary.activeReminders == 0) {
                appendLine("✅ All done! No active reminders.")
            }
        }
    }

    private fun formatScheduleSet(schedule: NotificationSchedule): String {
        return buildString {
            appendLine("⏰ Notification schedule configured successfully!")
            appendLine()
            appendLine("Interval: Every ${schedule.intervalMinutes} minutes")
            appendLine("Status: ${if (schedule.isEnabled) "Enabled" else "Disabled"}")
            appendLine("Created at: ${formatDate(schedule.createdAt)}")
        }
    }

    private fun formatScheduleInfo(schedule: NotificationSchedule): String {
        return buildString {
            appendLine("⏰ Notification Schedule")
            appendLine()
            appendLine("ID: ${schedule.id}")
            appendLine("Interval: Every ${schedule.intervalMinutes} minutes")
            appendLine("Status: ${if (schedule.isEnabled) "✅ Enabled" else "❌ Disabled"}")
            if (schedule.lastSentAt != null) {
                appendLine("Last notification sent: ${formatDate(schedule.lastSentAt)}")
            } else {
                appendLine("No notifications sent yet")
            }
            appendLine("Created at: ${formatDate(schedule.createdAt)}")
        }
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val dateTime = LocalDateTime.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (e: Exception) {
            isoDate
        }
    }

    private fun isOverdue(reminder: Reminder): Boolean {
        if (reminder.dueDate == null || reminder.status != ReminderStatus.ACTIVE) {
            return false
        }

        return try {
            val dueDate = LocalDateTime.parse(reminder.dueDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            dueDate.isBefore(LocalDateTime.now())
        } catch (e: Exception) {
            false
        }
    }
}