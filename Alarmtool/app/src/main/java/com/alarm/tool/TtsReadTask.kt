package com.alarm.tool

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.random.Random
import com.alarm.tool.dpToPx
import android.util.TypedValue
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log

class TtsReadTask : BaseTask, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToRead: String = ""
    private var taskCompleted: Boolean = false
    private var readButton: Button? = null
    private var context: Context? = null
    private var isListening = false
    private var isTtsReady = false
    private var initRetryCount = 0
    private val maxInitRetries = 3

    // Example texts to read
    private val texts = listOf(
        "请朗读这段文字，以关闭闹钟。",
        "早安，世界，新的一天开始了。",
        "坚持就是胜利，不要放弃。",
        "知识就是力量，学习永无止境。"
    )

    companion object {
        private const val TAG = "TtsReadTask"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val INIT_RETRY_DELAY = 1000L // 1 second
    }

    override fun generateTask(context: Context): View {
        this.context = context
        
        // 检查并请求权限
        if (context is Activity) {
            checkAndRequestPermissions(context)
        }

        // 初始化 TTS
        initTts()

        // 初始化语音识别器
        val isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (isRecognitionAvailable) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        }

        textToRead = texts[Random.nextInt(texts.size)]

        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textView = TextView(context).apply {
            text = textToRead
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
            setPadding(16.dpToPx(context), 0, 16.dpToPx(context), 16.dpToPx(context))
        }

        readButton = Button(context).apply {
            text = "开始朗读"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 32.dpToPx(context)
            }
            setOnClickListener {
                if (!isListening) {
                    startListening()
                } else {
                    stopListening()
                }
            }
        }

        linearLayout.addView(textView)
        linearLayout.addView(readButton)

        return linearLayout
    }

    private fun checkAndRequestPermissions(activity: Activity) {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        val permissionsToRequest = permissions.filter { permission ->
            val permissionStatus = ContextCompat.checkSelfPermission(activity, permission)
            permissionStatus != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun initTts() {
        try {
            Log.d(TAG, "Initializing TTS engine... (attempt ${initRetryCount + 1})")
            if (tts == null) {
                tts = TextToSpeech(context, this).apply {
                    setOnUtteranceProgressListener(createUtteranceProgressListener())
                }
                Log.d(TAG, "TTS engine created")
            } else {
                Log.d(TAG, "TTS engine already exists")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS engine", e)
            handleTtsInitError(e)
        }
    }

    private fun handleTtsInitError(error: Exception) {
        val ctx = context
        if (ctx != null) {
            if (initRetryCount < maxInitRetries) {
                initRetryCount++
                Log.d(TAG, "Retrying TTS initialization in ${INIT_RETRY_DELAY}ms (attempt ${initRetryCount})")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    initTts()
                }, INIT_RETRY_DELAY)
            } else {
                Toast.makeText(ctx, "语音合成引擎初始化失败，请检查系统TTS设置", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createUtteranceProgressListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS finished: $utteranceId")
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error: $utteranceId")
                val ctx = context
                if (ctx != null) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(ctx, "语音播放出错，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startListening() {
        val ctx = context
        if (ctx != null) {
            val hasPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                try {
                    isListening = true
                    readButton?.text = "正在听..."
                    speechRecognizer?.startListening(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting speech recognition", e)
                    Toast.makeText(ctx, "语音识别启动失败，请重试", Toast.LENGTH_SHORT).show()
                    stopListening()
                }
            } else {
                Toast.makeText(ctx, "需要录音权限才能使用语音识别功能", Toast.LENGTH_SHORT).show()
                if (ctx is Activity) {
                    checkAndRequestPermissions(ctx)
                }
            }
        }
    }

    private fun stopListening() {
        isListening = false
        readButton?.text = "开始朗读"
        speechRecognizer?.stopListening()
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 可以用来显示音量变化
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d(TAG, "Buffer received")
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
                stopListening()
            }

            override fun onError(error: Int) {
                Log.e(TAG, "Error in speech recognition: $error")
                stopListening()
                val ctx = context
                if (ctx != null) {
                    Toast.makeText(ctx, "语音识别出错，请重试", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0)

                Log.d(TAG, "Speech recognition result: $spokenText")
                Log.d(TAG, "Expected text: $textToRead")

                val isCorrect = spokenText != null && textMatches(spokenText, textToRead)
                val ctx = context
                if (isCorrect) {
                    taskCompleted = true
                    if (ctx != null) {
                        Toast.makeText(ctx, "朗读正确！", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (ctx != null) {
                        Toast.makeText(ctx, "朗读不正确，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                Log.d(TAG, "Partial results")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "Speech recognition event: $eventType")
            }
        }
    }

    private fun textMatches(spoken: String, expected: String): Boolean {
        // 移除标点符号和空格，进行简单的文本匹配
        val normalizedSpoken = spoken.replace(Regex("[\\p{P}\\s]"), "")
        val normalizedExpected = expected.replace(Regex("[\\p{P}\\s]"), "")
        return normalizedSpoken == normalizedExpected
    }

    override fun verifyTask(): Boolean {
        return taskCompleted
    }

    override fun getTaskType(): Alarm.TaskType {
        return Alarm.TaskType.TTS_READ
    }

    override fun restoreState(bundle: Bundle?) {
        bundle?.let {
            textToRead = it.getString("tts_text", texts[0])
            taskCompleted = it.getBoolean("task_completed", false)
            readButton?.isEnabled = !taskCompleted
        }
    }

    override fun saveState(bundle: Bundle) {
        bundle.putString("tts_text", textToRead)
        bundle.putBoolean("task_completed", taskCompleted)
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "TTS onInit called with status: $status")
        val ctx = context
        if (status == TextToSpeech.SUCCESS) {
            try {
                // 检查并设置语言
                setupTtsLanguage()
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS initialization", e)
                handleTtsInitError(e)
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            handleTtsInitializationFailure(status)
        }
    }

    private fun setupTtsLanguage() {
        val ctx = context ?: return
        val availableLanguages = tts?.availableLanguages
        Log.d(TAG, "Available TTS languages: $availableLanguages")

        // 尝试设置中文
        var languageResult = tts?.setLanguage(Locale.CHINESE)
        Log.d(TAG, "Chinese language result: $languageResult")

        // 如果中文不可用，尝试设置中文变体
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val chineseVariants = listOf(
                Locale.SIMPLIFIED_CHINESE,
                Locale.TRADITIONAL_CHINESE,
                Locale("zh", "CN"),
                Locale("zh", "TW")
            )

            for (variant in chineseVariants) {
                languageResult = tts?.setLanguage(variant)
                Log.d(TAG, "Trying language variant: $variant, result: $languageResult")
                if (languageResult == TextToSpeech.SUCCESS) {
                    break
                }
            }
        }

        // 如果所有中文变体都不可用，使用系统默认语言
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Chinese language not available, using system default")
            languageResult = tts?.setLanguage(Locale.getDefault())
            Toast.makeText(ctx, "未找到中文语音包，将使用系统默认语音", Toast.LENGTH_LONG).show()
        }

        if (languageResult == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TTS language setup successful")
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.8f)  // 降低语速以提高清晰度
            isTtsReady = true
            playIntroSound()
        } else {
            Log.e(TAG, "Failed to set any language")
            Toast.makeText(ctx, "语音合成初始化失败：无法设置语言", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleTtsInitializationFailure(status: Int) {
        val ctx = context
        if (ctx != null) {
            val errorMsg = when (status) {
                TextToSpeech.ERROR -> "初始化错误"
                TextToSpeech.ERROR_SYNTHESIS -> "合成错误"
                TextToSpeech.ERROR_SERVICE -> "服务错误"
                TextToSpeech.ERROR_OUTPUT -> "输出错误"
                TextToSpeech.ERROR_NETWORK -> "网络错误"
                TextToSpeech.ERROR_NETWORK_TIMEOUT -> "网络超时"
                TextToSpeech.ERROR_INVALID_REQUEST -> "无效请求"
                else -> "未知错误 (代码: $status)"
            }
            
            if (initRetryCount < maxInitRetries) {
                initRetryCount++
                Log.d(TAG, "Retrying TTS initialization due to $errorMsg (attempt ${initRetryCount})")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    initTts()
                }, INIT_RETRY_DELAY)
            } else {
                Toast.makeText(ctx, "语音合成初始化失败: $errorMsg\n请检查系统TTS设置", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun playIntroSound() {
        try {
            if (tts == null || !isTtsReady) {
                Log.e(TAG, "Cannot play intro sound: TTS not ready")
                return
            }

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putFloat(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC.toFloat())
            }
            
            val result = tts?.speak("请朗读以下文字", TextToSpeech.QUEUE_FLUSH, params, "intro")
            Log.d(TAG, "TTS speak result: $result")
            
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "Error playing intro sound")
                val ctx = context
                if (ctx != null) {
                    Toast.makeText(ctx, "播放提示音失败，请检查系统音量", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing intro sound", e)
            val ctx = context
            if (ctx != null) {
                Toast.makeText(ctx, "播放提示音失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shutdownTts() {
        try {
            isTtsReady = false
            tts?.stop()
            tts?.shutdown()
            tts = null

            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
} 