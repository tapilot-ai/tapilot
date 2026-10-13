package lifeos.md.tapilot.chatgpt

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Adapt the native agent's chat contract to the stateless SIWC Responses contract. */
object ChatGptResponses {
    fun body(model: String, messages: JSONArray, tools: JSONArray): JSONObject {
        val input = JSONArray()
        for (index in 0 until messages.length()) {
            val message = messages.getJSONObject(index)
            val role = message.getString("role")
            val savedOutput = message.optJSONArray("tapilotResponsesOutput")
            if (role == "assistant" && savedOutput != null) {
                for (i in 0 until savedOutput.length()) input.put(savedOutput.getJSONObject(i))
                continue
            }
            if (role == "tool") {
                input.put(JSONObject().put("type", "function_call_output").put("call_id", message.getString("tool_call_id"))
                    .put("output", message.optString("content")))
                continue
            }
            val content = JSONArray()
            val blocks = message.optJSONArray("content")
            if (blocks == null) {
                val text = if (message.isNull("content")) "" else message.optString("content")
                if (text.isNotBlank()) content.put(JSONObject().put("type", if (role == "assistant") "output_text" else "input_text").put("text", text))
            } else for (i in 0 until blocks.length()) {
                val block = blocks.getJSONObject(i)
                when (block.getString("type")) {
                    "text" -> content.put(JSONObject().put("type", if (role == "assistant") "output_text" else "input_text").put("text", block.getString("text")))
                    "image_url" -> content.put(JSONObject().put("type", "input_image").put("image_url", block.getJSONObject("image_url").getString("url")))
                    else -> error("ChatGPT 不支持此消息内容类型")
                }
            }
            if (content.length() > 0) input.put(JSONObject().put("role", if (role == "system") "developer" else role).put("content", content))
            val calls = message.optJSONArray("tool_calls") ?: JSONArray()
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i); val function = call.getJSONObject("function")
                input.put(JSONObject().put("type", "function_call").put("namespace", "tapilot").put("call_id", call.getString("id"))
                    .put("name", function.getString("name")).put("arguments", function.getString("arguments")))
            }
        }
        val body = JSONObject().put("model", model).put("input", input).put("store", false).put("stream", true)
            .put("include", JSONArray().put("reasoning.encrypted_content"))
        if (tools.length() > 0) {
            val functions = JSONArray()
            for (i in 0 until tools.length()) {
                val tool = tools.getJSONObject(i); require(tool.getString("type") == "function")
                functions.put(JSONObject(tool.getJSONObject("function").toString()).put("type", "function").put("strict", false))
            }
            body.put("tools", JSONArray().put(JSONObject().put("type", "namespace").put("name", "tapilot")
                .put("description", "Tapilot local task planning and Android phone operations").put("tools", functions)))
        }
        return body
    }
    fun params(model: String, token: String, messages: JSONArray, tools: JSONArray): JSONObject = JSONObject()
        .put("url", "${ChatGptOAuth.RESOURCE}/responses").put("method", "POST")
        .put("headers", JSONObject().put("Authorization", "Bearer $token").put("Content-Type", "application/json").put("Accept", "text/event-stream"))
        .put("bodyBase64", Base64.getEncoder().encodeToString(body(model, messages, tools).toString().toByteArray()))

    fun decode(response: JSONObject): JSONObject {
        val text = String(Base64.getDecoder().decode(response.getString("bodyBase64")), Charsets.UTF_8)
        if (response.getInt("status") !in 200..299) {
            val error = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
            throw IllegalStateException(failure(error, response.getInt("status")))
        }
        var completed: JSONObject? = null
        val data = mutableListOf<String>()
        fun event() {
            if (data.isEmpty()) return
            val payload = data.joinToString("\n"); data.clear()
            if (payload == "[DONE]") return
            val value = JSONObject(payload)
            when (value.optString("type")) {
                "response.completed" -> {
                    val result = value.getJSONObject("response")
                    check(result.optString("status") == "completed") { "ChatGPT 响应未完成，请重试" }
                    completed = result
                }
                "response.failed" -> error(failure(value.optJSONObject("response")?.optJSONObject("error")))
                "response.incomplete" -> error("ChatGPT 响应未完成，请重试或更换模型")
                "error" -> error(failure(value.optJSONObject("error") ?: value))
            }
        }
        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            if (line.isEmpty()) event() else if (line.startsWith("data:")) data += line.substring(5).removePrefix(" ")
        }
        event()
        val result = completed ?: error("ChatGPT 连接中断，未收到完整结果，请重试")
        val output = result.optJSONArray("output") ?: JSONArray()
        val textParts = mutableListOf<String>(); val calls = JSONArray()
        for (i in 0 until output.length()) {
            val item = output.getJSONObject(i)
            when (item.optString("type")) {
                "message" -> {
                    val content = item.optJSONArray("content") ?: JSONArray()
                    for (j in 0 until content.length()) {
                        val block = content.getJSONObject(j)
                        if (block.optString("type") == "output_text") textParts += block.getString("text")
                        if (block.optString("type") == "refusal") textParts += block.getString("refusal")
                    }
                }
                "function_call" -> {
                    require(item.optString("namespace", "tapilot") == "tapilot") { "Unexpected ChatGPT tool namespace" }
                    calls.put(JSONObject().put("id", item.getString("call_id")).put("type", "function")
                        .put("function", JSONObject().put("name", item.getString("name")).put("arguments", item.getString("arguments"))))
                }
            }
        }
        check(textParts.isNotEmpty() || calls.length() > 0) { "ChatGPT 未返回文字或操作指令" }
        val message = JSONObject().put("role", "assistant").put("content", textParts.joinToString("\n"))
        if (calls.length() > 0) message.put("tool_calls", calls)
        return JSONObject().put("tapilotResponsesOutput", output).put("choices", JSONArray().put(JSONObject().put("message", message)
            .put("finish_reason", if (calls.length() > 0) "tool_calls" else "stop")))
    }
    private fun failure(error: JSONObject?, status: Int = 0): String = when (error?.optString("code")) {
        "subscription_sharing_usage_limit_exceeded" -> "ChatGPT 套餐共享额度已用完，请在 ChatGPT 设置中检查用量或稍后重试"
        "subscription_sharing_usage_unavailable" -> "此 ChatGPT 账号暂时无法共享套餐，请检查账号与工作区权限"
        else -> when (status) {
            401 -> "ChatGPT 授权已失效，请重新登录"
            403 -> "ChatGPT 未获准使用此模型或套餐，请检查授权"
            429 -> "ChatGPT 请求限流或额度不足，请稍后重试"
            else -> "ChatGPT 请求失败${if (status > 0) " · HTTP $status" else ""}，请重试或检查模型权限"
        }
    }
}
