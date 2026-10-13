package lifeos.md.tapilot.conversation

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, atomic private storage. API keys continue to live only in Android Keystore. */
class ConversationStore(private val file: File) {
    fun exists() = file.isFile
    private val draftFile = File(file.parentFile, "${file.name}.draft")
    @Synchronized fun read(): JSONObject {
        val state = if (file.isFile) JSONObject(file.readText()) else empty()
        if (draftFile.isFile) {
            val draft = JSONObject(draftFile.readText())
            if (draft.optString("baseRevision") == state.optString("storageRevision")) state.put("draft", draft.getString("text"))
        }
        return state
    }
    @Synchronized fun save(value: JSONObject) {
        require(value.getInt("version") == 1)
        value.put("storageRevision", java.util.UUID.randomUUID().toString())
        writeAtomically(file, value.toString())
        draftFile.delete() // A leftover draft has the old revision and is ignored after a crash.
    }
    @Synchronized fun saveDraft(text: String) {
        val revision = if (file.isFile) JSONObject(file.readText()).optString("storageRevision") else ""
        writeAtomically(draftFile, JSONObject().put("baseRevision", revision).put("text", text).toString())
    }
    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val pending = File(target.parentFile, "${target.name}.tmp")
        try {
            pending.outputStream().use { it.write(text.toByteArray()); it.fd.sync() }
            check(pending.renameTo(target)) { "无法保存会话" }
        } finally { pending.delete() }
    }
    companion object {
        fun empty() = JSONObject().put("version", 1).put("profiles", JSONObject())
            .put("history", JSONArray()).put("preset", "doubao").put("active", "chat-${java.util.UUID.randomUUID()}")
            .put("draft", "").put("speechProvider", "volcengine").put("advanced", false).put("confirmEveryStep", false)

        fun importLegacy(values: JSONObject): JSONObject {
            val state = empty()
            val profiles = JSONObject()
            ModelProfiles.ids.forEach { id ->
                val defaults = ModelProfiles.defaults(id)
                listOf("baseUrl", "modelName", "modelFamily").forEach { field ->
                    val key = "midscene.modelPresetConfig.$id.$field"
                    val legacy = if (values.has(key)) values.getString(key)
                        else if (id == "custom" && values.has("midscene.$field")) values.getString("midscene.$field")
                        else defaults.optString(field)
                    defaults.put(field, legacy)
                }
                profiles.put(id, defaults)
            }
            state.put("profiles", profiles)
            val preset = values.optString("midscene.modelPreset").takeIf { it in ModelProfiles.ids }
                ?: if (values.has("midscene.baseUrl")) "custom" else "doubao"
            state.put("preset", preset)
            state.put("active", values.optString("tapilot.activeConversation", state.getString("active")))
            state.put("draft", values.optString("midscene.task"))
            state.put("yaml", values.optString("midscene.yaml"))
            state.put("speechProvider", values.optString("tapilot.speech.provider", "volcengine"))
            state.put("advanced", values.optString("midscene.advancedMode") == "true")
            state.put("confirmEveryStep", values.optString("tapilot.confirmEveryStep") == "true")
            val history = JSONArray(values.optString("midscene.reportHistory", "[]"))
            for (i in 0 until history.length()) {
                val entry = history.getJSONObject(i)
                if (!entry.has("source")) entry.put("source", entry.optString("task"))
                if (entry.optString("status") == "running") entry.put("status", "stopped")
                if (!entry.has("status")) entry.put("status", if (entry.optBoolean("succeeded", true)) "succeeded" else "failed")
                if (!entry.has("id")) entry.put("id", "legacy-$i")
                if (!entry.has("createdAt")) entry.put("createdAt", System.currentTimeMillis())
                if (!entry.has("reports")) entry.put("reports", JSONArray())
            }
            return state.put("history", history)
        }
    }
}

object ModelProfiles {
    val ids = listOf("doubao", "qwen", "deepseek", "gemini", "openai", "kimi", "mimo", "glm", "custom")
    fun defaults(id: String): JSONObject {
        val fields = when (id) {
            "doubao" -> listOf("https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-2-1-turbo-260628", "doubao-seed")
            "qwen" -> listOf("https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.7-plus", "qwen3")
            "deepseek" -> listOf("https://api.deepseek.com", "deepseek-flash", "deepseek")
            "gemini" -> listOf("https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-3.5-flash", "gemini")
            "openai" -> listOf("https://api.openai.com/v1", "gpt-6-astra", "gpt-6")
            "kimi" -> listOf("https://api.moonshot.cn/v1", "kimi-k3", "kimi3")
            "mimo" -> listOf("https://api.xiaomimimo.com/v1", "mimo-v2.5", "xiaomi-mimo")
            "glm" -> listOf("https://open.bigmodel.cn/api/paas/v4", "glm-5v-turbo", "glm-v")
            else -> listOf("", "", "")
        }
        return JSONObject().put("baseUrl", fields[0]).put("modelName", fields[1]).put("modelFamily", fields[2])
    }
    fun endpoint(base: String): String {
        val uri = java.net.URI(base.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "请填写有效的 HTTPS API 地址" }
        val path = uri.path.orEmpty().trimEnd('/')
        val endpoint = when {
            path.endsWith("/responses") -> error("对话 API 需要 chat/completions 接口，请填写兼容地址")
            path.endsWith("/chat/completions") -> path
            path.endsWith("/chat") -> "$path/completions"
            else -> "$path/chat/completions"
        }
        return java.net.URI(uri.scheme, null, uri.host, uri.port, endpoint, uri.query, null).toString()
    }
}
