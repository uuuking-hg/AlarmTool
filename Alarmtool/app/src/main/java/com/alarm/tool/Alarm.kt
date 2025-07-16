package com.alarm.tool

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "alarms")
@TypeConverters(Converters::class)
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val daysOfWeek: List<Int> = emptyList(), // 0-6 for Sunday-Saturday
    val taskType: TaskType = TaskType.MATH,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val name: String = "Alarm"
) {
    enum class TaskType {
        MATH,
        SHAKE,
        TTS_READ
        // TODO: Add more task types here
    }

    fun getFormattedTime(): String {
        val h = if (hour < 10) "0$hour" else "$hour"
        val m = if (minute < 10) "0$minute" else "$minute"
        return "$h:$m"
    }

    fun getRepeatDaysString(): String {
        if (daysOfWeek.isEmpty()) return "Once"
        if (daysOfWeek.size == 7) return "Everyday"

        val sortedDays = daysOfWeek.sorted()
        val daysMap = mapOf(
            0 to "Sun",
            1 to "Mon",
            2 to "Tue",
            3 to "Wed",
            4 to "Thu",
            5 to "Fri",
            6 to "Sat"
        )
        return sortedDays.joinToString(", ") { daysMap[it] ?: "" }
    }
} 