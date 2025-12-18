package com.bazik.reminder

import com.bazik.agent.AgentIntegrationService
import com.bazik.reminder.models.TaskSummary
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * SchedulerService - переписан согласно спецификации
 * Каждую минуту проверяет задачи и отправляет уведомления
 */
class SchedulerService(
    private val taskRepository: TaskRepository,
    private val notificationService: NotificationService,
    private val defaultZoneId: ZoneId = ZoneId.of("Europe/Moscow"),
    private val agentIntegrationService: AgentIntegrationService? = null
) {
    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
    private var schedulerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        if (schedulerJob?.isActive == true) {
            logger.warn("Scheduler is already running")
            return
        }

        schedulerJob = scope.launch {
            logger.info("Scheduler started - checking tasks every minute")

            while (isActive) {
                try {
                    // Проверка и отправка периодических уведомлений (summary)
                    checkAndSendNotifications()

                    // Новая логика: проверка и обработка задач
                    checkAndExecuteTaskNotifications()
                } catch (e: Exception) {
                    logger.error("Error in scheduler loop: ${e.message}", e)
                }

                // Check every minute
                delay(60_000)
            }
        }
    }

    fun stop() {
        schedulerJob?.cancel()
        logger.info("Scheduler stopped")
    }

    /**
     * Проверка периодических уведомлений (summary)
     */
    private suspend fun checkAndSendNotifications() {
        val schedule = taskRepository.getNotificationSchedule()

        if (schedule == null || !schedule.isEnabled) {
            return
        }

        val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime()
        val shouldSend = if (schedule.lastSentAt == null) {
            true
        } else {
            val lastSent = LocalDateTime.parse(schedule.lastSentAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val minutesSinceLastSent = ChronoUnit.MINUTES.between(lastSent, now)
            minutesSinceLastSent >= schedule.intervalMinutes
        }

        if (shouldSend) {
            logger.info("Time to send summary notification")
            sendSummaryNotification()
            taskRepository.updateLastSentAt(schedule.id)
        }
    }

    /**
     * НОВАЯ ЛОГИКА согласно спецификации:
     * Проверка задач, требующих выполнения, и отправка уведомлений
     */
    private suspend fun checkAndExecuteTaskNotifications() {
        if (agentIntegrationService == null) {
            logger.debug("AgentIntegrationService not available, skipping task notifications")
            return
        }

        try {
            val now = ZonedDateTime.now(defaultZoneId).toLocalDateTime()

            // Найти все задачи, у которых reminderDateTime <= now и isCompleted = false
            val tasksToProcess = taskRepository.getTasksToProcess(now)

            if (tasksToProcess.isEmpty()) {
                return
            }

            logger.info("Found ${tasksToProcess.size} task(s) ready for notification")

            for (task in tasksToProcess) {
                try {
                    logger.info("Processing task notification for #${task.id}: ${task.title}")

                    // Обработать задачу через агента (формирование и отправка уведомления)
                    val result = agentIntegrationService.executeTask(task)//processTaskNotification(task)

                    result.onSuccess { summary ->
                        logger.info("Task notification #${task.id} sent successfully")
                        logger.debug("Notification content:\n$summary")

                        // Пометить задачу как обработанную
                        // Если есть recurrence - обновится reminderDateTime
                        // Если нет recurrence - пометится как завершенная
                        taskRepository.markTaskAsProcessed(task)

                        if (task.recurrence != null) {
                            logger.info("Task #${task.id} rescheduled for next occurrence (recurrence: ${task.recurrence})")
                        } else {
                            logger.info("Task #${task.id} marked as completed")
                        }
                    }.onFailure { error ->
                        logger.error("Failed to send notification for task #${task.id}: ${error.message}", error)

                        // В случае ошибки можно отправить уведомление об ошибке
                        val errorMessage = buildString {
                            appendLine("❌ Failed to send notification for task:")
                            appendLine("Task: ${task.title}")
                            appendLine("Error: ${error.message}")
                        }

                        notificationService.sendNotification(
                            subject = "Task Notification Failed: ${task.title}",
                            body = errorMessage
                        ).onFailure {
                            logger.debug("Could not send error notification: ${it.message}")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing task notification #${task.id}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Error in checkAndExecuteTaskNotifications: ${e.message}", e)
        }
    }

    suspend fun sendSummaryNotification(): Result<String> {
        return try {
            val summary = generateSummary()

            val result = notificationService.sendSummary(summary)

            result.onSuccess {
                logger.info("Summary notification sent successfully")
            }.onFailure { e ->
                logger.error("Failed to send summary notification: ${e.message}", e)
            }

            result
        } catch (e: Exception) {
            logger.error("Error generating or sending summary: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun generateSummary(): TaskSummary {
        val allTasks = taskRepository.getAllTasks()
        val activeTasks = allTasks.filter { !it.isCompleted }
        val completedTasks = allTasks.filter { it.isCompleted }
        val overdueTasks = taskRepository.getOverdueTasks()
        val upcomingTasks = taskRepository.getUpcomingTasks(24)
        val highPriorityTasks = taskRepository.getHighPriorityTasks()

        return TaskSummary(
            totalTasks = allTasks.size,
            activeTasks = activeTasks.size,
            completedTasks = completedTasks.size,
            overdueTasks = overdueTasks.size,
            upcomingTasks = upcomingTasks,
            highPriorityTasks = highPriorityTasks,
            generatedAt = ZonedDateTime.now(defaultZoneId).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

    fun isRunning(): Boolean = schedulerJob?.isActive == true
}
