package lifeos.md.tapilot

import android.os.Bundle
import android.webkit.WebView
import android.webkit.ValueCallback
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import lifeos.md.tapilot.chat.ChatController
import lifeos.md.tapilot.chat.ChatScreen
import org.json.JSONObject

class SpeechChatVerificationActivity : ComponentActivity() {
    private lateinit var controller: ChatController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val counter = TextView(this).apply { text = "验证发送次数 0"; setTextColor(android.graphics.Color.MAGENTA) }
        var sends = 0
        val engine = object : WebView(this) {
            override fun evaluateJavascript(script: String, callback: ValueCallback<String>?) {
                val payload = runCatching { JSONObject(script.substringAfter("?.(").substringBeforeLast(")")) }.getOrNull()
                if (payload?.optString("action") == "send") { sends++; counter.text = "验证发送次数 $sends" }
            }
        }
        val rawRecognition = VerificationRecognition(intent.getLongExtra("startDelay", 0), intent.getBooleanExtra("failStart", false))
        val recognition = if (intent.getBooleanExtra("polish", false)) verificationPolishing(this, rawRecognition) else rawRecognition
        controller = ChatController(this, engine, {}, { _, done -> done(Result.success(null)) }, recognition, { "synthetic" })
        controller.update(JSONObject().put("protocolVersion", 1).put("ready", true).put("configured", true)
            .put("messages", org.json.JSONArray()).put("conversations", org.json.JSONArray()).put("language", "zh")
            .put("activeConversationId", "speech-verification").toString())
        val root = FrameLayout(this)
        root.addView(ComposeView(this).apply { setContent { ChatScreen(controller) } })
        root.addView(counter, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 80 })
        setContentView(root)
    }
    override fun onStop() { controller.cancelRecording(); super.onStop() }
    override fun onDestroy() { controller.close(); super.onDestroy() }
}
