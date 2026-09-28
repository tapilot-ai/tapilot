package lifeos.md.tapilot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import lifeos.md.tapilot.bridge.ModelCredentialStore
import lifeos.md.tapilot.bridge.SpeechTranscriber
import org.json.JSONObject

/** Compact entry over the app the user was using. The conversation continues in MainActivity. */
class GlobalAssistantPanelActivity : Activity() {
    private lateinit var transcriber: SpeechTranscriber
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var recording = false
    private var sent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transcriber = SpeechTranscriber(this)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply {
            gravity = Gravity.BOTTOM
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            dimAmount = 0.16f
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(dp(22f), dp(22f), dp(22f), dp(22f), 0f, 0f, 0f, 0f)
            }
        }
        root.addView(TextView(this).apply {
            text = "问 Tapilot"
            textSize = 18f
            setTextColor(Color.rgb(30, 34, 52))
        })
        status = TextView(this).apply {
            text = "按住说话或输入，发送后继续处理"
            textSize = 12f
            setTextColor(Color.rgb(99, 103, 122))
        }
        root.addView(status)
        input = EditText(this).apply {
            hint = "问我当前屏幕上的事…"
            minLines = 1
            maxLines = 3
            setSingleLine(false)
        }
        root.addView(input)
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        val voice = Button(this).apply {
            text = "按住说话"
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startVoice(); true }
                    MotionEvent.ACTION_UP -> { finishVoice(); true }
                    MotionEvent.ACTION_CANCEL -> { cancelVoice(); true }
                    else -> true
                }
            }
        }
        actions.addView(voice)
        actions.addView(Button(this).apply {
            text = "发送"
            setOnClickListener { send() }
        })
        root.addView(actions)
        setContentView(root)
    }

    private fun startVoice() {
        val credentials = ModelCredentialStore(this).getAll()
        val preferred = getSharedPreferences("tapilot-speech", 0)
            .getString("provider", "volcengine") ?: "volcengine"
        val provider = if (!credentials["speech-$preferred"].isNullOrBlank()) preferred
            else if (preferred == "volcengine") "deepgram" else "volcengine"
        val key = credentials["speech-$provider"]
        if (key.isNullOrBlank()) {
            status.text = "请先在 Tapilot 设置页填写语音 API Key"
            return
        }
        runCatching {
            transcriber.start(provider, key, "zh-CN") { result ->
                runOnUiThread {
                    result.onSuccess { recording = true; status.text = "正在听…松开发送" }
                        .onFailure { status.text = it.message ?: "录音未启动" }
                }
            }
        }.onFailure { status.text = it.message ?: "录音未启动" }
    }

    private fun finishVoice() {
        if (!recording) return
        recording = false
        status.text = "正在转写…"
        runCatching {
            transcriber.finish { result ->
                runOnUiThread {
                    result.onSuccess { text ->
                        input.setText(text)
                        status.text = "转写完成"
                        send()
                    }.onFailure { status.text = it.message ?: "转写失败" }
                }
            }
        }.onFailure { status.text = it.message ?: "转写失败" }
    }

    private fun cancelVoice() { if (recording) transcriber.cancel(); recording = false }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val context = LaunchContext.consume() ?: JSONObject().put("kind", "panel")
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
        if (!sent) LaunchContext.consume()
        super.onDestroy()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dp(value: Int): Int = dp(value.toFloat()).toInt()
}
