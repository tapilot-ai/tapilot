package lifeos.md.tapilot.conversation

import java.net.URI
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object NativeModelRequest {
    fun params(model: JSONObject, key: String, messages: JSONArray): JSONObject {
        val endpoint = ModelProfiles.endpoint(model.getString("baseUrl"))
        val body = JSONObject().put("model", model.getString("modelName")).put("messages", messages)
            .put("tools", JSONArray().put(NativeConversationAgent.tool)).put("tool_choice", "auto").put("stream", false).put("max_tokens", 2048)
        // DashScope hybrid Qwen models default to reasoning; use a fast, non-streaming reply.
        val host = URI(endpoint).host
        if ((host == "dashscope.aliyuncs.com" || host.endsWith(".maas.aliyuncs.com")) && model.getString("modelName").startsWith("qwen3"))
            body.put("enable_thinking", false)
        return JSONObject().put("url", endpoint).put("method", "POST")
            .put("headers", JSONObject().put("Authorization", "Bearer $key").put("Content-Type", "application/json"))
            .put("bodyBase64", Base64.getEncoder().encodeToString(body.toString().toByteArray()))
    }
    fun decode(response: JSONObject): JSONObject {
        if (response.getInt("status") !in 200..299) {
            val code = runCatching { JSONObject(String(Base64.getDecoder().decode(response.getString("bodyBase64"))))
                .optJSONObject("error")?.optString("code") }.getOrNull().orEmpty()
            val reason = when {
                code.equals("Arrearage", true) -> "模型账户欠费或余额不足，请检查服务账户状态"
                response.getInt("status") == 401 -> "模型 API Key 鉴权失败，请检查配置"
                response.getInt("status") == 429 -> "模型请求限流或额度不足，请稍后重试并检查额度"
                else -> "模型请求失败，请检查 API 配置、权限和额度"
            }
            error("$reason · HTTP ${response.getInt("status")}")
        }
        return JSONObject(String(Base64.getDecoder().decode(response.getString("bodyBase64"))))
    }
}
