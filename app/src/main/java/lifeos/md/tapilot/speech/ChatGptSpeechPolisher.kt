package lifeos.md.tapilot.speech

import lifeos.md.tapilot.AppText.text as appText
import lifeos.md.tapilot.chatgpt.ChatGptOAuth
import lifeos.md.tapilot.chatgpt.ChatGptResponses
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Uses the same signed-in account; never falls back to an unrelated API provider. */
internal class ChatGptSpeechPolisher(
    private val settings: SpeechPolishSettings,
    private val model: String,
    private val token: (forceRefresh: Boolean) -> String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
) : TranscriptPolisher {
    @Volatile private var cancelled = false
    @Volatile private var active: Call? = null
    override var failureReason: String? = null
        private set
    override fun cancel() { cancelled = true; active?.cancel(); client.connectionPool.evictAll() }
    override fun polish(text: String): String {
        failureReason = null
        if (cancelled || !settings.enabled || model.isBlank() || text.isBlank()) return text
        return try { runCatching {
            val messages = JSONArray().put(JSONObject().put("role", "system").put("content", settings.prompt(text)))
                .put(JSONObject().put("role", "user").put("content", text))
            val body = ChatGptResponses.body(model, messages, JSONArray())
                .put("reasoning", JSONObject().put("effort", "low"))
            for (attempt in 0..1) {
                if (cancelled) return text
                val credential = token(attempt > 0)
                if (cancelled) return text
                val call = client.newCall(Request.Builder().url("${ChatGptOAuth.RESOURCE}/responses")
                    .header("Authorization", "Bearer $credential").header("Accept", "text/event-stream")
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build())
                active = call
                if (cancelled) { call.cancel(); return text }
                call.execute().use { response ->
                    if (response.code == 401 && attempt == 0) return@use
                    if (!response.isSuccessful) {
                        failureReason = "ChatGPT HTTP ${response.code}"
                        return text
                    }
                    if (cancelled) return text
                    val source = response.body?.source() ?: return text
                    val limit = 1024L * 1024
                    if (source.request(limit + 1)) return text
                    val bytes = source.readByteArray()
                    if (cancelled) return text
                    val decoded = ChatGptResponses.decode(JSONObject().put("status", response.code)
                        .put("bodyBase64", Base64.getEncoder().encodeToString(bytes)))
                    return decoded.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                        .optString("content").trim().ifBlank { text }
                }
            }
            text
        }.getOrElse {
            failureReason = when (it) {
                is java.io.InterruptedIOException -> appText("ChatGPT 请求超时", "ChatGPT request timed out")
                is java.io.IOException -> appText("ChatGPT 网络连接失败", "ChatGPT network connection failed")
                else -> "ChatGPT 响应不完整或授权不可用"
            }
            text
        } } finally { active = null }
    }
}
