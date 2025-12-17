package com.bazik.reminder

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class SchedulerService(
    private val reminderRepository: ReminderRepository,
    private val notificationService: NotificationService
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
            logger.info("Scheduler started")

            while (isActive) {
                try {
                    checkAndSendNotifications()
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

    private suspend fun checkAndSendNotifications() {
        val schedule = reminderRepository.getNotificationSchedule()

        if (schedule == null || !schedule.isEnabled) {
            return
        }

        val now = LocalDateTime.now()
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
            reminderRepository.updateLastSentAt(schedule.id)
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

    private fun generateSummary(): com.bazik.reminder.models.ReminderSummary {
        val allReminders = reminderRepository.getAllReminders()
        val activeReminders = allReminders.filter { it.status == com.bazik.reminder.models.ReminderStatus.ACTIVE }
        val completedReminders = allReminders.filter { it.status == com.bazik.reminder.models.ReminderStatus.COMPLETED }
        val overdueReminders = reminderRepository.getOverdueReminders()
        val upcomingReminders = reminderRepository.getUpcomingReminders(24)
        val highPriorityReminders = reminderRepository.getHighPriorityReminders()

        return com.bazik.reminder.models.ReminderSummary(
            totalReminders = allReminders.size,
            activeReminders = activeReminders.size,
            completedReminders = completedReminders.size,
            overdueReminders = overdueReminders.size,
            upcomingReminders = upcomingReminders,
            highPriorityReminders = highPriorityReminders,
            generatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

    fun isRunning(): Boolean = schedulerJob?.isActive == true
}