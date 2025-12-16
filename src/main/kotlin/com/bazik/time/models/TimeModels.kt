package com.bazik.time.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorldTimeResponse(
    val abbreviation: String,
    @SerialName("client_ip")
    val clientIp: String? = null,
    val datetime: String,
    @SerialName("day_of_week")
    val dayOfWeek: Int,
    @SerialName("day_of_year")
    val dayOfYear: Int,
    val dst: Boolean,
    @SerialName("dst_from")
    val dstFrom: String? = null,
    @SerialName("dst_offset")
    val dstOffset: Int,
    @SerialName("dst_until")
    val dstUntil: String? = null,
    @SerialName("raw_offset")
    val rawOffset: Int,
    val timezone: String,
    val unixtime: Long,
    @SerialName("utc_datetime")
    val utcDatetime: String,
    @SerialName("utc_offset")
    val utcOffset: String,
    @SerialName("week_number")
    val weekNumber: Int
)

@Serializable
data class CityTimeInfo(
    val city: String,
    val country: String,
    val timezone: String,
    val currentTime: String,
    val currentDate: String,
    val utcOffset: String,
    val isDst: Boolean,
    val dayOfWeek: String,
    val unixTimestamp: Long
)