package com.alarm.tool

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import androidx.appcompat.app.AlertDialog
import android.app.ActivityManager
import android.content.pm.ActivityInfo
import android.view.WindowManager.LayoutParams.*
import android.os.PowerManager
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowInsets
import android.view.WindowInsets.Type

class AlarmTaskActivity : AppCompatActivity() {

    private var currentTask: BaseTask? = null
    private var alarmId: Int = -1
    private lateinit var dismissButton: Button
    private lateinit var taskContainer: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private var isTaskCompleted = false
    private var isActivityPaused = false
    private var exitAttempts = 0
    private val MAX_EXIT_ATTEMPTS = 3
    private val EXIT_ATTEMPT_RESET_DELAY = 10000L // 10 seconds
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "AlarmTaskActivity"
        private const val TASK_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private const val TASK_CHECK_INTERVAL_MS = 500L
        private const val WAKELOCK_TIMEOUT = 10 * 60 * 1000L // 10 minutes
    }

    private val taskCheckRunnable = object : Runnable {
        override fun run() {
            if (!isTaskCompleted && !isActivityPaused) {
                currentTask?.let {
                    if (it.verifyTask()) {
                        isTaskCompleted = true
                        enableDismissButton()
                    } else {
                        disableDismissButton()
                        handler.postDelayed(this, TASK_CHECK_INTERVAL_MS)
                    }
                }
            }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    showOnLockScreen()
                }
            }
        }
    }

    private val exitAttemptResetRunnable = Runnable {
        exitAttempts = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "AlarmTaskActivity onCreate")

        setupWindowFlags()
        setContentView(R.layout.activity_alarm_task)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        dismissButton = findViewById(R.id.btn_dismiss_alarm)
        taskContainer = findViewById(R.id.task_container)

        alarmId = intent.getIntExtra("alarm_id", -1)
        if (alarmId != -1) {
            setupTask(savedInstanceState)
        } else {
            Log.e(TAG, "Invalid alarm ID")
            finish()
        }

        registerScreenOffReceiver()
        startTaskTimeout()
    }

    private fun setupWindowFlags() {
        // 获取 PowerManager.WakeLock
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "AlarmTool::AlarmWakeLock"
            )
            wakeLock?.acquire(WAKELOCK_TIMEOUT)
        }

        // 设置窗口标志
        window.apply {
            // 在锁屏界面上显示
            addFlags(FLAG_KEEP_SCREEN_ON or
                    FLAG_DISMISS_KEYGUARD or
                    FLAG_SHOW_WHEN_LOCKED or
                    FLAG_TURN_SCREEN_ON or
                    FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)

            // 设置窗口亮度为最大
            attributes = attributes.apply {
                screenBrightness = 1.0f
                buttonBrightness = 1.0f
            }

            // 禁用系统手势
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
        }

        // 如果设备处于锁屏状态，解锁屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }

    private fun setupTask(savedInstanceState: Bundle?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val alarmDao = AlarmDatabase.getDatabase(applicationContext).alarmDao()
                val alarm = alarmDao.getAlarmById(alarmId)
                
                if (alarm != null) {
                    runOnUiThread {
                        try {
                            currentTask = TaskFactory.createTask(alarm.taskType)
                            currentTask?.restoreState(savedInstanceState)
                            taskContainer.addView(currentTask?.generateTask(this@AlarmTaskActivity))
                            setupDismissButton()
                            handler.post(taskCheckRunnable)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting up task", e)
                            showErrorDialog()
                        }
                    }
                } else {
                    Log.e(TAG, "Alarm not found: $alarmId")
                    runOnUiThread { finish() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading alarm", e)
                runOnUiThread { showErrorDialog() }
            }
        }
    }

    private fun setupDismissButton() {
        dismissButton.setOnClickListener {
            if (isTaskCompleted) {
                stopAlarmAndFinish()
            } else {
                Toast.makeText(this, "请先完成任务！", Toast.LENGTH_SHORT).show()
            }
        }
        disableDismissButton()
    }

    private fun enableDismissButton() {
        dismissButton.isEnabled = true
        dismissButton.setBackgroundColor(ContextCompat.getColor(this, R.color.primaryColor))
    }

    private fun disableDismissButton() {
        dismissButton.isEnabled = false
        dismissButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
    }

    private fun startTaskTimeout() {
        handler.postDelayed({
            if (!isFinishing && !isTaskCompleted) {
                showTimeoutDialog()
            }
        }, TASK_TIMEOUT_MS)
    }

    private fun showTimeoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("任务超时")
            .setMessage("您已经尝试了5分钟，是否需要跳过任务？")
            .setPositiveButton("跳过任务") { _, _ ->
                isTaskCompleted = true
                stopAlarmAndFinish()
            }
            .setNegativeButton("继续尝试") { dialog, _ ->
                dialog.dismiss()
                startTaskTimeout() // 重新开始计时
            }
            .setCancelable(false)
            .show()
    }

    private fun showErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("错误")
            .setMessage("加载任务时出现错误，是否跳过任务？")
            .setPositiveButton("跳过任务") { _, _ ->
                stopAlarmAndFinish()
            }
            .setNegativeButton("重试") { dialog, _ ->
                dialog.dismiss()
                setupTask(null)
            }
            .setCancelable(false)
            .show()
    }

    private fun stopAlarmAndFinish() {
        stopAlarmAndReleaseResources()
        finish()
    }

    private fun stopAlarmAndReleaseResources() {
        handler.removeCallbacks(taskCheckRunnable)
        val stopServiceIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        stopService(stopServiceIntent)
        
        // 释放任务资源
        when (currentTask) {
            is ShakeTask -> (currentTask as ShakeTask).unregisterSensorListener()
            is TtsReadTask -> (currentTask as TtsReadTask).shutdownTts()
        }
        currentTask = null
    }

    private fun registerScreenOffReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)
    }

    private fun unregisterScreenOffReceiver() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering screen off receiver", e)
        }
    }

    private fun showOnLockScreen() {
        setupWindowFlags()  // Reapply window flags
        
        try {
            // Force activity to front
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_NO_USER_ACTION)
            
            // Ensure task is visible
            currentTask?.let { task ->
                taskContainer.removeAllViews()
                taskContainer.addView(task.generateTask(this))
                if (!isTaskCompleted) {
                    handler.post(taskCheckRunnable)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing on lock screen", e)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentTask?.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        isActivityPaused = false
        setupWindowFlags()  // Reapply window flags on resume
        showOnLockScreen()  // Ensure activity is visible
    }

    override fun onPause() {
        super.onPause()
        isActivityPaused = true
        handler.removeCallbacks(taskCheckRunnable)
        if (!isTaskCompleted) {
            // 如果任务未完成，立即返回到任务界面
            handler.postDelayed({
                val intent = Intent(this, AlarmTaskActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra("alarm_id", alarmId)
                startActivity(intent)
            }, 100)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmAndReleaseResources()
        unregisterScreenOffReceiver()
        // 释放 WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    override fun onBackPressed() {
        if (isTaskCompleted) {
            super.onBackPressed()
        } else {
            exitAttempts++
            if (exitAttempts >= MAX_EXIT_ATTEMPTS) {
                Toast.makeText(this, "请完成任务以关闭闹钟。重复尝试退出将无法关闭闹钟。", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "请先完成任务才能关闭闹钟！", Toast.LENGTH_SHORT).show()
            }
            handler.removeCallbacks(exitAttemptResetRunnable)
            handler.postDelayed(exitAttemptResetRunnable, EXIT_ATTEMPT_RESET_DELAY)
        }
    }

    override fun onUserLeaveHint() {
        if (!isTaskCompleted) {
            // 用户按下Home键，立即返回到任务界面
            val intent = Intent(this, AlarmTaskActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("alarm_id", alarmId)
            startActivity(intent)
        }
    }
} 