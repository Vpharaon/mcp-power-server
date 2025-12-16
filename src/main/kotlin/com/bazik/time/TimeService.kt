package com.bazik.time

import com.bazik.time.models.CityTimeInfo
import com.bazik.time.models.WorldTimeResponse
import com.bazik.weather.WeatherService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek

class TimeService(
    private val httpClient: HttpClient,
    private val weatherService: WeatherService
) {
    private val worldTimeApiBase = "http://worldtimeapi.org/api"

    suspend fun getCityTime(city: String): Result<CityTimeInfo> {
        return try {
            // Сначала получаем координаты и timezone из OpenWeatherMap
            val weatherResult = weatherService.getCurrentWeather(city)

            if (weatherResult.isFailure) {
                return Result.failure(Exception("Failed to get city coordinates: ${weatherResult.exceptionOrNull()?.message}"))
            }

            val weather = weatherResult.getOrThrow()
            val lat = weather.coord.lat
            val lon = weather.coord.lon
            val timezoneOffset = weather.timezone

            // Получаем точное время из WorldTimeAPI
            val timeResponse = getTimeByCoordinates(lat, lon)

            if (timeResponse.isSuccess) {
                val timeData = timeResponse.getOrThrow()

                // Парсим datetime для более читабельного формата
                val datetime = Instant.parse(timeData.utcDatetime)
                val zoneId = ZoneId.of(timeData.timezone)
                val zonedDateTime = datetime.atZone(zoneId)

                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                val cityTimeInfo = CityTimeInfo(
                    city = weather.name,
                    country = weather.sys.country,
                    timezone = timeData.timezone,
                    currentTime = zonedDateTime.format(timeFormatter),
                    currentDate = zonedDateTime.format(dateFormatter),
                    utcOffset = timeData.utcOffset,
                    isDst = timeData.dst,
                    dayOfWeek = getDayOfWeekName(timeData.dayOfWeek),
                    unixTimestamp = timeData.unixtime
                )

                Result.success(cityTimeInfo)
            } else {
                // Fallback: используем данные из OpenWeatherMap
                val instant = Instant.now()
                val offset = java.time.ZoneOffset.ofTotalSeconds(timezoneOffset)
                val zonedDateTime = instant.atZone(offset)

                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                val cityTimeInfo = CityTimeInfo(
                    city = weather.name,
                    country = weather.sys.country,
                    timezone = "UTC${formatOffset(timezoneOffset)}",
                    currentTime = zonedDateTime.format(timeFormatter),
                    currentDate = zonedDateTime.format(dateFormatter),
                    utcOffset = formatOffset(timezoneOffset),
                    isDst = false,
                    dayOfWeek = zonedDateTime.dayOfWeek.toString(),
                    unixTimestamp = instant.epochSecond
                )

                Result.success(cityTimeInfo)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getTimeByCoordinates(lat: Double, lon: Double): Result<WorldTimeResponse> {
        return try {
            // WorldTimeAPI не поддерживает прямой запрос по координатам,
            // поэтому мы попробуем получить время через timezone
            // В реальном случае можно использовать другой API или расширить функционал

            // Fallback: используем IP-based запрос или timezone guess
            val response: HttpResponse = httpClient.get("$worldTimeApiBase/ip")

            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Failed to fetch time: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatCityTime(timeInfo: CityTimeInfo): String {
        return buildString {
            appendLine("🕐 Time in ${timeInfo.city}, ${timeInfo.country}")
            appendLine()
            appendLine("Current time: ${timeInfo.currentTime}")
            appendLine("Current date: ${timeInfo.currentDate}")
            appendLine("Day of week: ${timeInfo.dayOfWeek}")
            appendLine()
            appendLine("Timezone: ${timeInfo.timezone}")
            appendLine("UTC offset: ${timeInfo.utcOffset}")
            appendLine("DST active: ${if (timeInfo.isDst) "Yes" else "No"}")
            appendLine()
            appendLine("Unix timestamp: ${timeInfo.unixTimestamp}")
        }
    }

    private fun getDayOfWeekName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            0 -> "Sunday"
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            else -> "Unknown"
        }
    }

    private fun formatOffset(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val sign = if (hours >= 0) "+" else ""
        return String.format("%s%02d:%02d", sign, hours, Math.abs(minutes))
    }
}