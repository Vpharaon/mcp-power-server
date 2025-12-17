package com.bazik.mcp

import com.bazik.mcp.models.*
import com.bazik.reminder.ReminderService
import com.bazik.time.TimeService
import com.bazik.weather.WeatherService
import kotlinx.serialization.json.*

class McpService(
    private val weatherService: WeatherService,
    private val timeService: TimeService,
    private val reminderService: ReminderService? = null
) {

    private val tools = buildList {
        addAll(listOf(
        Tool(
            name = "get_current_weather",
            description = "Get current weather for a specified city",
            inputSchema = InputSchema(
                properties = mapOf(
                    "city" to PropertySchema(
                        type = "string",
                        description = "City name (e.g., 'London', 'New York', 'Tokyo')"
                    ),
                    "units" to PropertySchema(
                        type = "string",
                        description = "Temperature units",
                        enum = listOf("metric", "imperial", "standard")
                    )
                ),
                required = listOf("city")
            )
        ),
        Tool(
            name = "get_weather_forecast",
            description = "Get 5-day weather forecast for a specified city",
            inputSchema = InputSchema(
                properties = mapOf(
                    "city" to PropertySchema(
                        type = "string",
                        description = "City name (e.g., 'London', 'New York', 'Tokyo')"
                    ),
                    "units" to PropertySchema(
                        type = "string",
                        description = "Temperature units",
                        enum = listOf("metric", "imperial", "standard")
                    )
                ),
                required = listOf("city")
            )
        ),
        Tool(
            name = "get_city_time",
            description = "Get current time, date, and timezone information for a specified city",
            inputSchema = InputSchema(
                properties = mapOf(
                    "city" to PropertySchema(
                        type = "string",
                        description = "City name (e.g., 'London', 'New York', 'Tokyo')"
                    )
                ),
                required = listOf("city")
            )
        )
        ))

        // Add reminder tools if service is available
        if (reminderService != null) {
            addAll(listOf(
                Tool(
                    name = "add_reminder",
                    description = "Create a new reminder with title, description, optional due date and priority",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "title" to PropertySchema(
                                type = "string",
                                description = "Short title for the reminder"
                            ),
                            "description" to PropertySchema(
                                type = "string",
                                description = "Detailed description of what needs to be done"
                            ),
                            "due_date" to PropertySchema(
                                type = "string",
                                description = "Optional due date in ISO format: 2024-12-17T15:30:00"
                            ),
                            "priority" to PropertySchema(
                                type = "string",
                                description = "Priority level",
                                enum = listOf("LOW", "MEDIUM", "HIGH", "URGENT")
                            )
                        ),
                        required = listOf("title", "description")
                    )
                ),
                Tool(
                    name = "list_reminders",
                    description = "List all reminders or filter by status (ACTIVE, COMPLETED, ARCHIVED)",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "status" to PropertySchema(
                                type = "string",
                                description = "Optional status filter",
                                enum = listOf("ACTIVE", "COMPLETED", "ARCHIVED")
                            )
                        ),
                        required = emptyList()
                    )
                ),
                Tool(
                    name = "get_reminder",
                    description = "Get detailed information about a specific reminder by ID",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "id" to PropertySchema(
                                type = "number",
                                description = "Reminder ID"
                            )
                        ),
                        required = listOf("id")
                    )
                ),
                Tool(
                    name = "complete_reminder",
                    description = "Mark a reminder as completed by ID",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "id" to PropertySchema(
                                type = "number",
                                description = "Reminder ID to complete"
                            )
                        ),
                        required = listOf("id")
                    )
                ),
                Tool(
                    name = "delete_reminder",
                    description = "Delete a reminder by ID",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "id" to PropertySchema(
                                type = "number",
                                description = "Reminder ID to delete"
                            )
                        ),
                        required = listOf("id")
                    )
                ),
                Tool(
                    name = "get_reminders_summary",
                    description = "Get a comprehensive summary of all reminders including statistics, overdue items, and high priority tasks",
                    inputSchema = InputSchema(
                        properties = emptyMap(),
                        required = emptyList()
                    )
                ),
                Tool(
                    name = "set_notification_schedule",
                    description = "Configure automatic periodic notifications (email/telegram) with summary of reminders",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "interval_minutes" to PropertySchema(
                                type = "number",
                                description = "How often to send notifications in minutes (e.g., 60 for hourly, 1440 for daily)"
                            ),
                            "enabled" to PropertySchema(
                                type = "boolean",
                                description = "Enable or disable notifications (default: true)"
                            )
                        ),
                        required = listOf("interval_minutes")
                    )
                ),
                Tool(
                    name = "get_notification_schedule",
                    description = "Get current notification schedule configuration",
                    inputSchema = InputSchema(
                        properties = emptyMap(),
                        required = emptyList()
                    )
                ),
                Tool(
                    name = "send_test_notification",
                    description = "Send a test notification immediately to verify email/telegram configuration",
                    inputSchema = InputSchema(
                        properties = emptyMap(),
                        required = emptyList()
                    )
                )
            ))
        }
    }

    suspend fun handleRequest(request: JsonRpcRequest): JsonRpcResponse {
        return try {
            when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> handleToolsList(request)
                "tools/call" -> handleToolsCall(request)
                else -> JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(
                        code = -32601,
                        message = "Method not found: ${request.method}"
                    )
                )
            }
        } catch (e: Exception) {
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32603,
                    message = "Internal error: ${e.message}"
                )
            )
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        val result = InitializeResult(
            protocolVersion = "2024-11-05",
            capabilities = Capabilities(
                tools = ToolsCapability(supported = true)
            ),
            serverInfo = ServerInfo(
                name = "weather-mcp-server",
                version = "1.0.0"
            )
        )
        return JsonRpcResponse(
            id = request.id,
            result = Json.encodeToJsonElement(result)
        )
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val result = ToolsList(tools = tools)
        return JsonRpcResponse(
            id = request.id,
            result = Json.encodeToJsonElement(result)
        )
    }

    private suspend fun handleToolsCall(request: JsonRpcRequest): JsonRpcResponse {
        val params = request.params ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = -32602,
                message = "Invalid params: params is required"
            )
        )

        val toolName = params["name"]?.jsonPrimitive?.content
        val arguments = params["arguments"]?.jsonObject

        if (toolName == null || arguments == null) {
            return JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: name and arguments are required"
                )
            )
        }

        val resultText = when (toolName) {
            // Weather tools
            "get_current_weather" -> {
                val city = arguments["city"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: city"))
                val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"

                val weatherResult = weatherService.getCurrentWeather(city, units)
                weatherResult.fold(
                    onSuccess = { weather -> weatherService.formatCurrentWeather(weather) },
                    onFailure = { e -> "Error fetching weather: ${e.message}" }
                )
            }
            "get_weather_forecast" -> {
                val city = arguments["city"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: city"))
                val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"

                val forecastResult = weatherService.getForecast(city, units)
                forecastResult.fold(
                    onSuccess = { forecast -> weatherService.formatForecast(forecast) },
                    onFailure = { e -> "Error fetching forecast: ${e.message}" }
                )
            }
            "get_city_time" -> {
                val city = arguments["city"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: city"))

                val timeResult = timeService.getCityTime(city)
                timeResult.fold(
                    onSuccess = { timeInfo -> timeService.formatCityTime(timeInfo) },
                    onFailure = { e -> "Error fetching time: ${e.message}" }
                )
            }

            // Reminder tools
            "add_reminder" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                val title = arguments["title"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: title"))
                val description = arguments["description"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: description"))
                val dueDate = arguments["due_date"]?.jsonPrimitive?.content
                val priority = arguments["priority"]?.jsonPrimitive?.content ?: "MEDIUM"

                reminderService.addReminder(title, description, dueDate, priority)
            }
            "list_reminders" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                val status = arguments["status"]?.jsonPrimitive?.content
                reminderService.listReminders(status)
            }
            "get_reminder" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                val id = arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing or invalid required parameter: id"))

                reminderService.getReminderById(id)
            }
            "complete_reminder" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                val id = arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing or invalid required parameter: id"))

                reminderService.completeReminder(id)
            }
            "delete_reminder" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                val id = arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing or invalid required parameter: id"))

                reminderService.deleteReminder(id)
            }
            "get_reminders_summary" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                reminderService.getSummary()
            }
            "set_notification_schedule" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                val intervalMinutes = arguments["interval_minutes"]?.jsonPrimitive?.intOrNull
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing or invalid required parameter: interval_minutes"))
                val enabled = arguments["enabled"]?.jsonPrimitive?.booleanOrNull ?: true

                reminderService.setNotificationSchedule(intervalMinutes, enabled)
            }
            "get_notification_schedule" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                reminderService.getNotificationSchedule()
            }
            "send_test_notification" -> {
                if (reminderService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Reminder service not available"))

                reminderService.sendTestNotification()
            }

            else -> "Unknown tool: $toolName"
        }

        val result = ToolCallResult(
            content = listOf(TextContent(text = resultText))
        )

        return JsonRpcResponse(
            id = request.id,
            result = Json.encodeToJsonElement(result)
        )
    }
}