package com.alarm.tool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.PowerManager
import android.os.PowerManager.WakeLock

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var originalAudioMode: Int = AudioManager.MODE_NORMAL
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: WakeLock? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                ensureMaxVolume()
            }
        }
    }

    companion object {
        private const val TAG = "AlarmService"
        const val CHANNEL_ID = "AlarmServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_ALARM = "com.alarm.tool.START_ALARM"
        const val ACTION_STOP_ALARM = "com.alarm.tool.STOP_ALARM"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val EXTRA_VIBRATE = "vibrate"
        private const val WAKELOCK_TIMEOUT = 10 * 60 * 1000L // 10 minutes
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 500) // 震动模式：0ms延迟，500ms开，500ms关
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService onCreate")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        registerVolumeReceiver()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_ALARM -> {
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
                
                val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
                val ringtoneUri = intent.getStringExtra(EXTRA_RINGTONE_URI)?.let { Uri.parse(it) }
                val shouldVibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)

                serviceScope.launch {
                    try {
                        startAlarm(alarmId, ringtoneUri, shouldVibrate)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting alarm", e)
                        stopSelf()
                    }
                }
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
                stopSelf()
            }
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "AlarmService onDestroy")
        stopAlarm()
        serviceJob.cancel()
        unregisterVolumeReceiver()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "闹钟服务"
            val descriptionText = "用于保持闹钟响铃的服务"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null) // 不使用通知声音
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, AlarmTaskActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("闹钟正在响铃")
            .setContentText("点击此处完成任务并关闭闹钟")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setWillPauseWhenDucked(true)
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
    }

    private suspend fun startAlarm(alarmId: Int, ringtoneUri: Uri?, shouldVibrate: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                // 保存并修改音频模式
                originalAudioMode = audioManager.mode
                audioManager.mode = AudioManager.MODE_NORMAL

                if (!requestAudioFocus()) {
                    Log.e(TAG, "Failed to get audio focus")
                    return@withContext
                }

                ensureMaxVolume()
                playRingtone(ringtoneUri)
                if (shouldVibrate) {
                    startVibration()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting alarm", e)
            }
        }
    }

    private fun ensureMaxVolume() {
        try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    private fun playRingtone(ringtoneUri: Uri?) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                
                val uri = ringtoneUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setDataSource(applicationContext, uri)
                isLooping = true
                prepareAsync()
                
                setOnPreparedListener { player ->
                    try {
                        player.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting MediaPlayer", e)
                    }
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    // 如果播放失败，尝试使用默认铃声
                    if (ringtoneUri != null) {
                        playRingtone(null)
                    }
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaPlayer", e)
            // 如果设置失败，尝试使用默认铃声
            if (ringtoneUri != null) {
                playRingtone(null)
            }
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VIBRATION_PATTERN, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null

            vibrator?.cancel()
            vibrator = null

            abandonAudioFocus()
            audioManager.mode = originalAudioMode
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm", e)
        }
    }

    private fun registerVolumeReceiver() {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeReceiver, filter)
    }

    private fun unregisterVolumeReceiver() {
        try {
            unregisterReceiver(volumeReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering volume receiver", e)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AlarmTool::AlarmServiceWakeLock"
            )
            wakeLock?.acquire(WAKELOCK_TIMEOUT)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
} 