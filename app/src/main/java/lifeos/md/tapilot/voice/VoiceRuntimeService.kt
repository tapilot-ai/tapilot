package lifeos.md.tapilot.voice

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import lifeos.md.tapilot.MainActivity
import lifeos.md.tapilot.TapilotApplication

/** Started by a visible chat screen or transparent voice entry; permission is requested in the UI. */
class VoiceRuntimeService : Service() {
    private val runtime get() = (application as TapilotApplication).assistant
    private var microphone = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var keepAlive = false
    private val endWhenIdle = Runnable {
        if (!keepAlive && !runtime.startingVoice) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "语音对话", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { runtime.stopAudio(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        if (intent?.action == START_VOICE) microphone = true
        startForeground(ID, notification(runtime.voiceState), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
            if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        runtime.serviceStarted(this, intent?.action == START_VOICE)
        when (intent?.action) { PAUSE -> runtime.pauseVoice(); RESUME -> runtime.resumeVoice(); INTERRUPT -> runtime.interruptVoice() }
        return START_NOT_STICKY
    }
    fun refresh(state: VoiceState, playing: Boolean, waiting: Boolean) {
        keepAlive = state.active || playing || waiting || runtime.startingVoice
        handler.removeCallbacks(endWhenIdle)
        if (!keepAlive) { handler.postDelayed(endWhenIdle, 300); return }
        getSystemService(NotificationManager::class.java).notify(ID, notification(state))
    }
    private fun notification(state: VoiceState): Notification {
        val zh = runtime.state.chinese
        fun label(a: String, b: String) = if (zh) a else b
        val text = when (state.phase) {
            VoicePhase.Listening -> label("我在听 · 在其他应用也可以继续说", "Listening · Keep talking in other apps")
            VoicePhase.Speaking -> label("正在回答", "Speaking")
            VoicePhase.Paused -> label("语音已暂停", "Voice paused")
            VoicePhase.Error -> label("语音出错 · 点击返回查看", "Voice error · Tap to review")
            else -> label("已收到，正在处理", "Received, working on it")
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String, title: String, code: Int) = Notification.Action.Builder(null, title,
            PendingIntent.getService(this, code, Intent(this, VoiceRuntimeService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(label("Tapilot 语音", "Tapilot Voice")).setContentText(text).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).apply {
                if (state.active) {
                    if (state.phase in listOf(VoicePhase.Paused, VoicePhase.Error)) addAction(action(RESUME, label("继续", "Resume"), 1))
                    else if (state.phase in listOf(VoicePhase.Speaking, VoicePhase.Thinking)) addAction(action(INTERRUPT, label("打断", "Interrupt"), 2))
                    else addAction(action(PAUSE, label("暂停", "Pause"), 3))
                }
                addAction(action(STOP, label("结束语音", "End voice"), 4))
            }.build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); runtime.serviceDestroyed(this); super.onDestroy() }
    companion object {
        const val START_VOICE = "lifeos.md.tapilot.voice.START"
        private const val STOP = "lifeos.md.tapilot.voice.STOP"
        private const val PAUSE = "lifeos.md.tapilot.voice.PAUSE"
        private const val RESUME = "lifeos.md.tapilot.voice.RESUME"
        private const val INTERRUPT = "lifeos.md.tapilot.voice.INTERRUPT"
        private const val CHANNEL = "tapilot_voice"
        private const val ID = 2115
    }
}
