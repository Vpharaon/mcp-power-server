package com.bazik.mcp

import com.bazik.mcp.models.*
import com.bazik.weather.WeatherService
import kotlinx.serialization.json.*

class McpService(private val weatherService: WeatherService) {

    private val tools = listOf(
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
        )
    )

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

        val city = arguments["city"]?.jsonPrimitive?.content
        val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"

        if (city == null) {
            return JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: city is required"
                )
            )
        }

        val resultText = when (toolName) {
            "get_current_weather" -> {
                val weatherResult = weatherService.getCurrentWeather(city, units)
                weatherResult.fold(
                    onSuccess = { weather -> weatherService.formatCurrentWeather(weather) },
                    onFailure = { e -> "Error fetching weather: ${e.message}" }
                )
            }
            "get_weather_forecast" -> {
                val forecastResult = weatherService.getForecast(city, units)
                forecastResult.fold(
                    onSuccess = { forecast -> weatherService.formatForecast(forecast) },
                    onFailure = { e -> "Error fetching forecast: ${e.message}" }
                )
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