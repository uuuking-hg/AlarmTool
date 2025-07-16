package com.alarm.tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    private val TAG = "AlarmReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        val alarmId = intent.getIntExtra("alarm_id", -1)
        val alarmName = intent.getStringExtra("alarm_name") ?: "Unknown Alarm"

        val alarmDao = AlarmDatabase.getDatabase(context).alarmDao()
        val alarmScheduler = AlarmScheduler(context)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Device rebooted. Rescheduling alarms...")
                CoroutineScope(Dispatchers.IO).launch {
                    alarmDao.getAllAlarms().collect {
                        it.forEach { alarm ->
                            if (alarm.isEnabled) {
                                alarmScheduler.scheduleAlarm(alarm)
                                Log.d(TAG, "Rescheduled alarm: ${alarm.name} at ${alarm.getFormattedTime()}")
                            }
                        }
                    }
                }
            }
            "com.alarm.tool.ALARM_TRIGGER" -> {
                Log.d(TAG, "Alarm triggered! ID: $alarmId, Name: $alarmName")
                CoroutineScope(Dispatchers.IO).launch {
                    val alarm = alarmDao.getAlarmById(alarmId)
                    if (alarm != null) {
                        // Start AlarmService to play sound and vibrate
                        val serviceIntent = Intent(context, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_START_ALARM
                            putExtra(AlarmService.EXTRA_ALARM_ID, alarm.id)
                            putExtra(AlarmService.EXTRA_RINGTONE_URI, alarm.ringtoneUri)
                            putExtra(AlarmService.EXTRA_VIBRATE, alarm.vibrate)
                        }
                        context.startForegroundService(serviceIntent) // Use startForegroundService for Android O+

                        // Start the AlarmTaskActivity
                        val alarmTaskIntent = Intent(context, AlarmTaskActivity::class.java).apply {
                            putExtra("alarm_id", alarm.id)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(alarmTaskIntent)

                        // If it's a repeating alarm, schedule the next occurrence
                        if (alarm.daysOfWeek.isNotEmpty()) {
                            val calendar = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, alarm.hour)
                                set(Calendar.MINUTE, alarm.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            // 找到下一个活跃的日期
                            while (true) {
                                val currentDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0-6 (Mon-Sun)
                                if (alarm.daysOfWeek.contains(currentDayOfWeek)) {
                                    // 如果今天是活跃日且时间已过，则设置为下周的同一天
                                    if (System.currentTimeMillis() > calendar.timeInMillis) {
                                        calendar.add(Calendar.WEEK_OF_YEAR, 1)
                                    }
                                    break // 找到下一个有效的日期和时间
                                }
                                calendar.add(Calendar.DAY_OF_YEAR, 1) // 移至下一天
                            }

                            alarmScheduler.scheduleAlarm(alarm, calendar.timeInMillis)
                            Log.d(TAG, "Rescheduled next occurrence for alarm: ${alarm.name} on ${calendar.time}")
                        } else {
                            // For single alarms, disable it after it fires
                            alarmDao.updateAlarm(alarm.copy(isEnabled = false))
                            Log.d(TAG, "Disabled single alarm: ${alarm.name}")
                        }
                    } else {
                        Log.e(TAG, "Alarm with ID $alarmId not found in database.")
                    }
                }
            }
        }
    }
} 