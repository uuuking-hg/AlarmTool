package com.alarm.tool

object TaskFactory {
    fun createTask(taskType: Alarm.TaskType): BaseTask {
        return when (taskType) {
            Alarm.TaskType.MATH -> MathTask()
            Alarm.TaskType.SHAKE -> ShakeTask()
            Alarm.TaskType.TTS_READ -> TtsReadTask()
            // Add more task types here as they are implemented
        }
    }
} 