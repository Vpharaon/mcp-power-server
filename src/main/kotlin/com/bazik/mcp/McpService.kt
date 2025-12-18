package com.bazik.mcp

import com.bazik.mcp.models.*
import com.bazik.time.TimeService
import com.bazik.weather.WeatherService
import kotlinx.serialization.json.*

class McpService(
    private val weatherService: WeatherService,
    private val timeService: TimeService,
    private val taskService: com.bazik.reminder.TaskService? = null
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

        // Add task tools if service is available
        if (taskService != null) {
            addAll(listOf(
                Tool(
                    name = "add_task",
                    description = "Create a new task with optional title (auto-generated if empty), description, reminder time, optional recurrence and importance",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "title" to PropertySchema(
                                type = "string",
                                description = "Optional short title (if empty, will be auto-generated from description)"
                            ),
                            "description" to PropertySchema(
                                type = "string",
                                description = "Detailed description of the task"
                            ),
                            "reminder_time" to PropertySchema(
                                type = "string",
                                description = "Reminder date and time in ISO format: 2024-12-17T15:30:00 (server time)"
                            ),
                            "recurrence" to PropertySchema(
                                type = "string",
                                description = "Optional recurrence pattern",
                                enum = listOf("DAILY", "WEEKLY", "MONTHLY")
                            ),
                            "importance" to PropertySchema(
                                type = "string",
                                description = "Task importance level",
                                enum = listOf("LOW", "MEDIUM", "HIGH", "URGENT")
                            )
                        ),
                        required = listOf("description", "reminder_time")
                    )
                ),
                Tool(
                    name = "list_tasks",
                    description = "List all tasks or filter by status (ACTIVE, COMPLETED)",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "status" to PropertySchema(
                                type = "string",
                                description = "Optional status filter",
                                enum = listOf("ACTIVE", "COMPLETED")
                            )
                        ),
                        required = emptyList()
                    )
                ),
                Tool(
                    name = "get_task",
                    description = "Get detailed information about a specific task by ID",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "id" to PropertySchema(
                                type = "number",
                                description = "Task ID"
                            )
                        ),
                        required = listOf("id")
                    )
                ),
                Tool(
                    name = "complete_task",
                    description = "Mark a task as completed by ID",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "id" to PropertySchema(
                                type = "number",
                                description = "Task ID to complete"
                            )
                        ),
                        required = listOf("id")
                    )
                ),
                Tool(
                    name = "delete_task",
                    description = "Delete a task by ID",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "id" to PropertySchema(
                                type = "number",
                                description = "Task ID to delete"
                            )
                        ),
                        required = listOf("id")
                    )
                ),
                Tool(
                    name = "get_tasks_for_date",
                    description = "Get all tasks scheduled for a specific date",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "date" to PropertySchema(
                                type = "string",
                                description = "Date in ISO format: 2024-12-17"
                            )
                        ),
                        required = listOf("date")
                    )
                ),
                Tool(
                    name = "get_tasks_by_importance",
                    description = "Get all tasks filtered by importance level",
                    inputSchema = InputSchema(
                        properties = mapOf(
                            "importance" to PropertySchema(
                                type = "string",
                                description = "Importance level",
                                enum = listOf("LOW", "MEDIUM", "HIGH", "URGENT")
                            )
                        ),
                        required = listOf("importance")
                    )
                ),
                Tool(
                    name = "get_tasks_summary",
                    description = "Get a comprehensive summary of all tasks including statistics, overdue items, and high priority tasks",
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
            "get_current_weather" -> {
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

            // Task tools
            "add_task" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                val title = arguments["title"]?.jsonPrimitive?.content
                val description = arguments["description"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: description"))
                val reminderTime = arguments["reminder_time"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: reminder_time"))
                val recurrence = arguments["recurrence"]?.jsonPrimitive?.content
                val importance = arguments["importance"]?.jsonPrimitive?.content ?: "MEDIUM"

                taskService.addTask(title, description, reminderTime, recurrence, importance)
            }
            "list_tasks" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                val status = arguments["status"]?.jsonPrimitive?.content
                taskService.listTasks(status)
            }
            "get_task" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                val id = arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing or invalid required parameter: id"))

                taskService.getTaskById(id)
            }
            "complete_task" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                val id = arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing or invalid required parameter: id"))

                taskService.completeTask(id)
            }
            "delete_task" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                val id = arguments["id"]?.jsonPrimitive?.longOrNull
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing or invalid required parameter: id"))

                taskService.deleteTask(id)
            }
            "get_tasks_for_date" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                val date = arguments["date"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: date"))

                taskService.getTasksForDate(date)
            }
            "get_tasks_by_importance" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                val importance = arguments["importance"]?.jsonPrimitive?.content
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing required parameter: importance"))

                taskService.getTasksByImportance(importance)
            }
            "get_tasks_summary" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                taskService.getSummary()
            }
            "set_notification_schedule" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                val intervalMinutes = arguments["interval_minutes"]?.jsonPrimitive?.intOrNull
                    ?: return JsonRpcResponse(id = request.id, error = JsonRpcError(-32602, "Missing or invalid required parameter: interval_minutes"))
                val enabled = arguments["enabled"]?.jsonPrimitive?.booleanOrNull ?: true

                taskService.setNotificationSchedule(intervalMinutes, enabled)
            }
            "get_notification_schedule" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                taskService.getNotificationSchedule()
            }
            "send_test_notification" -> {
                if (taskService == null) return JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Task service not available"))

                taskService.sendTestNotification()
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