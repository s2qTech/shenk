package io.s2qtech.shenk

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID

class NativeTimerForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "训练计时", NotificationManager.IMPORTANCE_LOW).apply {
                description = "训练进行时保持计时可靠"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("训练计时进行中")
            .setContentText("返回身刻查看当前动作")
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "shenk-native-timer"
        private const val NOTIFICATION_ID = 201

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NativeTimerForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NativeTimerForegroundService::class.java))
        }
    }
}

class TimerCuePlayer(
    context: Context,
    private val onStatus: (String?) -> Unit = {},
) : TextToSpeech.OnInitListener {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setWillPauseWhenDucked(false)
        .build()
    private val pendingSpeech = ArrayDeque<String>()
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            onStatus("语音服务初始化失败，请检查系统文字转语音设置。")
            return
        }
        val locale = listOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINESE).firstOrNull {
            tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
        }
        if (locale == null) {
            onStatus("当前语音服务缺少中文语音，请在系统中安装中文语音包。")
            return
        }
        tts.language = locale
        tts.setAudioAttributes(audioAttributes)
        val preferred = tts.voices
            ?.filter { it.locale.language == Locale.CHINESE.language }
            ?.sortedByDescending { voice ->
                val name = voice.name.lowercase()
                name.contains("female") || name.contains("woman") || name.contains("女")
            }
            ?.firstOrNull()
        preferred?.let { tts.voice = it }
        tts.setPitch(1.03f)
        tts.setSpeechRate(0.96f)
        ready = true
        onStatus(null)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
        })
        val queued = synchronized(pendingSpeech) {
            pendingSpeech.removeLastOrNull().also { pendingSpeech.clear() }
        }
        queued?.let(::speakNow)
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            if (text.any { !it.isDigit() }) {
                synchronized(pendingSpeech) {
                    pendingSpeech.clear()
                    pendingSpeech.addLast(text)
                }
            }
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        audioManager.requestAudioFocus(audioFocusRequest)
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) == TextToSpeech.ERROR) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
            onStatus("语音提示播放失败，请检查媒体音量和文字转语音设置。")
        }
    }

    fun close() {
        synchronized(pendingSpeech) { pendingSpeech.clear() }
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        tts.stop()
        tts.shutdown()
    }
}

class TimerCallMonitor(
    private val context: Context,
    private val onCall: () -> Unit,
) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private var registered = false
    private val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                onCall()
            }
        }
    }

    fun registerIfPermitted(): Boolean {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        if (!registered) {
            telephony.registerTelephonyCallback(context.mainExecutor, callback)
            registered = true
        }
        return true
    }

    fun close() {
        if (registered) telephony.unregisterTelephonyCallback(callback)
        registered = false
    }
}
