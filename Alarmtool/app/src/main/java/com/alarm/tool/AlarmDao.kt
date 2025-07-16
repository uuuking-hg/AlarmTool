package com.alarm.tool

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute ASC")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute ASC")
    suspend fun getAllAlarmsSync(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :alarmId")
    suspend fun getAlarmById(alarmId: Int): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)

    @Query("DELETE FROM alarms")
    suspend fun deleteAllAlarms()

    @Query("SELECT COUNT(*) FROM alarms")
    suspend fun getAlarmCount(): Int

    @Transaction
    suspend fun replaceAllAlarms(alarms: List<Alarm>) {
        deleteAllAlarms()
        alarms.forEach { insertAlarm(it) }
    }
} 