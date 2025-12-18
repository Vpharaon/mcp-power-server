package com.bazik.agent

import com.bazik.mcp.McpService
import com.bazik.mcp.models.JsonRpcRequest
import com.bazik.reminder.NotificationService
import com.bazik.reminder.models.Task
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Сервис для интеграции агента с MCP tools
 * Позволяет агенту вызывать доступные MCP инструменты
 * ОБНОВЛЕН: добавлен метод processTaskNotification согласно спецификации
 */
class AgentIntegrationService(
    private val agentService: AgentService,
    private val mcpService: McpService,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(AgentIntegrationService::class.java)

    /**
     * НОВЫЙ МЕТОД согласно спецификации:
     * Обработка задачи и формирование уведомления
     *
     * Логика:
     * 1. Извлечь город из task.title или task.description
     * 2. Если город найден - получить погоду и время для этого города
     * 3. Если город не найден - получить погоду и время для 3 городов РФ (Москва, СПб, Казань)
     * 4. Сформировать summary по строгому шаблону
     * 5. Отправить summary в Telegram
     */
    suspend fun processTaskNotification(task: Task): Result<String> {
        return try {
            logger.info("Processing task notification for task #${task.id}")

            // Извлечь город из текста задачи
            val city = extractCityFromTask(task)

            val summary: String

            if (city != null) {
                logger.info("City found: $city")
                // Получить погоду и время для найденного города
                val weather = getWeatherForCity(city)
                val time = getTimeForCity(city)

                summary = buildTaskNotificationSummary(task, city, weather, time, isMultiCity = false)
            } else {
                logger.info("No city found, using default Russian cities")
                // Получить погоду и время для 3 городов РФ
                val defaultCities = listOf("Moscow", "Saint Petersburg", "Kazan")
                val citiesData = mutableMapOf<String, Pair<String, String>>() // city -> (weather, time)

                for (cityName in defaultCities) {
                    val weather = getWeatherForCity(cityName)
                    val time = getTimeForCity(cityName)
                    citiesData[cityName] = Pair(weather, time)
                }

                summary = buildTaskNotificationSummaryMultiCity(task, citiesData)
            }

            // Отправить summary в Telegram
            val sendResult = notificationService.sendNotification(
                subject = "Task Reminder: ${task.title}",
                body = summary
            )

            sendResult.fold(
                onSuccess = {
                    logger.info("Task notification sent successfully")
                    Result.success(summary)
                },
                onFailure = { error ->
                    logger.error("Failed to send task notification: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            logger.error("Error processing task notification: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Извлечь название города из задачи
     * Простая эвристика: ищем слова с большой буквы (кириллица или латиница)
     */
    private fun extractCityFromTask(task: Task): String? {
        val text = "${task.title} ${task.description}"

        // Regex для поиска слов с большой буквы (кириллица)
        val cyrillicPattern = Regex("""(?:^|[^\p{L}])([А-ЯЁ][а-яё]{2,})(?:[^\p{L}]|$)""")
        val cyrillicMatches = cyrillicPattern.findAll(text)

        // Список известных городов (можно расширить)
        val knownCities = setOf(
            "Москва", "Санкт-Петербург", "Петербург", "Казань", "Новосибирск",
            "Екатеринбург", "Нижний", "Челябинск", "Самара", "Омск", "Ростов",
            "Уфа", "Красноярск", "Воронеж", "Пермь", "Волгоград", "Краснодар",
            "Саратов", "Тюмень", "Тольятти", "Ижевск", "Барнаул", "Ульяновск",
            "Moscow", "Petersburg", "Kazan", "Novosibirsk"
        )

        // Ищем совпадение с известными городами
        for (match in cyrillicMatches) {
            val word = match.groupValues[1]
            if (knownCities.contains(word)) {
                return word
            }
        }

        // Если точного совпадения нет, возвращаем первое слово с большой буквы (если есть)
        val firstMatch = cyrillicMatches.firstOrNull()
        if (firstMatch != null) {
            return firstMatch.groupValues[1]
        }

        return null
    }

    /**
     * Получить погоду для города через MCP
     */
    private suspend fun getWeatherForCity(city: String): String {
        return try {
            val request = JsonRpcRequest(
                jsonrpc = "2.0",
                id = "weather-${System.currentTimeMillis()}",
                method = "tools/call",
                params = buildJsonObject {
                    put("name", "get_current_weather")
                    put("arguments", buildJsonObject {
                        put("city", city)
                        put("units", "metric")
                    })
                }
            )

            val response = mcpService.handleRequest(request)

            if (response.error != null) {
                return "Погода недоступна"
            }

            val result = response.result ?: return "Погода недоступна"
            val content = result.jsonObject["content"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = content?.get("text")?.jsonPrimitive?.content ?: "Погода недоступна"

            text
        } catch (e: Exception) {
            logger.error("Error getting weather for $city: ${e.message}", e)
            "Погода недоступна"
        }
    }

    /**
     * Получить время для города через MCP
     */
    private suspend fun getTimeForCity(city: String): String {
        return try {
            val request = JsonRpcRequest(
                jsonrpc = "2.0",
                id = "time-${System.currentTimeMillis()}",
                method = "tools/call",
                params = buildJsonObject {
                    put("name", "get_city_time")
                    put("arguments", buildJsonObject {
                        put("city", city)
                    })
                }
            )

            val response = mcpService.handleRequest(request)

            if (response.error != null) {
                return "Время недоступно"
            }

            val result = response.result ?: return "Время недоступно"
            val content = result.jsonObject["content"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = content?.get("text")?.jsonPrimitive?.content ?: "Время недоступно"

            text
        } catch (e: Exception) {
            logger.error("Error getting time for $city: ${e.message}", e)
            "Время недоступно"
        }
    }

    /**
     * Сформировать summary для одного города
     */
    private fun buildTaskNotificationSummary(
        task: Task,
        city: String,
        weather: String,
        time: String,
        isMultiCity: Boolean
    ): String {
        val dateTime = LocalDateTime.parse(task.reminderDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        return buildString {
            appendLine("🔔 Напоминание о задаче!")
            appendLine("📌 Заголовок: ${task.title}")
            appendLine("📅 Дата: ${dateTime.toLocalDate()}")
            appendLine("⏰ Время: ${dateTime.toLocalTime()}")
            appendLine("📝 Текст: ${task.description}")
            appendLine("🌤️ Погода в $city: $weather")
            appendLine("🕐 Время в $city: $time")
        }
    }

    /**
     * Сформировать summary для нескольких городов
     */
    private fun buildTaskNotificationSummaryMultiCity(
        task: Task,
        citiesData: Map<String, Pair<String, String>>
    ): String {
        val dateTime = LocalDateTime.parse(task.reminderDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        return buildString {
            appendLine("🔔 Напоминание о задаче!")
            appendLine("📌 Заголовок: ${task.title}")
            appendLine("📅 Дата: ${dateTime.toLocalDate()}")
            appendLine("⏰ Время: ${dateTime.toLocalTime()}")
            appendLine("📝 Текст: ${task.description}")
            appendLine("🌤️ Погода в городах РФ:")
            citiesData.forEach { (city, data) ->
                val (weather, time) = data
                appendLine("- $city: $weather")
            }
        }
    }

    /**
     * Обработка задачи агентом с возможностью вызова MCP tools
     * (старый метод, сохранен для совместимости)
     */
    suspend fun executeTask(taskDescription: String): Result<String> {
        return try {
            logger.info("Executing task with agent: $taskDescription")

            // Получаем список доступных tools из MCP
            val availableTools = getAvailableMcpTools()

            if (availableTools.isEmpty()) {
                logger.warn("No MCP tools available for agent")
                return agentService.chat(taskDescription)
            }

            // Передаем задачу агенту с доступными tools
            val agentResult = agentService.processTask(taskDescription, availableTools)

            agentResult.fold(
                onSuccess = { taskResult ->
                    val resultBuilder = StringBuilder()

                    // Добавляем ответ агента
                    if (taskResult.content.isNotBlank()) {
                        resultBuilder.appendLine("Agent response: ${taskResult.content}")
                    }

                    // Если агент хочет вызвать tools, выполняем их
                    if (taskResult.toolCalls.isNotEmpty()) {
                        resultBuilder.appendLine("\nExecuting ${taskResult.toolCalls.size} tool(s):")

                        for (toolCall in taskResult.toolCalls) {
                            try {
                                val toolResult = executeMcpTool(toolCall.name, toolCall.arguments)
                                resultBuilder.appendLine("\n[${toolCall.name}]:")
                                resultBuilder.appendLine(toolResult)
                            } catch (e: Exception) {
                                logger.error("Error executing tool ${toolCall.name}: ${e.message}", e)
                                resultBuilder.appendLine("\n[${toolCall.name}]: Error - ${e.message}")
                            }
                        }
                    }

                    Result.success(resultBuilder.toString())
                },
                onFailure = { error ->
                    logger.error("Agent task execution failed: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            logger.error("Error in executeTask: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Получить список доступных MCP tools в формате для агента
     */
    private suspend fun getAvailableMcpTools(): List<JsonObject> {
        return try {
            // Запрашиваем список tools через MCP
            val request = JsonRpcRequest(
                jsonrpc = "2.0",
                id = "tools-list",
                method = "tools/list",
                params = null
            )

            val response = mcpService.handleRequest(request)
            val result = response.result ?: return emptyList()

            // Извлекаем tools из ответа
            val toolsList = result.jsonObject["tools"]?.jsonArray ?: return emptyList()

            // Конвертируем в формат для DeepSeek API
            toolsList.map { tool ->
                val toolObj = tool.jsonObject
                JsonObject(mapOf(
                    "type" to JsonPrimitive("function"),
                    "function" to JsonObject(mapOf(
                        "name" to (toolObj["name"] ?: JsonPrimitive("")),
                        "description" to (toolObj["description"] ?: JsonPrimitive("")),
                        "parameters" to (toolObj["inputSchema"] ?: buildJsonObject {})
                    ))
                ))
            }
        } catch (e: Exception) {
            logger.error("Error getting available MCP tools: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Выполнить MCP tool
     */
    private suspend fun executeMcpTool(toolName: String, argumentsJson: String): String {
        return try {
            // Парсим аргументы
            val arguments = Json.parseToJsonElement(argumentsJson).jsonObject

            // Создаем MCP запрос
            val request = JsonRpcRequest(
                jsonrpc = "2.0",
                id = "tool-call-${System.currentTimeMillis()}",
                method = "tools/call",
                params = buildJsonObject {
                    put("name", toolName)
                    put("arguments", arguments)
                }
            )

            // Выполняем через MCP
            val response = mcpService.handleRequest(request)

            if (response.error != null) {
                return "Error: ${response.error.message}"
            }

            // Извлекаем результат
            val result = response.result ?: return "No result"
            val content = result.jsonObject["content"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = content?.get("text")?.jsonPrimitive?.content ?: "No content"

            text
        } catch (e: Exception) {
            logger.error("Error executing MCP tool $toolName: ${e.message}", e)
            "Error executing tool: ${e.message}"
        }
    }
}