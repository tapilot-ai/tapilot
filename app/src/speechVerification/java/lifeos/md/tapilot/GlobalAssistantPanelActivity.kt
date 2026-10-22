package lifeos.md.tapilot

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import lifeos.md.tapilot.theme.TapilotTheme
import androidx.core.view.WindowCompat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import lifeos.md.tapilot.chat.RecordingFeedback
import lifeos.md.tapilot.speech.SpeechRecognition
import androidx.core.widget.doAfterTextChanged
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.CheckBox
import android.widget.ImageView
import android.graphics.BitmapFactory
import android.util.Base64
import lifeos.md.tapilot.chat.HoldToRecordEditor
import lifeos.md.tapilot.chat.RecordingGesture
import lifeos.md.tapilot.bridge.ModelCredentialStore
import org.json.JSONObject

/** Compact entry over the app the user was using. The conversation continues in MainActivity. */
open class GlobalAssistantPanelActivity : ComponentActivity() {
    private lateinit var transcriber: SpeechRecognition
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var recording by mutableStateOf(false)
    private var transcribing by mutableStateOf(false)
    private var sent = false
    private var cancelledOnRelease by mutableStateOf(false)
    private var sendOnRelease by mutableStateOf(false)
    private var voiceGeneration = 0
    private lateinit var launchContext: JSONObject
    private var includeScreen = true
    private var levels by mutableStateOf(List(24) { 0f })
    private var transcript by mutableStateOf("")
    private var seconds by mutableStateOf(0)
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private val clock = object : Runnable {
        override fun run() {
            if (!recording) return
            seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
            if (seconds >= 60) cancelVoice() else handler.postDelayed(this, 250)
        }
    }
    protected open fun createRecognition(): SpeechRecognition = lifeos.md.tapilot.speech.PolishingSpeechRecognition(this)
    protected open fun recognitionCredentials(): Pair<String, String?> {
        val provider = getSharedPreferences("tapilot-speech", 0).getString("provider", "volcengine") ?: "volcengine"
        return provider to ModelCredentialStore(this).getAll()["speech-$provider"]
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transcriber = createRecognition()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        launchContext = LaunchContext.consumePanel() ?: JSONObject().put("kind", "panel")
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply {
            gravity = Gravity.BOTTOM
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            dimAmount = 0.16f
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            requestFocus()
            setPadding(dp(18), dp(16), dp(18), dp(20))
            background = GradientDrawable().apply {
                setColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_surface else R.color.brand_surface))
                cornerRadii = floatArrayOf(dp(22f), dp(22f), dp(22f), dp(22f), 0f, 0f, 0f, 0f)
            }
        }
        root.addView(TextView(this).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            text = "问 Tapilot"
            textSize = 18f
            setTextColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_text else R.color.brand_text))
        })
        status = TextView(this).apply {
            text = "按住说话或输入，发送后继续处理"
            textSize = 12f
            setTextColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_muted else R.color.brand_muted))
        }
        root.addView(status)
        if (launchContext.has("dataUrl")) {
            val reference = CheckBox(this).apply {
                text = "引用当前屏幕"; isChecked = true; minHeight = dp(48)
                buttonTintList = android.content.res.ColorStateList.valueOf(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_primary else R.color.brand_primary))
                setTextColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_text else R.color.brand_text))
                setOnCheckedChangeListener { _, checked -> includeScreen = checked }
            }
            root.addView(reference)
            val preview = ImageView(this).apply {
                contentDescription = "唤起助手时的屏幕快照"
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(runCatching {
                    val bytes = Base64.decode(launchContext.getString("dataUrl").substringAfter(','), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })
                }.getOrNull())
            }
            root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(100)))
        } else status.text = "未引用屏幕 · 可直接提问"
        input = HoldToRecordEditor(this).apply {
            onPrepare = { transcriber.prepare() }
            onDiscardPreparation = { transcriber.cancelPreparation() }
            onHold = { startVoice() }
            onMove = { gesture ->
                cancelledOnRelease = gesture == RecordingGesture.Cancel
                sendOnRelease = gesture == RecordingGesture.Send
                if (recording) status.text = if (cancelledOnRelease) "松开取消" else if (sendOnRelease) "松开发送" else "松开转文字"
            }
            onRelease = { if (cancelledOnRelease) cancelVoice() else finishVoice(sendOnRelease) }
            onCancel = { cancelVoice() }
            typingHint = "输入消息"
            speechHint = "按住说话"
            refreshHint()
            doAfterTextChanged { refreshHint() }
            setTextColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_text else R.color.brand_text))
            setHintTextColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_muted else R.color.brand_muted))
            minLines = 1
            maxLines = 3
            setSingleLine(false)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    send()
                    true
                } else false
            }
        }
        input.background = GradientDrawable().apply {
            cornerRadius = dp(18f)
            setColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_input else R.color.brand_input))
            setStroke(dp(1), AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_strong_line else R.color.brand_strong_line))
        }
        input.setPadding(dp(16), dp(12), dp(16), dp(12))
        root.addView(input)
        val actions = LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, 0, dp(72), 0)
        }
        val voice = Button(this).apply {
            text = "按住说话"
            stateListAnimator = null
            setTextColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_primary else R.color.brand_primary))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f)
                setColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_soft else R.color.brand_soft))
            }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startVoice(); true }
                    MotionEvent.ACTION_UP -> { if (cancelledOnRelease) cancelVoice() else finishVoice(sendOnRelease); true }
                    MotionEvent.ACTION_MOVE -> {
                        val gesture = lifeos.md.tapilot.chat.recordingGesture(event.rawX, event.rawY, window.decorView.width.toFloat(),
                            window.decorView.height.toFloat(), resources.displayMetrics.density)
                        cancelledOnRelease = gesture == RecordingGesture.Cancel
                        sendOnRelease = gesture == RecordingGesture.Send
                        if (recording) status.text = if (cancelledOnRelease) "松开取消" else if (sendOnRelease) "松开发送" else "松开转文字"
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { cancelVoice(); true }
                    else -> true
                }
            }
        }
        actions.addView(voice)
        actions.addView(Button(this).apply {
            text = "发送"
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) }
            setTextColor(AppTheme.colors(this@GlobalAssistantPanelActivity).contrast)
            background = GradientDrawable().apply {
                setColor(AppTheme.color(this@GlobalAssistantPanelActivity, if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_primary else R.color.brand_primary))
                cornerRadius = dp(18f)
            }
            setOnClickListener { send() }
        })
        root.addView(actions)
        val container = FrameLayout(this).apply {
            setOnClickListener { finish() }
            val panelParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            )
            addView(root, panelParams)
            setOnApplyWindowInsetsListener { _, insets ->
                val keyboard = maxOf(insets.getInsets(WindowInsets.Type.ime()).bottom, insets.getInsets(WindowInsets.Type.systemBars()).bottom)
                if (panelParams.bottomMargin != keyboard) {
                    panelParams.bottomMargin = keyboard
                    root.layoutParams = panelParams
                }
                insets
            }
        }
        container.addView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TapilotTheme(AppTheme.isDark(this@GlobalAssistantPanelActivity)) {
                    RecordingFeedback(cancelledOnRelease, sendOnRelease,
                        if (transcribing) "正在转文字…" else if (cancelledOnRelease) "松手 取消"
                        else if (sendOnRelease) "松手 发送" else "松手 填入输入框",
                        "取消", "发送", levels, seconds, transcript, transcribing,
                        visible = recording || transcribing,
                        onCancel = ::cancelVoice, onSend = { finishVoice(true) })
                }
            }
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.setOnClickListener { }
        setContentView(container)
    }

    private fun startVoice() {
        if (recording || transcribing || sent) return
        val (provider, key) = recognitionCredentials()
        if (key.isNullOrBlank()) { status.text = "请先在 Tapilot 设置页填写语音 API Key"; return }
        val generation = ++voiceGeneration
        recording = true
        transcript = ""
        levels = List(24) { 0f }
        seconds = 0
        startedAt = SystemClock.elapsedRealtime()
        handler.post(clock)
        cancelledOnRelease = false
        sendOnRelease = false
        input.clearFocus()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        status.text = "正在录音 · 松开转文字 · 左侧取消 / 右侧发送"
        runCatching {
            transcriber.start(provider, key, "zh-CN",
                onLevel = { level -> runOnUiThread { if (generation == voiceGeneration && recording) levels = levels.drop(1) + level } },
                onTranscript = { text -> runOnUiThread { if (generation == voiceGeneration && (recording || transcribing)) transcript = text } },
                onError = { error -> runOnUiThread {
                if (generation == voiceGeneration) { cancelVoice(); status.text = error.message ?: "录音失败" }
            } }) { result -> runOnUiThread {
                if (generation == voiceGeneration) result.onFailure { cancelVoice(); status.text = it.message ?: "录音失败" }
            } }
        }.onFailure { cancelVoice(); status.text = it.message ?: "录音未启动" }
    }

    private fun finishVoice(sendOnRelease: Boolean) {
        if (!recording) return
        handler.removeCallbacks(clock)
        recording = false
        transcribing = true
        val generation = voiceGeneration
        status.text = "正在转写…"
        runCatching {
            transcriber.finish { result -> runOnUiThread {
                if (generation != voiceGeneration || isFinishing || isDestroyed) return@runOnUiThread
                transcribing = false
                result.onSuccess { text ->
                    input.setText(lifeos.md.tapilot.speech.appendSpeechTranscript(input.text.toString(), text))
                    status.text = "转写完成"
                    if (sendOnRelease) send() else {
                        input.setSelection(input.text.length)
                        input.requestFocus()
                        getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(input, 0)
                    }
                }.onFailure { transcribing = false; status.text = it.message ?: "转写失败" }
            } }
        }.onFailure { transcribing = false; status.text = it.message ?: "转写失败" }
    }

    private fun cancelVoice() {
        handler.removeCallbacks(clock)
        voiceGeneration++
        transcriber.cancel()
        recording = false
        transcribing = false
        status.text = "已取消录音"
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || recording || transcribing || sent) return
        val context = JSONObject(launchContext.toString())
        if (!includeScreen) context.remove("dataUrl")
        context.put("text", text).put("autoSend", true).put("fromPanel", true)
        sent = true
        deliverQuestion(context)
    }

    protected open fun deliverQuestion(context: JSONObject) {
        LaunchContext.put(context)
        status.text = "正在打开会话…"
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && ::transcriber.isInitialized && (recording || transcribing)) cancelVoice()
    }

    override fun onStop() {
        if (recording || transcribing) cancelVoice()
        super.onStop()
    }

    override fun onDestroy() {
        cancelVoice()
        transcriber.close()

        super.onDestroy()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dp(value: Int): Int = dp(value.toFloat()).toInt()
}
