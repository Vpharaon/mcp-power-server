package com.bazik.weather

import com.bazik.weather.models.CurrentWeatherResponse
import com.bazik.weather.models.ForecastResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class WeatherService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    private val baseUrl = "https://api.openweathermap.org/data/2.5"

    suspend fun getCurrentWeather(city: String, units: String = "metric"): Result<CurrentWeatherResponse> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/weather") {
                parameter("q", city)
                parameter("appid", apiKey)
                parameter("units", units)
            }

            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch weather: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getForecast(city: String, units: String = "metric"): Result<ForecastResponse> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/forecast") {
                parameter("q", city)
                parameter("appid", apiKey)
                parameter("units", units)
            }

            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch forecast: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatCurrentWeather(weather: CurrentWeatherResponse): String {
        return buildString {
            appendLine("Current weather in ${weather.name}, ${weather.sys.country}:")
            appendLine("Temperature: ${weather.main.temp}°C (feels like ${weather.main.feelsLike}°C)")
            appendLine("Conditions: ${weather.weather.firstOrNull()?.description ?: "N/A"}")
            appendLine("Humidity: ${weather.main.humidity}%")
            appendLine("Wind speed: ${weather.wind.speed} m/s")
            appendLine("Pressure: ${weather.main.pressure} hPa")
        }
    }

    fun formatForecast(forecast: ForecastResponse): String {
        return buildString {
            appendLine("Weather forecast for ${forecast.city.name}, ${forecast.city.country}:")
            appendLine()
            forecast.list.take(8).forEach { item ->
                appendLine("${item.dtTxt}:")
                appendLine("  Temperature: ${item.main.temp}°C (feels like ${item.main.feelsLike}°C)")
                appendLine("  Conditions: ${item.weather.firstOrNull()?.description ?: "N/A"}")
                appendLine("  Humidity: ${item.main.humidity}%")
                appendLine("  Wind: ${item.wind.speed} m/s")
                appendLine()
            }
        }
    }
}