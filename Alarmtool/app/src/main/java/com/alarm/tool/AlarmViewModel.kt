package com.alarm.tool

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AlarmRepository
    val allAlarms: LiveData<List<Alarm>>

    init {
        val alarmDao = AlarmDatabase.getDatabase(application).alarmDao()
        repository = AlarmRepository(alarmDao)
        allAlarms = repository.allAlarms.asLiveData(viewModelScope.coroutineContext + Dispatchers.IO)
    }

    suspend fun insert(alarm: Alarm): Long {
        return repository.insert(alarm)
    }

    fun update(alarm: Alarm) = viewModelScope.launch(Dispatchers.IO) {
        repository.update(alarm)
    }

    fun delete(alarm: Alarm) = viewModelScope.launch(Dispatchers.IO) {
        repository.delete(alarm)
    }

    suspend fun getAlarmById(id: Int): Alarm? {
        return repository.getAlarmById(id)
    }
} 