package com.alarm.tool

import android.content.Context
import android.os.Bundle
import android.view.View

interface BaseTask {
    /**
     * Generates the task content (e.g., math problem, text to read) and any UI elements needed for it.
     * @param context The context to create views or access resources.
     * @return A View representing the task's interactive elements.
     */
    fun generateTask(context: Context): View

    /**
     * Verifies the user's completion of the task.
     * @return True if the task is completed successfully, false otherwise.
     */
    fun verifyTask(): Boolean

    /**
     * Returns a unique string identifier for the task type.
     */
    fun getTaskType(): Alarm.TaskType

    /**
     * Optional: Called when the task activity is created, to restore state if needed.
     */
    fun restoreState(bundle: Bundle?)

    /**
     * Optional: Called to save the task's current state (e.g., current math problem).
     */
    fun saveState(bundle: Bundle)
} 