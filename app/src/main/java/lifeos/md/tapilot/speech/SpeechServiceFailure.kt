package lifeos.md.tapilot.speech

import lifeos.md.tapilot.AppText.text as appText
/** Keep service diagnostics actionable without copying response bodies or credentials into UI. */
object SpeechServiceFailure {
    fun message(provider: String, status: Int?, body: String, requestId: String? = null): String {
        val reason = when {
            body.contains("requested resource not granted", true) ->
                appText("当前 API Key 未获语音资源权限。请在豆包语音控制台开通 Seed ASR 2.0 小时版（volc.seedasr.sauc.duration）。", "This API Key lacks speech resource access. Enable Seed ASR 2.0 duration (volc.seedasr.sauc.duration) in Doubao Voice.")
            body.contains("quota", true) || body.contains("balance", true) || body.contains("arrear", true) ->
                appText("语音服务额度不足，请在服务控制台检查余额和可用额度。", "Speech quota exhausted. Check balance and available quota in the provider console.")
            status == 401 -> appText("语音鉴权失败，请检查所选服务的 API Key。", "Speech authentication failed. Check the selected provider API Key.")
            status == 403 -> if (provider == "volcengine")
                appText("语音服务拒绝访问，请检查豆包语音 API Key 的资源权限和服务开通状态。", "Speech access denied. Check Doubao Voice API Key permissions and service activation.")
                else appText("Deepgram 拒绝访问，请检查 API Key 的项目权限。", "Deepgram denied access. Check the API Key project permissions.")
            status == 429 -> appText("语音请求过于频繁，请稍后重试。", "Speech rate limit reached. Try again later.")
            else -> appText("语音服务连接失败，请稍后重试。", "Speech connection failed. Try again later.")
        }
        val reference = requestId?.takeIf { it.matches(Regex("[a-zA-Z0-9-]{1,80}")) }
        return reason + (status?.let { " HTTP $it" } ?: "") + (reference?.let { appText(" · 请求编号 $it", " · Request ID $it") } ?: "")
    }
}
