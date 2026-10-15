package lifeos.md.tapilot.speech

import android.content.Context
import lifeos.md.tapilot.bridge.ModelCredentialStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

internal data class SpeechPolishSettings(
    val enabled: Boolean = false,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val promptZh: String = SpeechPolishPrompts.zh,
    val promptEn: String = SpeechPolishPrompts.en,
) {
    fun validated(): SpeechPolishSettings {
        val url = baseUrl.trim().toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null && baseUrl.length <= 2048) { "润色 API 地址必须为不含凭据或查询参数的 HTTPS 地址" }
        require(model.trim().isNotEmpty() && model.length <= 256 && model.none { it == '\r' || it == '\n' || it == '\u0000' }) { "请填写有效的润色模型名称" }
        require(listOf(promptZh, promptEn).all { it.isNotBlank() && it.length <= 8000 }) { "润色提示词不能为空且不超过 8000 字符" }
        return copy(baseUrl = url.toString().trimEnd('/'), model = model.trim())
    }
    fun prompt(text: String): String {
        val han = text.count { it in '\u3400'..'\u9fff' || it in '\uf900'..'\ufaff' }
        val latin = Regex("[A-Za-z]+").findAll(text).count()
        return if (han > latin) promptZh else promptEn
    }
    fun json() = JSONObject().put("enabled", enabled).put("baseUrl", baseUrl).put("model", model).put("promptZh", promptZh).put("promptEn", promptEn)
}

internal class SpeechPolishStore(context: Context) {
    private val preferences = context.getSharedPreferences("tapilot-speech-polish", Context.MODE_PRIVATE)
    private val credentials = ModelCredentialStore(context)
    fun load(): SpeechPolishSettings = runCatching {
        val json = JSONObject(preferences.getString("settings", "{}").orEmpty())
        val defaults = SpeechPolishSettings()
        defaults.copy(enabled = json.optBoolean("enabled"), baseUrl = json.optString("baseUrl", defaults.baseUrl),
            model = json.optString("model", defaults.model), promptZh = json.optString("promptZh", defaults.promptZh),
            promptEn = json.optString("promptEn", defaults.promptEn)).validated()
    }.getOrDefault(SpeechPolishSettings())
    fun enableForSiwcIfUnset(): Boolean {
        val saved = JSONObject(preferences.getString("settings", "{}").orEmpty())
        if (!saved.has("enabled")) save(load().copy(enabled = true))
        return load().enabled
    }
    fun hasKey() = credentials.contains(KEY)
    fun key() = credentials.get(KEY).orEmpty()
    fun save(settings: SpeechPolishSettings, replacementKey: String = "") {
        // Turning off must take effect even if a partially edited URL or key is invalid.
        if (!settings.enabled) {
            check(preferences.edit().putString("settings", load().copy(enabled = false).json().toString()).commit()) { "无法关闭语音润色" }
        }
        val valid = settings.validated()
        val key = replacementKey.trim()
        require(key.length <= 4096 && key.none { it == '\r' || it == '\n' || it == '\u0000' }) { "无效的润色 API Key" }
        if (key.isNotBlank()) credentials.set(KEY, key)
        check(preferences.edit().putString("settings", valid.json().toString()).commit()) { "无法保存润色配置" }
    }
    fun deleteKey() = credentials.delete(KEY)
    companion object { const val KEY = "speech-polish" }
}
