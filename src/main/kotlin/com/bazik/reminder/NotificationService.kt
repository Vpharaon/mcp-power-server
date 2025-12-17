package com.bazik.reminder

import com.bazik.reminder.models.ReminderSummary
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.apache.commons.mail.SimpleEmail
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class NotificationConfig(
    val emailEnabled: Boolean = false,
    val emailSmtpHost: String? = null,
    val emailSmtpPort: Int? = null,
    val emailUsername: String? = null,
    val emailPassword: String? = null,
    val emailFrom: String? = null,
    val emailTo: String? = null,
    val telegramEnabled: Boolean = false,
    val telegramBotToken: String? = null,
    val telegramChatId: String? = null
)

class NotificationService(
    private val httpClient: HttpClient,
    private val config: NotificationConfig
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    suspend fun sendSummary(summary: ReminderSummary): Result<String> {
        val message = formatSummaryMessage(summary)
        val results = mutableListOf<String>()

        // Send via Email
        if (config.emailEnabled) {
            try {
                sendEmail(message)
                results.add("Email sent successfully")
                logger.info("Summary email sent successfully")
            } catch (e: Exception) {
                logger.error("Failed to send email: ${e.message}", e)
                results.add("Email failed: ${e.message}")
            }
        }

        // Send via Telegram
        if (config.telegramEnabled) {
            try {
                val telegramResult = sendTelegram(message)
                results.add("Telegram sent successfully")
                logger.info("Summary sent to Telegram successfully")
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

    private fun sendEmail(message: String) {
        require(config.emailSmtpHost != null) { "Email SMTP host is not configured" }
        require(config.emailSmtpPort != null) { "Email SMTP port is not configured" }
        require(config.emailUsername != null) { "Email username is not configured" }
        require(config.emailPassword != null) { "Email password is not configured" }
        require(config.emailFrom != null) { "Email from address is not configured" }
        require(config.emailTo != null) { "Email to address is not configured" }

        val email = SimpleEmail()
        email.hostName = config.emailSmtpHost
        email.setSmtpPort(config.emailSmtpPort)
        email.setAuthentication(config.emailUsername, config.emailPassword)

        // Use STARTTLS instead of SSL for better compatibility with Gmail
        if (config.emailSmtpPort == 587) {
            email.isStartTLSEnabled = true
            email.isStartTLSRequired = true
            email.isSSLOnConnect = false
        } else {
            // For port 465, use SSL
            email.isSSLOnConnect = true
            email.sslSmtpPort = config.emailSmtpPort.toString()
        }

        // Add SSL check bypass for self-signed certificates (if needed)
        email.setSSLCheckServerIdentity(true)

        email.setFrom(config.emailFrom)
        email.subject = "Reminders Summary - ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
        email.setMsg(message)
        email.addTo(config.emailTo)
        email.send()
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

    private fun formatSummaryMessage(summary: ReminderSummary): String {
        val sb = StringBuilder()

        sb.appendLine("📋 **REMINDERS SUMMARY**")
        sb.appendLine("Generated at: ${summary.generatedAt}")
        sb.appendLine()
        sb.appendLine("**Statistics:**")
        sb.appendLine("• Total reminders: ${summary.totalReminders}")
        sb.appendLine("• Active: ${summary.activeReminders}")
        sb.appendLine("• Completed: ${summary.completedReminders}")
        sb.appendLine("• Overdue: ${summary.overdueReminders}")
        sb.appendLine()

        if (summary.highPriorityReminders.isNotEmpty()) {
            sb.appendLine("🔴 **HIGH PRIORITY REMINDERS:**")
            summary.highPriorityReminders.forEach { reminder ->
                sb.appendLine("  • [${reminder.priority}] ${reminder.title}")
                if (reminder.dueDate != null) {
                    sb.appendLine("    Due: ${formatDate(reminder.dueDate)}")
                }
                sb.appendLine("    ${reminder.description}")
                sb.appendLine()
            }
        }

        if (summary.upcomingReminders.isNotEmpty()) {
            sb.appendLine("📅 **UPCOMING REMINDERS (Next 24 hours):**")
            summary.upcomingReminders.forEach { reminder ->
                sb.appendLine("  • ${reminder.title}")
                if (reminder.dueDate != null) {
                    sb.appendLine("    Due: ${formatDate(reminder.dueDate)}")
                }
                sb.appendLine("    ${reminder.description}")
                sb.appendLine()
            }
        }

        if (summary.activeReminders == 0) {
            sb.appendLine("✅ All done! No active reminders.")
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