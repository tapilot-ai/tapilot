package lifeos.md.tapilot.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import lifeos.md.tapilot.*
import lifeos.md.tapilot.bridge.ModelCredentialStore

/** Visible entry legally starts the microphone, then returns to the original app. */
class VoiceEntryActivity : Activity() {
    private val runtime get() = (application as TapilotApplication).assistant
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var requested = false
    private var opened = false
    private var capturing = false
    private var started = 0L
    private val connect = object : Runnable {
        override fun run() {
            if (isFinishing || !resumed) return
            if (!runtime.floatingVoice || (intent.getBooleanExtra("heldVoice", false) && intent.getIntExtra("voiceEntryGeneration", -1) != runtime.floatingEntryGeneration)) { opened = true; finish(); return }
            if (!runtime.state.ready) {
                if (SystemClock.elapsedRealtime() - started > 5_000) failEntry("语音启动失败，请重试")
                else handler.postDelayed(this, 16)
                return
            }
            if (!requested) {
                requested = true
                val key = runCatching { ModelCredentialStore(this@VoiceEntryActivity).getAll()["speech-${runtime.state.speechProvider}"] }.getOrNull()
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requested = false
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7103)
                    return
                }
                if (key.isNullOrBlank() || !runtime.state.configured) { failEntry("请先在设置中配置模型和转写服务"); return }
                runtime.prepareVoiceEntry()
                if (runtime.voiceState.active) {
                    when (runtime.voiceState.phase) {
                        VoicePhase.Listening, VoicePhase.Preparing -> Unit
                        VoicePhase.Paused, VoicePhase.Error -> runtime.resumeVoice()
                        else -> runtime.interruptVoice()
                    }
                } else runtime.startVoice(key)
            }
            if ((runtime.voiceState.active && runtime.voiceState.phase != VoicePhase.Preparing) || (!runtime.startingVoice && runtime.outputError.isNotBlank())) {
                if (runtime.voiceState.phase == VoicePhase.Listening && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                    android.util.Log.d("TapilotVoice", "Microphone ready for floating voice")
                if (runtime.voiceState.active && runtime.voiceState.phase != VoicePhase.Error && intent.getBooleanExtra("heldVoice", false)) {
                    // Keep the original overlay's touch stream alive. Capture follows release.
                    if (runtime.floatingContextReady) runtime.completeFloatingContext()
                    opened = true; finish()
                } else if (runtime.voiceState.phase == VoicePhase.Listening) captureContextAndReturn() else failEntry(runtime.voiceState.error.ifBlank { runtime.outputError })
            } else handler.postDelayed(this, 16)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("heldVoice", false) && (!runtime.floatingVoice ||
            intent.getIntExtra("voiceEntryGeneration", -1) != runtime.floatingEntryGeneration)) {
            opened = true; finish(); return
        }
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        setContentView(TextView(this).apply {
            text = if (AppLanguage.resolved(this@VoiceEntryActivity) == "zh") "正在开启录音…" else "Starting microphone…"
            textSize = 18f; gravity = Gravity.CENTER
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setTextColor(AppTheme.colors(this@VoiceEntryActivity).contrast)
            setBackgroundColor(AppTheme.color(this@VoiceEntryActivity, R.color.brand_primary))
        })
        runtime.floatingVoice = true
        runtime.floatingVoiceError = ""
        if (!runtime.coordinator.ready) runtime.coordinator.initialize()
    }
    override fun onResume() {
        super.onResume()
        resumed = true
        if (started == 0L) started = SystemClock.elapsedRealtime()
        handler.removeCallbacks(connect); handler.post(connect)
    }
    override fun onPause() {
        resumed = false
        handler.removeCallbacks(connect)
        super.onPause()
    }
    private fun captureContextAndReturn() {
        if (capturing || opened) return
        capturing = true
        val service = lifeos.md.tapilot.accessibility.MidsceneAccessibilityService.current()
        if (service == null) { completeEntry(); return }
        // Recording continues while the original screen is captured. No chat page is opened.
        // Hide only this entry's UI so the attachment contains the original app.
        window.decorView.visibility = android.view.View.INVISIBLE
        handler.postDelayed({ if (!opened && !isFinishing) completeEntry() }, 2_200)
        service.capture("voice-entry-${java.util.UUID.randomUUID()}") { result ->
            handler.post {
                if (opened || isFinishing) return@post
                result.getOrNull()?.let { capture ->
                    LaunchContext.put(org.json.JSONObject().put("kind", "screen").put("label", "当前屏幕")
                        .put("dataUrl", capture.dataUrl).put("fromPanel", true).put("voice", true))
                }
                completeEntry()
            }
        }
    }
    private fun completeEntry() {
        if (opened) return
        opened = true
        runtime.coordinator.receiveLaunchContext()
        runtime.finishVoiceEntry()
        finish()
    }
    private fun failEntry(message: String) {
        runtime.endVoice()
        runtime.floatingVoice = true
        runtime.floatingVoiceError = message.ifBlank { "语音启动失败，请重试" }
        opened = true
        finish()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7103) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) handler.post(connect)
            else failEntry("需要麦克风权限才能语音对话")
        }
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (!opened && requested) runtime.endVoice()
        super.onDestroy()
    }
}
