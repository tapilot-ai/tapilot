package lifeos.md.tapilot.speech

import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

internal interface TranscriptPolisher {
    val failureReason: String? get() = null
    fun cancel()
    fun polish(text: String): String
}

/** A failed polish never discards a successfully recognized transcript. */
internal class SpeechPolisher(
    private val settings: SpeechPolishSettings,
    private val key: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
) : TranscriptPolisher {
    @Volatile private var active: Call? = null
    @Volatile private var cancelled = false
    override fun cancel() { cancelled = true; active?.cancel(); client.connectionPool.evictAll() }
    override fun polish(text: String): String {
        if (cancelled || !settings.enabled || key.isBlank() || text.isBlank()) return text
        return runCatching {
            val config = settings.validated()
            val endpoint = if (config.baseUrl.endsWith("/chat/completions")) config.baseUrl else "${config.baseUrl}/chat/completions"
            val body = JSONObject().put("model", config.model).put("temperature", .2).put("stream", false)
                .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", config.prompt(text)))
                    .put(JSONObject().put("role", "user").put("content", text)))
            when (config.baseUrl.toHttpUrl().host) {
                "ark.cn-beijing.volces.com" -> if (config.model.startsWith("deepseek-v4-1-")) body.put("thinking", JSONObject().put("type", "disabled"))
                "api.deepseek.com" -> body.put("thinking", JSONObject().put("type", "disabled"))
                "generativelanguage.googleapis.com" -> if (config.model.startsWith("gemini-2.5-")) body.put("reasoning_effort", "none")
            }
            val host = config.baseUrl.toHttpUrl().host
            val aliyun = host in setOf("dashscope.aliyuncs.com", "dashscope-intl.aliyuncs.com", "dashscope-us.aliyuncs.com") ||
                host.endsWith(".maas.aliyuncs.com")
            // Hybrid Qwen defaults to thinking, which commonly exceeds the short polish deadline.
            if (aliyun && Regex("qwen3\\.[567]-(plus|flash)(-\\d{4}-\\d{2}-\\d{2})?").matches(config.model)) {
                body.put("enable_thinking", false)
            }
            val call = client.newCall(Request.Builder().url(endpoint).header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build())
            active = call
            if (cancelled) { call.cancel(); return text }
            call.execute().use { response ->
                if (!response.isSuccessful) return text
                val reader = response.body?.charStream() ?: return text
                val chars = CharArray(1024 * 1024 + 1)
                var count = 0
                while (count < chars.size) {
                    val read = reader.read(chars, count, chars.size - count)
                    if (read < 0) break
                    count += read
                }
                if (count == chars.size) return text
                val content = JSONObject(String(chars, 0, count)).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.opt("content") as? String
                content?.trim()?.takeIf { it.isNotEmpty() } ?: text
            }
        }.getOrDefault(text).also { active = null }
    }
}
