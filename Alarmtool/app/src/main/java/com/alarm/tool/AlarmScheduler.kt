package com.alarm.tool

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val TAG = "AlarmScheduler"
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(alarm: Alarm, triggerAtMillis: Long) {
        Log.d(TAG, "Scheduling alarm ${alarm.id} for ${alarm.getFormattedTime()} at $triggerAtMillis")
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.alarm.tool.ALARM_TRIGGER"
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_name", alarm.name)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or 
                   (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            flags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent),
                        pendingIntent
                    )
                    Log.d(TAG, "Alarm scheduled with setAlarmClock")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "Alarm scheduled with setAndAllowWhileIdle")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Alarm scheduled with setExactAndAllowWhileIdle")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Alarm scheduled with setExact")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }

    fun scheduleAlarm(alarm: Alarm) {
        Log.d(TAG, "Scheduling alarm: $alarm")
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()
        
        if (alarm.daysOfWeek.isEmpty()) {
            // 单次闹钟
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            Log.d(TAG, "Single alarm scheduled for: ${calendar.time}")
        } else {
            // 重复闹钟：找到下一个活跃日期
            var daysChecked = 0
            val today = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 转换为 0-6 (周日-周六)
            
            while (daysChecked < 7) {
                val checkDay = (today + daysChecked) % 7
                if (alarm.daysOfWeek.contains(checkDay)) {
                    if (daysChecked == 0 && calendar.timeInMillis <= now) {
                        // 今天是目标日期但时间已过
                        calendar.add(Calendar.WEEK_OF_YEAR, 1)
                    } else {
                        calendar.add(Calendar.DAY_OF_YEAR, daysChecked)
                    }
                    Log.d(TAG, "Repeating alarm scheduled for: ${calendar.time}")
                    break
                }
                daysChecked++
            }
        }

        scheduleAlarm(alarm, calendar.timeInMillis)
    }

    fun cancelAlarm(alarm: Alarm) {
        Log.d(TAG, "Cancelling alarm: ${alarm.id}")
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.alarm.tool.ALARM_TRIGGER"
            putExtra("alarm_id", alarm.id)
        }
        
        val flags = PendingIntent.FLAG_NO_CREATE or 
                   (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)
                   
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            flags
        )
        
        pendingIntent?.let {
            try {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(TAG, "Alarm cancelled successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel alarm", e)
            }
        }
    }
} 