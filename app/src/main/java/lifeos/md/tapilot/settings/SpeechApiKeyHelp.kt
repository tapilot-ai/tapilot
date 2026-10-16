package lifeos.md.tapilot.settings

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp

/** Provider-specific onboarding beside the transcription credential field. */
@Composable
internal fun SpeechApiKeyHelp(provider: String, zh: Boolean) {
    fun label(chinese: String, english: String) = if (zh) chinese else english
    val volcengine = provider == "volcengine"
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    fun open(url: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, label("请安装浏览器后重试", "Install a browser and try again"), Toast.LENGTH_LONG).show()
        }
    }
    Column {
        Text(
            if (volcengine) label(
                "登录火山引擎并完成实名认证，在豆包语音中开通流式语音识别 2.0 服务，然后进入 API Key 管理创建密钥。这里需要豆包语音 API Key，不能使用方舟模型密钥或旧版 APP ID。",
                "Sign in to Volcengine and complete identity verification. Enable Streaming Speech Recognition 2.0 in Doubao Voice, then create a key in API Key management. Use a Doubao Voice API Key; Ark model keys and legacy APP IDs are not supported."
            ) else label(
                "登录 Deepgram，选择项目，进入 Settings → API Keys → Create a New API Key，创建允许语音转写的密钥。密钥只显示一次，请及时复制。",
                "Sign in to Deepgram, select a project, then open Settings → API Keys → Create a New API Key. Create a key with permission to transcribe audio. Copy it immediately; the secret is only shown once."
            ), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { open(if (volcengine)
            "https://console.volcengine.com/speech/new/setting/apikeys"
            else "https://console.deepgram.com/") }) {
            Text(if (volcengine) label("获取火山引擎 API Key ↗", "Get a Volcengine API Key ↗")
                else label("获取 Deepgram API Key ↗", "Get a Deepgram API Key ↗"))
        }
        TextButton(onClick = { open(if (volcengine)
            "https://docs.volcengine.com/docs/DoubaoVoice/QuickStartNewConsole?lang=${if (zh) "zh" else "en"}"
            else "https://developers.deepgram.com/docs/create-additional-api-keys") }) {
            Text(label("查看官方获取指南 ↗", "Read the official setup guide ↗"))
        }
        Text(label("复制密钥后粘贴到下方，点击“检查连接”即可保存并验证。",
            "Paste the key below, then select Check connection to save and verify it."),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
