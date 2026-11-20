package com.wefit.app.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromHourly(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun toHourly(value: String): List<Int> {
        val parsed = if (value.isBlank()) emptyList() else value.split(",").map { it.trim().toIntOrNull() ?: 0 }
        return List(24) { parsed.getOrElse(it) { 0 } }
    }
}
