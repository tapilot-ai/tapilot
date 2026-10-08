package lifeos.md.tapilot

import android.widget.TextView
import android.view.Gravity
import android.widget.FrameLayout
import lifeos.md.tapilot.bridge.SpeechRecognition
import org.json.JSONObject

class SpeechPanelVerificationActivity : GlobalAssistantPanelActivity() {
    override fun createRecognition(): SpeechRecognition =
        VerificationRecognition(intent.getLongExtra("startDelay", 0), intent.getBooleanExtra("failStart", false)).let { raw ->
            if (intent.getBooleanExtra("polish", false)) verificationPolishing(this, raw) else raw
        }
    override fun recognitionCredentials() = "volcengine" to "synthetic"
    override fun deliverQuestion(context: JSONObject) {
        val label = TextView(this).apply { text = "验证发送次数 1"; setTextColor(android.graphics.Color.MAGENTA) }
        addContentView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = 80 })
    }
}
