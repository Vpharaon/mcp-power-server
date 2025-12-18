package com.bazik

import com.bazik.agent.AgentIntegrationService
import com.bazik.agent.AgentService
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
import java.time.ZoneId

fun Application.configureRouting() {
    val logger = LoggerFactory.getLogger("Routing")

    // Read timezone configuration
    val defaultTimezone = environment.config.propertyOrNull("timezone.default")?.getString() ?: "Europe/Moscow"
    val defaultZoneId = try {
        ZoneId.of(defaultTimezone)
    } catch (e: Exception) {
        logger.warn("Invalid timezone '$defaultTimezone', falling back to Europe/Moscow")
        ZoneId.of("Europe/Moscow")
    }
    logger.info("Using timezone: $defaultZoneId")

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

    // Initialize task services first (before MCP and Agent)
    val taskService: com.bazik.reminder.TaskService? = try {
        val tasksEnabled = environment.config.propertyOrNull("tasks.enabled")?.getString()?.toBoolean() ?: false

        if (tasksEnabled) {
            logger.info("Initializing task services...")

            // Database setup
            val dbPath = environment.config.propertyOrNull("tasks.database.path")?.getString() ?: "tasks.db"
            val database = Database.connect("jdbc:sqlite:$dbPath", "org.sqlite.JDBC")
            logger.info("Database connected: $dbPath")

            // Repository
            val taskRepository = com.bazik.reminder.TaskRepository(database, defaultZoneId)
            logger.info("Task repository initialized")

            // Notification config
            val telegramEnabledStr = environment.config.propertyOrNull("notifications.telegram.enabled")?.getString()
            val telegramBotToken = environment.config.propertyOrNull("notifications.telegram.botToken")?.getString()
            val telegramChatId = environment.config.propertyOrNull("notifications.telegram.chatId")?.getString()

            logger.info("Telegram config: enabled='$telegramEnabledStr', hasToken=${telegramBotToken != null}, hasChatId=${telegramChatId != null}")

            val notificationConfig = NotificationConfig(
                emailEnabled = environment.config.propertyOrNull("notifications.email.enabled")?.getString()?.toBoolean() ?: false,
                emailSmtpHost = environment.config.propertyOrNull("notifications.email.smtp.host")?.getString(),
                emailSmtpPort = environment.config.propertyOrNull("notifications.email.smtp.port")?.getString()?.toInt(),
                emailUsername = environment.config.propertyOrNull("notifications.email.username")?.getString(),
                emailPassword = environment.config.propertyOrNull("notifications.email.password")?.getString(),
                emailFrom = environment.config.propertyOrNull("notifications.email.from")?.getString(),
                emailTo = environment.config.propertyOrNull("notifications.email.to")?.getString(),
                telegramEnabled = telegramEnabledStr?.toBoolean() ?: false,
                telegramBotToken = telegramBotToken,
                telegramChatId = telegramChatId
            )

            // Notification service
            val notificationService = NotificationService(httpClient, notificationConfig)
            logger.info("Notification service initialized (Email: ${notificationConfig.emailEnabled}, Telegram: ${notificationConfig.telegramEnabled})")

            // Временно создаем MCP и Agent без финального сервиса
            val mcpServiceTemp = McpService(weatherService, timeService, null)

            // Initialize AI agent with notification service
            val agentIntegrationService: AgentIntegrationService? = try {
                val agentEnabled = environment.config.propertyOrNull("agent.enabled")?.getString()?.toBoolean() ?: false

                if (agentEnabled) {
                    val deepseekApiKey = environment.config.propertyOrNull("agent.deepseek.apiKey")?.getString()
                        ?: throw IllegalStateException("DeepSeek API key is not configured")
                    val baseUrl = environment.config.propertyOrNull("agent.deepseek.baseUrl")?.getString()
                        ?: "https://api.deepseek.com/v1"

                    logger.info("Initializing AI agent...")
                    val agentService = AgentService(httpClient, deepseekApiKey, baseUrl)
                    val integrationService = AgentIntegrationService(agentService, mcpServiceTemp, notificationService)
                    logger.info("AI agent initialized successfully")
                    integrationService
                } else {
                    logger.info("AI agent is disabled")
                    null
                }
            } catch (e: Exception) {
                logger.error("Failed to initialize AI agent: ${e.message}", e)
                null
            }

            // Scheduler with agent integration
            val schedulerService = com.bazik.reminder.SchedulerService(
                taskRepository,
                notificationService,
                defaultZoneId,
                agentIntegrationService
            )

            // Start scheduler - always start if tasks are enabled
            // Scheduler will check tasks every minute
            schedulerService.start()
            logger.info("Scheduler started (checks every minute)")

            val existingSchedule = taskRepository.getNotificationSchedule()
            if (existingSchedule != null && existingSchedule.isEnabled) {
                logger.info("Notification schedule active: ${existingSchedule.intervalMinutes} minutes")
            }

            if (agentIntegrationService != null) {
                logger.info("Agent task execution enabled - scheduler will process task notifications")
            }

            // TaskService
            val service = com.bazik.reminder.TaskService(taskRepository, schedulerService)
            logger.info("Task service initialized successfully")
            service
        } else {
            logger.info("Task services are disabled")
            null
        }
    } catch (e: Exception) {
        logger.error("Failed to initialize task services: ${e.message}", e)
        null
    }

    // Update MCP service with taskService
    val mcpServiceFinal = McpService(weatherService, timeService, taskService)

    routing {
        get("/") {
            call.respondText("Weather MCP Server is running")
        }

        get("/health") {
            val taskStatus = if (taskService != null) "enabled" else "disabled"
            call.respond(mapOf(
                "status" to "healthy",
                "tasks" to taskStatus
            ))
        }

        post("/mcp") {
            try {
                val request = call.receive<JsonRpcRequest>()
                val response = mcpServiceFinal.handleRequest(request)
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
