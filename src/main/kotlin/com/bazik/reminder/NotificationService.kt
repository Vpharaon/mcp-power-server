package com.bazik.reminder

import com.bazik.reminder.models.TaskSummary
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class NotificationConfig(
    val telegramEnabled: Boolean = false,
    val telegramBotToken: String? = null,
    val telegramChatId: String? = null
)

class NotificationService(
    private val httpClient: HttpClient,
    private val config: NotificationConfig
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    suspend fun sendSummary(summary: TaskSummary): Result<String> {
        val message = formatSummaryMessage(summary)
        val subject = "Tasks Summary - ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
        return sendNotification(subject, message)
    }

    suspend fun sendNotification(subject: String, body: String): Result<String> {
        val results = mutableListOf<String>()

        // Send via Telegram
        if (config.telegramEnabled) {
            try {
                val telegramResult = sendTelegram(body)
                results.add("Telegram sent successfully")
                logger.info("Telegram notification sent successfully")
            } catch (e: Exception) {
                logger.error("Failed to send Telegram message: ${e.message}", e)
                results.add("Telegram failed: ${e.message}")
            }
        }

        return if (results.isEmpty()) {
            Result.failure(Exception("No notification methods are enabled"))
        } else {
            Result.success(results.joinToString("\n"))
        }
    }

    private suspend fun sendTelegram(message: String): String {
        require(config.telegramBotToken != null) { "Telegram bot token is not configured" }
        require(config.telegramChatId != null) { "Telegram chat ID is not configured" }

        val url = "https://api.telegram.org/bot${config.telegramBotToken}/sendMessage"

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "chat_id" to config.telegramChatId,
                "text" to message,
                "parse_mode" to "Markdown"
            ))
        }

        return if (response.status.isSuccess()) {
            "Telegram message sent successfully"
        } else {
            throw Exception("Telegram API error: ${response.status}")
        }
    }

    private fun formatSummaryMessage(summary: TaskSummary): String {
        val sb = StringBuilder()

        sb.appendLine("📋 **TASKS SUMMARY**")
        sb.appendLine("Generated at: ${summary.generatedAt}")
        sb.appendLine()
        sb.appendLine("**Statistics:**")
        sb.appendLine("• Total tasks: ${summary.totalTasks}")
        sb.appendLine("• Active: ${summary.activeTasks}")
        sb.appendLine("• Completed: ${summary.completedTasks}")
        sb.appendLine("• Overdue: ${summary.overdueTasks}")
        sb.appendLine()

        if (summary.highPriorityTasks.isNotEmpty()) {
            sb.appendLine("🔴 **HIGH PRIORITY TASKS:**")
            summary.highPriorityTasks.forEach { task ->
                sb.appendLine("  • [${task.importance}] ${task.title}")
                sb.appendLine("    Reminder: ${formatDate(task.reminderDateTime)}")
                sb.appendLine("    ${task.description}")
                sb.appendLine()
            }
        }

        if (summary.upcomingTasks.isNotEmpty()) {
            sb.appendLine("📅 **UPCOMING TASKS (Next 24 hours):**")
            summary.upcomingTasks.forEach { task ->
                sb.appendLine("  • ${task.title}")
                sb.appendLine("    Reminder: ${formatDate(task.reminderDateTime)}")
                sb.appendLine("    ${task.description}")
                sb.appendLine()
            }
        }

        if (summary.activeTasks == 0) {
            sb.appendLine("✅ All done! No active tasks.")
        }

        return sb.toString()
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val dateTime = LocalDateTime.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } catch (e: Exception) {
            isoDate
        }
    }
}