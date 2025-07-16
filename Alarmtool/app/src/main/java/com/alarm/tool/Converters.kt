package com.alarm.tool

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromDaysOfWeek(value: List<Int>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDaysOfWeek(value: String): List<Int> {
        val type = object : TypeToken<List<Int>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromTaskType(value: Alarm.TaskType): String {
        return value.name
    }

    @TypeConverter
    fun toTaskType(value: String): Alarm.TaskType {
        return Alarm.TaskType.valueOf(value)
    }
} 