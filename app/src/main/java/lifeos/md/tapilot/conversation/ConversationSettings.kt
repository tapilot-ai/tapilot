package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.AppText.text as appText
import android.content.Context
import android.os.Handler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import lifeos.md.tapilot.bridge.ModelCredentialStore
import lifeos.md.tapilot.chatgpt.ChatGptConnection
import lifeos.md.tapilot.services.ServiceGateway
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Settings persistence and connection checks are independent of task orchestration. */
internal class ConversationSettings(
    private val activity: Context,
    private val state: () -> JSONObject,
    private val isRunning: () -> Boolean,
    private val isClosed: () -> Boolean,
    private val credentials: ModelCredentialStore,
    private val chatGpt: ChatGptConnection,
    private val models: ConversationModelClient,
    private val services: ServiceGateway,
    private val worker: Executor,
    private val handler: Handler,
    private val save: () -> Unit,
    private val publish: () -> Unit,
    private val local: (String, String) -> String,
) {
    var status by mutableStateOf(""); private set
    var section by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    private val data get() = state()
    private val running get() = isRunning()
    private val closed get() = isClosed()
    val preset get() = data.optString("preset", "doubao")
    fun profile(id: String = preset): JSONObject = JSONObject((data.optJSONObject("profiles")?.optJSONObject(id) ?: ModelProfiles.defaults(id)).toString())
    fun clearStatus() { status = "" }
    fun testChatGpt() {
        if (busy || running || !chatGpt.ready) return
        section = "chatgpt"; busy = true; status = local("正在检查 ChatGPT…", "Checking ChatGPT…")
        val account = chatGpt.activeId; val model = chatGpt.model
        worker.execute {
            val result = runCatching { models.chatGpt(account, model, JSONArray().put(JSONObject().put("role", "user").put("content", "Reply OK.")), JSONArray(), UUID.randomUUID().toString(), AtomicBoolean(false)) }
            handler.post { if (!closed) { busy = false; status = result.fold({ local("ChatGPT 连接成功", "ChatGPT connection succeeded") }, { it.message ?: "ChatGPT 连接失败" }) } }
        }
    }
    fun selectPreset(id: String) { if (id !in ModelProfiles.ids || running) return; data.put("preset", id); status = ""; save(); publish() }
    fun saveModel(profile: JSONObject, replacementKey: String = "", reset: Boolean = false) {
        if (running) return
        section = "model"
        runCatching {
            if (reset) credentials.delete(preset) else if (replacementKey.isNotBlank()) credentials.set(preset, replacementKey.trim())
            data.getJSONObject("profiles").put(preset, if (reset) ModelProfiles.defaults(preset) else JSONObject().apply { listOf("baseUrl", "modelName", "modelFamily").forEach { put(it, profile.optString(it)) } })
            save(); status = local("已保存", "Saved"); publish()
        }.onFailure { status = it.message ?: appText("保存失败", "Save failed") }
    }
    fun preference(name: String, value: Any) { if (!running) { data.put(name, value); save(); publish() } }
    fun saveSpeech(provider: String, key: String) {
        section = "speech"
        runCatching {
            require(provider in listOf("volcengine", "deepgram"))
            if (key.isNotBlank()) credentials.set("speech-$provider", key.trim())
            activity.getSharedPreferences("tapilot-speech", 0).edit().putString("provider", provider).commit()
            preference("speechProvider", provider); status = local("语音配置已保存", "Speech configuration saved")
        }.onFailure { status = it.message ?: appText("保存失败", "Save failed") }
    }
    fun resetSpeech(provider: String) {
        if (running) return
        section = "speech"
        runCatching {
            require(provider in listOf("volcengine", "deepgram"))
            credentials.delete("speech-$provider")
            status = local("已清除该转写服务的密钥", "Transcription key removed"); publish()
        }.onFailure { status = it.message.orEmpty() }
    }
    fun checkSpeech(provider: String) = call("speech.checkConnection", JSONObject().put("provider", provider))
    fun call(method: String, params: JSONObject = JSONObject()) {
        if (busy || running) return
        section = if (method.startsWith("speech.")) "speech" else "general"
        busy = true; status = local("正在检查…", "Checking…")
        services.call(method, params) { result ->
            busy = false
            status = result.fold({ local("检查通过", "Check passed") }, { it.message ?: "检查失败" }); publish()
        }
    }
    fun testModel() {
        if (busy || running) return
        section = "model"
        busy = true; status = local("正在检查模型…", "Checking model…")
        val model = profile()
        worker.execute {
            val result = runCatching {
                val key = credentials.get(preset).orEmpty()
                check(key.isNotBlank()) { appText("请先保存模型 API Key", "Save a model API Key first") }
                models.complete(ModelProfile.from(model), key, JSONArray().put(JSONObject().put("role", "user").put("content", "Reply OK.")), JSONArray().put(NativeConversationAgent.tool), UUID.randomUUID().toString(), AtomicBoolean(false))
            }
            handler.post { if (!closed) { busy = false; status = result.fold({ local("模型连接成功", "Model connection succeeded") }, { it.cause?.message ?: it.message ?: "模型连接失败" }) } }
        }
    }
}
