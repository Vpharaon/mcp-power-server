package com.bazik

import com.bazik.mcp.McpService
import com.bazik.mcp.models.JsonRpcRequest
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

fun Application.configureRouting() {
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
    val mcpService = McpService(weatherService)

    routing {
        get("/") {
            call.respondText("Weather MCP Server is running")
        }

        get("/health") {
            call.respond(mapOf("status" to "healthy"))
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
