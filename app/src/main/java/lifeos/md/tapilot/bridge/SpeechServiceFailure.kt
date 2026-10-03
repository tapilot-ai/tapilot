package lifeos.md.tapilot.bridge

/** Keep service diagnostics actionable without copying response bodies or credentials into UI. */
object SpeechServiceFailure {
    fun message(provider: String, status: Int?, body: String, requestId: String? = null): String {
        val reason = when {
            body.contains("requested resource not granted", true) ->
                "当前 API Key 未获语音资源权限。请在豆包语音控制台开通 Seed ASR 2.0 小时版（volc.seedasr.sauc.duration）。"
            body.contains("quota", true) || body.contains("balance", true) || body.contains("arrear", true) ->
                "语音服务额度不足，请在服务控制台检查余额和可用额度。"
            status == 401 -> "语音鉴权失败，请检查所选服务的 API Key。"
            status == 403 -> if (provider == "volcengine")
                "语音服务拒绝访问，请检查豆包语音 API Key 的资源权限和服务开通状态。"
                else "Deepgram 拒绝访问，请检查 API Key 的项目权限。"
            status == 429 -> "语音请求过于频繁，请稍后重试。"
            else -> "语音服务连接失败，请稍后重试。"
        }
        val reference = requestId?.takeIf { it.matches(Regex("[a-zA-Z0-9-]{1,80}")) }
        return reason + (status?.let { " HTTP $it" } ?: "") + (reference?.let { " · 请求编号 $it" } ?: "")
    }
}
