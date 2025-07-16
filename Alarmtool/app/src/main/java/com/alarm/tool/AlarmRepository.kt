package com.alarm.tool

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmRepository(private val alarmDao: AlarmDao) {
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val TAG = "AlarmRepository"

    suspend fun insert(alarm: Alarm): Long {
        return alarmDao.insertAlarm(alarm)
    }

    suspend fun update(alarm: Alarm) {
        alarmDao.updateAlarm(alarm)
    }

    suspend fun delete(alarm: Alarm) {
        alarmDao.deleteAlarm(alarm)
    }

    suspend fun getAlarmById(id: Int): Alarm? {
        return alarmDao.getAlarmById(id)
    }

    suspend fun exportAlarms(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val alarms = alarmDao.getAllAlarmsSync()
            val backupData = BackupData(
                version = 1,
                timestamp = System.currentTimeMillis(),
                alarms = alarms
            )
            val json = gson.toJson(backupData)
            file.writeText(json)
            Log.d(TAG, "Alarms exported successfully to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting alarms", e)
            false
        }
    }

    suspend fun importAlarms(file: File): ImportResult = withContext(Dispatchers.IO) {
        try {
            val json = file.readText()
            val type = object : TypeToken<BackupData>() {}.type
            val backupData = gson.fromJson<BackupData>(json, type)

            if (backupData.version > 1) {
                return@withContext ImportResult.Error("不支持的备份版本")
            }

            // 验证数据
            if (!validateBackupData(backupData)) {
                return@withContext ImportResult.Error("备份数据格式无效")
            }

            // 导入闹钟
            backupData.alarms.forEach { alarm ->
                try {
                    alarmDao.insertAlarm(alarm)
                } catch (e: Exception) {
                    Log.e(TAG, "Error importing alarm: $alarm", e)
                }
            }

            ImportResult.Success(backupData.alarms.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing alarms", e)
            ImportResult.Error(e.message ?: "导入失败")
        }
    }

    private fun validateBackupData(backupData: BackupData): Boolean {
        if (backupData.alarms.isEmpty()) return false
        
        backupData.alarms.forEach { alarm ->
            // 验证基本字段
            if (alarm.hour !in 0..23 || alarm.minute !in 0..59) return false
            
            // 验证重复日期
            alarm.daysOfWeek.forEach { day ->
                if (day !in 0..6) return false
            }
            
            // 验证任务类型
            try {
                Alarm.TaskType.valueOf(alarm.taskType.name)
            } catch (e: IllegalArgumentException) {
                return false
            }
        }
        
        return true
    }

    suspend fun createBackup(baseDir: File): BackupResult = withContext(Dispatchers.IO) {
        try {
            // 创建备份目录
            val backupDir = File(baseDir, "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // 生成备份文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "alarm_backup_$timestamp.json")

            // 导出数据
            if (exportAlarms(backupFile)) {
                // 清理旧备份
                cleanOldBackups(backupDir)
                BackupResult.Success(backupFile.absolutePath)
            } else {
                BackupResult.Error("备份创建失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating backup", e)
            BackupResult.Error(e.message ?: "备份失败")
        }
    }

    private fun cleanOldBackups(backupDir: File) {
        try {
            val backupFiles = backupDir.listFiles { file ->
                file.name.startsWith("alarm_backup_") && file.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() }

            // 保留最近5个备份
            backupFiles?.drop(5)?.forEach { file ->
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old backups", e)
        }
    }
}

data class BackupData(
    val version: Int,
    val timestamp: Long,
    val alarms: List<Alarm>
)

sealed class ImportResult {
    data class Success(val count: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

sealed class BackupResult {
    data class Success(val filePath: String) : BackupResult()
    data class Error(val message: String) : BackupResult()
} 