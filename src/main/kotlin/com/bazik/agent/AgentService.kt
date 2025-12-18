package com.bazik.agent

import com.bazik.agent.models.AgentRequest
import com.bazik.agent.models.AgentResponse
import com.bazik.agent.models.Message
import com.bazik.agent.models.ToolCall
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class AgentService(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.deepseek.com/v1"
) {
    private val logger = LoggerFactory.getLogger(AgentService::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Обработка задачи агентом с возможностью вызова MCP tools
     */
    suspend fun processTask(
        taskDescription: String,
        availableTools: List<JsonObject>
    ): Result<AgentTaskResult> {
        return try {
            logger.info("Processing task with agent: $taskDescription")

            val messages = listOf(
                Message(
                    role = "system",
                    content = """You are a helpful AI assistant, capable of completing tasks using the available tools.
                                |When you receive a task, analyze it and use the appropriate tools to complete it.
                                |Try to determine whether the task mentions a city or cities and whether you need the weather and current time for them. If there are no such details, simply request the weather and time for Moscow.
                                |Always provide clear information about what you've done.
                                |Your final answer should looks like this:
                                |
                                |📌 title
                                |📅 date
                                |⏰ time
                                |📝 task description
                                |🌤️ weather in the city from description if it exists
                                |🕐 time in the city from description if it exists
                                |
                                |""".trimMargin()
                ),
                Message(
                    role = "user",
                    content = taskDescription
                )
            )

            val request = AgentRequest(
                model = "deepseek-chat",
                messages = messages,
                tools = availableTools,
                toolChoice = "auto"
            )

            val response: HttpResponse = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(Json.encodeToString(AgentRequest.serializer(), request))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("DeepSeek API error: ${response.status}, body: $errorBody")
                return Result.failure(Exception("DeepSeek API error: ${response.status}"))
            }

            val agentResponse = response.body<AgentResponse>()
            val choice = agentResponse.choices.firstOrNull()
                ?: return Result.failure(Exception("No response from agent"))

            val message = choice.message
            val toolCalls = message.toolCalls ?: emptyList()

            Result.success(
                AgentTaskResult(
                    content = message.content ?: "",
                    toolCalls = toolCalls.map { ToolCallInfo(it.function.name, it.function.arguments) },
                    finishReason = choice.finishReason
                )
            )
        } catch (e: Exception) {
            logger.error("Error processing task with agent: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Простой запрос к агенту без использования tools
     */
    suspend fun chat(prompt: String): Result<String> {
        return try {
            val messages = listOf(
                Message(role = "user", content = prompt)
            )

            val request = AgentRequest(
                model = "deepseek-chat",
                messages = messages
            )

            val response: HttpResponse = httpClient.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(Json.encodeToString(AgentRequest.serializer(), request))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.error("DeepSeek API error: ${response.status}, body: $errorBody")
                return Result.failure(Exception("DeepSeek API error: ${response.status}"))
            }

            val agentResponse = response.body<AgentResponse>()
            val content = agentResponse.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("No response from agent"))

            Result.success(content)
        } catch (e: Exception) {
            logger.error("Error chatting with agent: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * Результат выполнения задачи агентом
 */
data class AgentTaskResult(
    val content: String,
    val toolCalls: List<ToolCallInfo>,
    val finishReason: String
)

/**
 * Информация о вызове tool
 */
data class ToolCallInfo(
    val name: String,
    val arguments: String
)