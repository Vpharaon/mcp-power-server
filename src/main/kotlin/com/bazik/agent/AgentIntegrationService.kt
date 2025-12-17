package com.bazik.agent

import com.bazik.mcp.McpService
import com.bazik.mcp.models.JsonRpcRequest
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Сервис для интеграции агента с MCP tools
 * Позволяет агенту вызывать доступные MCP инструменты
 */
class AgentIntegrationService(
    private val agentService: AgentService,
    private val mcpService: McpService
) {
    private val logger = LoggerFactory.getLogger(AgentIntegrationService::class.java)

    /**
     * Обработка задачи агентом с возможностью вызова MCP tools
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