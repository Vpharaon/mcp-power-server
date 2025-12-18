package com.bazik.reminder

import com.bazik.reminder.models.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * TaskService (переименовано из ReminderService)
 * Бизнес-логика для управления задачами
 */
class TaskService(
    private val repository: TaskRepository,
    private val schedulerService: SchedulerService
) {

    /**
     * Добавить задачу
     * title может быть null - в этом случае он будет сгенерирован из description
     */
    fun addTask(
        title: String?,
        description: String,
        reminderTime: String, // ISO format: 2024-12-17T15:30:00
        recurrence: String? = null,
        importance: String = "MEDIUM"
    ): String {
        return try {
            // Validate importance
            val importanceEnum = try {
                TaskImportance.valueOf(importance.uppercase())
            } catch (e: IllegalArgumentException) {
                return "Error: Invalid importance. Must be one of: LOW, MEDIUM, HIGH, URGENT"
            }

            // Validate recurrence if provided
            val recurrenceEnum: TaskRecurrence? = if (recurrence != null && recurrence.isNotBlank()) {
                try {
                    TaskRecurrence.valueOf(recurrence.uppercase())
                } catch (e: IllegalArgumentException) {
                    return "Error: Invalid recurrence. Must be one of: DAILY, WEEKLY, MONTHLY"
                }
            } else {
                null
            }

            // Validate reminderTime format
            try {
                LocalDateTime.parse(reminderTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (e: DateTimeParseException) {
                return "Error: Invalid date format. Use ISO format: 2024-12-17T15:30:00"
            }

            val task = repository.addTask(
                title = title,
                description = description,
                reminderDateTime = reminderTime,
                recurrence = recurrenceEnum,
                importance = importanceEnum
            )

            formatTaskCreated(task)
        } catch (e: Exception) {
            "Error adding task: ${e.message}"
        }
    }

    /**
     * Получить список всех задач или отфильтрованных по статусу
     */
    fun listTasks(status: String? = null): String {
        return try {
            val tasks = when (status?.uppercase()) {
                "ACTIVE" -> repository.getTasksByCompleted(false)
                "COMPLETED" -> repository.getTasksByCompleted(true)
                null -> repository.getAllTasks()
                else -> return "Error: Invalid status. Must be one of: ACTIVE, COMPLETED"
            }

            if (tasks.isEmpty()) {
                "No tasks found."
            } else {
                formatTasksList(tasks)
            }
        } catch (e: Exception) {
            "Error listing tasks: ${e.message}"
        }
    }

    /**
     * Получить задачу по ID
     */
    fun getTaskById(id: Long): String {
        return try {
            val task = repository.getTaskById(id)
            if (task == null) {
                "Task with ID $id not found."
            } else {
                formatTaskDetail(task)
            }
        } catch (e: Exception) {
            "Error getting task: ${e.message}"
        }
    }

    /**
     * Получить задачи на конкретную дату
     */
    fun getTasksForDate(date: String): String {
        return try {
            val localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
            val tasks = repository.getTasksByDate(localDate)

            if (tasks.isEmpty()) {
                "No tasks found for date $date."
            } else {
                buildString {
                    appendLine("📅 Tasks for $date (${tasks.size} total)")
                    appendLine()
                    tasks.forEach { task ->
                        appendLine("#${task.id} - ${task.title}")
                        appendLine("  Time: ${formatTime(task.reminderDateTime)}")
                        appendLine("  Importance: ${task.importance}")
                        if (task.recurrence != null) {
                            appendLine("  Recurrence: ${task.recurrence}")
                        }
                        appendLine("  ${task.description}")
                        appendLine()
                    }
                }
            }
        } catch (e: DateTimeParseException) {
            "Error: Invalid date format. Use ISO format: 2024-12-17"
        } catch (e: Exception) {
            "Error getting tasks for date: ${e.message}"
        }
    }

    /**
     * Получить задачи по важности
     */
    fun getTasksByImportance(importance: String): String {
        return try {
            val importanceEnum = try {
                TaskImportance.valueOf(importance.uppercase())
            } catch (e: IllegalArgumentException) {
                return "Error: Invalid importance. Must be one of: LOW, MEDIUM, HIGH, URGENT"
            }

            val tasks = repository.getTasksByImportance(importanceEnum)

            if (tasks.isEmpty()) {
                "No tasks found with importance $importance."
            } else {
                buildString {
                    appendLine("📋 Tasks with importance $importance (${tasks.size} total)")
                    appendLine()
                    tasks.forEach { task ->
                        val statusTag = if (task.isCompleted) " ✅" else ""
                        appendLine("#${task.id} - ${task.title}$statusTag")
                        appendLine("  Reminder: ${formatDateTime(task.reminderDateTime)}")
                        if (task.recurrence != null) {
                            appendLine("  Recurrence: ${task.recurrence}")
                        }
                        appendLine("  ${task.description}")
                        appendLine()
                    }
                }
            }
        } catch (e: Exception) {
            "Error getting tasks by importance: ${e.message}"
        }
    }

    /**
     * Пометить задачу как завершенную
     */
    fun completeTask(id: Long): String {
        return try {
            val success = repository.updateTaskCompletion(id, true)
            if (success) {
                "✅ Task #$id marked as completed"
            } else {
                "Task with ID $id not found."
            }
        } catch (e: Exception) {
            "Error completing task: ${e.message}"
        }
    }

    /**
     * Удалить задачу
     */
    fun deleteTask(id: Long): String {
        return try {
            val success = repository.deleteTask(id)
            if (success) {
                "🗑️ Task #$id deleted successfully"
            } else {
                "Task with ID $id not found."
            }
        } catch (e: Exception) {
            "Error deleting task: ${e.message}"
        }
    }

    /**
     * Получить сводку по задачам
     */
    fun getSummary(): String {
        return try {
            val allTasks = repository.getAllTasks()
            val activeTasks = allTasks.filter { !it.isCompleted }
            val completedTasks = allTasks.filter { it.isCompleted }
            val overdueTasks = repository.getOverdueTasks()
            val upcomingTasks = repository.getUpcomingTasks(24)
            val highPriorityTasks = repository.getHighPriorityTasks()

            val summary = TaskSummary(
                totalTasks = allTasks.size,
                activeTasks = activeTasks.size,
                completedTasks = completedTasks.size,
                overdueTasks = overdueTasks.size,
                upcomingTasks = upcomingTasks,
                highPriorityTasks = highPriorityTasks,
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

    // ============= Formatting methods =============

    private fun formatTaskCreated(task: Task): String {
        return buildString {
            appendLine("✅ Task created successfully!")
            appendLine()
            appendLine("ID: ${task.id}")
            appendLine("Title: ${task.title}")
            appendLine("Description: ${task.description}")
            appendLine("Importance: ${task.importance}")
            appendLine("Reminder date/time: ${formatDateTime(task.reminderDateTime)}")
            if (task.recurrence != null) {
                appendLine("Recurrence: ${task.recurrence}")
            }
            appendLine("Status: ${if (task.isCompleted) "Completed" else "Active"}")
            appendLine("Created at: ${formatDateTime(task.createdAt)}")
        }
    }

    private fun formatTasksList(tasks: List<Task>): String {
        return buildString {
            appendLine("📋 Tasks (${tasks.size} total)")
            appendLine()

            tasks.forEach { task ->
                val overdueTag = if (isOverdue(task)) " ⚠️ OVERDUE" else ""
                val completedTag = if (task.isCompleted) " ✅" else ""
                appendLine("#${task.id} - ${task.title}$overdueTag$completedTag")
                appendLine("  Importance: ${task.importance} | Status: ${if (task.isCompleted) "Completed" else "Active"}")
                appendLine("  Reminder: ${formatDateTime(task.reminderDateTime)}")
                if (task.recurrence != null) {
                    appendLine("  Recurrence: ${task.recurrence}")
                }
                appendLine("  ${task.description}")
                appendLine()
            }
        }
    }

    private fun formatTaskDetail(task: Task): String {
        return buildString {
            appendLine("📌 Task Details")
            appendLine()
            appendLine("ID: ${task.id}")
            appendLine("Title: ${task.title}")
            appendLine("Description: ${task.description}")
            appendLine("Importance: ${task.importance}")
            appendLine("Status: ${if (task.isCompleted) "Completed" else "Active"}")
            appendLine("Reminder date/time: ${formatDateTime(task.reminderDateTime)}")
            if (task.recurrence != null) {
                appendLine("Recurrence: ${task.recurrence}")
            }
            if (isOverdue(task) && !task.isCompleted) {
                appendLine("⚠️ This task is OVERDUE")
            }
            appendLine("Created at: ${formatDateTime(task.createdAt)}")
            appendLine("Updated at: ${formatDateTime(task.updatedAt)}")
        }
    }

    private fun formatSummary(summary: TaskSummary): String {
        return buildString {
            appendLine("📊 TASKS SUMMARY")
            appendLine("Generated at: ${formatDateTime(summary.generatedAt)}")
            appendLine()
            appendLine("Statistics:")
            appendLine("  • Total tasks: ${summary.totalTasks}")
            appendLine("  • Active: ${summary.activeTasks}")
            appendLine("  • Completed: ${summary.completedTasks}")
            appendLine("  • Overdue: ${summary.overdueTasks}")
            appendLine()

            if (summary.highPriorityTasks.isNotEmpty()) {
                appendLine("🔴 HIGH PRIORITY TASKS:")
                summary.highPriorityTasks.forEach { task ->
                    appendLine("  #${task.id} - [${task.importance}] ${task.title}")
                    appendLine("    Reminder: ${formatDateTime(task.reminderDateTime)}")
                }
                appendLine()
            }

            if (summary.upcomingTasks.isNotEmpty()) {
                appendLine("📅 UPCOMING TASKS (Next 24 hours):")
                summary.upcomingTasks.forEach { task ->
                    appendLine("  #${task.id} - ${task.title}")
                    appendLine("    Reminder: ${formatDateTime(task.reminderDateTime)}")
                }
                appendLine()
            }

            if (summary.activeTasks == 0) {
                appendLine("✅ All done! No active tasks.")
            }
        }
    }

    private fun formatScheduleSet(schedule: NotificationSchedule): String {
        return buildString {
            appendLine("⏰ Notification schedule configured successfully!")
            appendLine()
            appendLine("Interval: Every ${schedule.intervalMinutes} minutes")
            appendLine("Status: ${if (schedule.isEnabled) "Enabled" else "Disabled"}")
            appendLine("Created at: ${formatDateTime(schedule.createdAt)}")
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
                appendLine("Last notification sent: ${formatDateTime(schedule.lastSentAt)}")
            } else {
                appendLine("No notifications sent yet")
            }
            appendLine("Created at: ${formatDateTime(schedule.createdAt)}")
        }
    }

    private fun formatDateTime(isoDate: String): String {
        return try {
            val dateTime = LocalDateTime.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (e: Exception) {
            isoDate
        }
    }

    private fun formatTime(isoDateTime: String): String {
        return try {
            val dateTime = LocalDateTime.parse(isoDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (e: Exception) {
            isoDateTime
        }
    }

    private fun isOverdue(task: Task): Boolean {
        if (task.isCompleted) {
            return false
        }

        return try {
            val reminderDateTime = LocalDateTime.parse(task.reminderDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            reminderDateTime.isBefore(LocalDateTime.now())
        } catch (e: Exception) {
            false
        }
    }
}
