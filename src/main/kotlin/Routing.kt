package com.bazik

import com.bazik.mcp.McpService
import com.bazik.mcp.models.JsonRpcRequest
import com.bazik.reminder.*
import com.bazik.time.TimeService
import com.bazik.weather.WeatherService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")

    val apiKey = environment.config.propertyOrNull("weather.apiKey")?.getString()
        ?: throw IllegalStateException("OpenWeatherMap API key is not configured")

    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    val weatherService = WeatherService(httpClient, apiKey)
    val timeService = TimeService(httpClient, weatherService)

    // Initialize reminder services if enabled
    val reminderService: ReminderService? = try {
        val remindersEnabled = environment.config.propertyOrNull("reminders.enabled")?.getString()?.toBoolean() ?: false

        if (remindersEnabled) {
            logger.info("Initializing reminder services...")

            // Database setup
            val dbPath = environment.config.propertyOrNull("reminders.database.path")?.getString() ?: "reminders.db"
            val database = Database.connect("jdbc:sqlite:$dbPath", "org.sqlite.JDBC")
            logger.info("Database connected: $dbPath")

            // Repository
            val reminderRepository = ReminderRepository(database)
            logger.info("Reminder repository initialized")

            // Notification config
            val notificationConfig = NotificationConfig(
                emailEnabled = environment.config.propertyOrNull("notifications.email.enabled")?.getString()?.toBoolean() ?: false,
                emailSmtpHost = environment.config.propertyOrNull("notifications.email.smtp.host")?.getString(),
                emailSmtpPort = environment.config.propertyOrNull("notifications.email.smtp.port")?.getString()?.toInt(),
                emailUsername = environment.config.propertyOrNull("notifications.email.username")?.getString(),
                emailPassword = environment.config.propertyOrNull("notifications.email.password")?.getString(),
                emailFrom = environment.config.propertyOrNull("notifications.email.from")?.getString(),
                emailTo = environment.config.propertyOrNull("notifications.email.to")?.getString(),
                telegramEnabled = environment.config.propertyOrNull("notifications.telegram.enabled")?.getString()?.toBoolean() ?: false,
                telegramBotToken = environment.config.propertyOrNull("notifications.telegram.botToken")?.getString(),
                telegramChatId = environment.config.propertyOrNull("notifications.telegram.chatId")?.getString()
            )

            // Notification service
            val notificationService = NotificationService(httpClient, notificationConfig)
            logger.info("Notification service initialized (Email: ${notificationConfig.emailEnabled}, Telegram: ${notificationConfig.telegramEnabled})")

            // Scheduler
            val schedulerService = SchedulerService(reminderRepository, notificationService)

            // Start scheduler if there's an active schedule
            val existingSchedule = reminderRepository.getNotificationSchedule()
            if (existingSchedule != null && existingSchedule.isEnabled) {
                schedulerService.start()
                logger.info("Scheduler started with interval: ${existingSchedule.intervalMinutes} minutes")
            }

            // ReminderService
            val service = ReminderService(reminderRepository, schedulerService)
            logger.info("Reminder service initialized successfully")
            service
        } else {
            logger.info("Reminder services are disabled")
            null
        }
    } catch (e: Exception) {
        logger.error("Failed to initialize reminder services: ${e.message}", e)
        null
    }

    val mcpService = McpService(weatherService, timeService, reminderService)

    routing {
        get("/") {
            call.respondText("Weather MCP Server is running")
        }

        get("/health") {
            val reminderStatus = if (reminderService != null) "enabled" else "disabled"
            call.respond(mapOf(
                "status" to "healthy",
                "reminders" to reminderStatus
            ))
        }

        post("/mcp") {
            try {
                val request = call.receive<JsonRpcRequest>()
                val response = mcpService.handleRequest(request)
                call.respond(response)
            } catch (e: Exception) {
                call.respond(
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "error",
                        "error" to mapOf(
                            "code" to -32700,
                            "message" to "Parse error: ${e.message}"
                        )
                    )
                )
            }
        }
    }
}
