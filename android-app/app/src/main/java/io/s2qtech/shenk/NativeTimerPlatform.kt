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

class TimerCuePlayer(context: Context) : TextToSpeech.OnInitListener {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setWillPauseWhenDucked(false)
        .build()
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts.language = Locale.SIMPLIFIED_CHINESE
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
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { audioManager.abandonAudioFocusRequest(audioFocusRequest) }
        })
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        audioManager.requestAudioFocus(audioFocusRequest)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun close() {
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
