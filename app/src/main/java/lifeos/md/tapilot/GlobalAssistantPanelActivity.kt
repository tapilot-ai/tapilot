package lifeos.md.tapilot

import android.app.Activity
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
import lifeos.md.tapilot.bridge.SpeechTranscriber
import org.json.JSONObject

/** Compact entry over the app the user was using. The conversation continues in MainActivity. */
class GlobalAssistantPanelActivity : Activity() {
    private lateinit var transcriber: SpeechTranscriber
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var recording = false
    private var transcribing = false
    private var sent = false
    private var cancelledOnRelease = false
    private var transcribeOnRelease = false
    private var voiceGeneration = 0
    private lateinit var launchContext: JSONObject
    private var includeScreen = true
    private var voiceStartY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transcriber = SpeechTranscriber(this)
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
            setPadding(dp(18), dp(16), dp(18), dp(20))
            background = GradientDrawable().apply {
                setColor(getColor(if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_surface else R.color.brand_surface))
                cornerRadii = floatArrayOf(dp(22f), dp(22f), dp(22f), dp(22f), 0f, 0f, 0f, 0f)
            }
        }
        root.addView(TextView(this).apply {
            text = "问 Tapilot"
            textSize = 18f
            setTextColor(getColor(if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_text else R.color.brand_text))
        })
        status = TextView(this).apply {
            text = "按住说话或输入，发送后继续处理"
            textSize = 12f
            setTextColor(getColor(if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_muted else R.color.brand_muted))
        }
        root.addView(status)
        if (launchContext.has("dataUrl")) {
            val reference = CheckBox(this).apply {
                text = "引用当前屏幕"; isChecked = true; minHeight = dp(48)
                buttonTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.brand_blue))
                setTextColor(getColor(if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_text else R.color.brand_text))
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
            onHold = { startVoice() }
            onMove = { gesture ->
                cancelledOnRelease = gesture == RecordingGesture.Cancel
                transcribeOnRelease = gesture == RecordingGesture.Transcribe
                if (recording) status.text = if (cancelledOnRelease) "松开取消" else "松开转文字"
            }
            onRelease = { if (cancelledOnRelease) cancelVoice() else finishVoice(false) }
            onCancel = { cancelVoice() }
            hint = "长按语音输入"
            setTextColor(getColor(if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_text else R.color.brand_text))
            setHintTextColor(getColor(if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_muted else R.color.brand_muted))
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
        root.addView(input)
        val actions = LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, 0, dp(72), 0)
        }
        val voice = Button(this).apply {
            text = "按住说话"
            stateListAnimator = null
            setTextColor(getColor(R.color.brand_blue))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f)
                setColor(getColor(if (AppTheme.isDark(this@GlobalAssistantPanelActivity)) R.color.brand_dark_soft else R.color.brand_soft))
            }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { voiceStartY = event.rawY; startVoice(); true }
                    MotionEvent.ACTION_UP -> { if (cancelledOnRelease) cancelVoice() else finishVoice(!transcribeOnRelease); true }
                    MotionEvent.ACTION_MOVE -> {
                        val gesture = lifeos.md.tapilot.chat.recordingGesture(voiceStartY, event.rawX, event.rawY,
                            resources.displayMetrics.widthPixels.toFloat(), resources.displayMetrics.density)
                        cancelledOnRelease = gesture == RecordingGesture.Cancel
                        transcribeOnRelease = gesture == RecordingGesture.Transcribe
                        if (recording) status.text = if (cancelledOnRelease) "松开取消" else "正在录音 · 松开发送"
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
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(getColor(R.color.brand_blue))
                cornerRadius = dp(10f)
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
                val keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom
                if (panelParams.bottomMargin != keyboard) {
                    panelParams.bottomMargin = keyboard
                    root.layoutParams = panelParams
                }
                insets
            }
        }
        root.setOnClickListener { }
        setContentView(container)
    }

    private fun startVoice() {
        if (recording || transcribing || sent) return
        val provider = getSharedPreferences("tapilot-speech", 0).getString("provider", "volcengine") ?: "volcengine"
        val key = ModelCredentialStore(this).getAll()["speech-$provider"]
        if (key.isNullOrBlank()) { status.text = "请先在 Tapilot 设置页填写语音 API Key"; return }
        val generation = ++voiceGeneration
        recording = true
        cancelledOnRelease = false
        transcribeOnRelease = false
        status.text = "正在录音 · 上滑后松开取消"
        runCatching {
            transcriber.start(provider, key, "zh-CN", onError = { error -> runOnUiThread {
                if (generation == voiceGeneration) { recording = false; status.text = error.message ?: "录音失败" }
            } }) { result -> runOnUiThread {
                if (generation == voiceGeneration) result.onFailure { recording = false; status.text = it.message ?: "录音失败" }
            } }
        }.onFailure { recording = false; status.text = it.message ?: "录音未启动" }
    }

    private fun finishVoice(sendOnRelease: Boolean) {
        if (!recording) return
        recording = false
        transcribing = true
        val generation = voiceGeneration
        status.text = "正在转写…"
        runCatching {
            transcriber.finish { result -> runOnUiThread {
                if (generation != voiceGeneration || isFinishing || isDestroyed) return@runOnUiThread
                transcribing = false
                result.onSuccess { text ->
                    input.setText(listOf(input.text.toString().trim(), text.trim()).filter { it.isNotBlank() }.joinToString(" "))
                    status.text = "转写完成"; if (sendOnRelease) send()
                }.onFailure { transcribing = false; status.text = it.message ?: "转写失败" }
            } }
        }.onFailure { transcribing = false; status.text = it.message ?: "转写失败" }
    }

    private fun cancelVoice() {
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
        LaunchContext.put(context)
        sent = true
        status.text = "正在打开会话…"
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    override fun onDestroy() {
        cancelVoice()
        transcriber.close()

        super.onDestroy()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dp(value: Int): Int = dp(value.toFloat()).toInt()
}
