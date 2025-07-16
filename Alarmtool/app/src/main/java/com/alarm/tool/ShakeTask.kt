package com.alarm.tool

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sqrt
import android.util.TypedValue
import com.alarm.tool.dpToPx

class ShakeTask : BaseTask, SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var shakeCount: Int = 0
    private val targetShakeCount: Int = 10 // Customizable target
    private var lastShakeTime: Long = 0
    private var shakeTextView: TextView? = null
    private var taskCompleted: Boolean = false

    // For shake detection
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private val SHAKE_THRESHOLD_GRAVITY = 2.7f // Adjust as needed
    private val SHAKE_SLOP_TIME_MS = 500 // Time between shakes

    override fun generateTask(context: Context): View {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        shakeTextView = TextView(context).apply {
            text = "Shake your phone: $shakeCount / $targetShakeCount"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f) // Adjust text size as needed
            gravity = Gravity.CENTER
        }
        linearLayout.addView(shakeTextView)

        return linearLayout
    }

    override fun verifyTask(): Boolean {
        return taskCompleted
    }

    override fun getTaskType(): Alarm.TaskType {
        return Alarm.TaskType.SHAKE
    }

    override fun restoreState(bundle: Bundle?) {
        bundle?.let {
            shakeCount = it.getInt("shake_count", 0)
            taskCompleted = it.getBoolean("task_completed", false)
            shakeTextView?.text = "Shake your phone: $shakeCount / $targetShakeCount"
        }
    }

    override fun saveState(bundle: Bundle) {
        bundle.putInt("shake_count", shakeCount)
        bundle.putBoolean("task_completed", taskCompleted)
    }

    fun unregisterSensorListener() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastShakeTime) > SHAKE_SLOP_TIME_MS) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val gX = x / SensorManager.GRAVITY_EARTH
                val gY = y / SensorManager.GRAVITY_EARTH
                val gZ = z / SensorManager.GRAVITY_EARTH

                // gForce will be close to 1 when there is no movement.
                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                    val diffX = x - lastX
                    val diffY = y - lastY
                    val diffZ = z - lastZ

                    if (diffX * diffX + diffY * diffY + diffZ * diffZ > 0.5f) { // Check for significant change
                        shakeCount++
                        shakeTextView?.text = "Shake your phone: $shakeCount / $targetShakeCount"
                        lastShakeTime = currentTime

                        if (shakeCount >= targetShakeCount) {
                            taskCompleted = true
                            unregisterSensorListener()
                        }
                    }
                }

                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used for this task
    }
} 