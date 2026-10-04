package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject

data class PhoneResult(val success: Boolean, val result: String) {
    fun json() = JSONObject().put("success", success).put("result", result)
}
data class AgentReply(val reply: String, val lastPhoneResult: PhoneResult?)

/** Owns all context; Midscene receives only a concrete instruction and a visible assertion. */
object NativeConversationAgent {
    val tool: JSONObject get() = JSONObject("""{"type":"function","function":{"name":"operate_phone","description":"Execute one concrete phone operation through Midscene and verify a visible success condition. Do not include conversation history.","parameters":{"type":"object","properties":{"instruction":{"type":"string"},"success_condition":{"type":"string"}},"required":["instruction","success_condition"],"additionalProperties":false}}}""")
    fun messages(history: List<JSONObject>, source: String, chinese: Boolean,
                 attachments: Map<String, JSONObject> = emptyMap(), images: List<String> = emptyList()): JSONArray {
        val prompt = if (chinese) "你是 Tapilot 的对话助手。你负责理解并维护整段对话的上下文，直接回答无需手机操作的问题。需要读取或操作手机时，调用 operate_phone，给它一条具体、完整的操作指令，并给出执行后能从手机最终画面确认的 success_condition。用户明确要求手机操作时，即使还要求只回复一个答案，也必须先调用工具完成操作，再按要求回复。不能因为知道答案或之前操作过，就跳过本次操作。Midscene 只负责执行和检查；不要把聊天记录、角色设定或回复用户的要求传给它。根据工具返回的真实结果向用户回复，不要声称未执行或未验证的操作已经完成。工具失败时可调整指令重试，无法完成则如实说明。"
            else "You are Tapilot's conversation assistant. Own the conversation context and answer directly when phone access is unnecessary. Call operate_phone with a concrete self-contained instruction and a success_condition observable on the final screen. When the user explicitly requests phone actions, perform them through the tool before replying, even if they also request only a short answer. Knowing the answer or having operated previously does not replace a new requested action. Midscene only executes and checks; never pass chat history or response-writing directions. Report actual results and never claim unverified success."
        val list = JSONArray().put(JSONObject().put("role", "system").put("content", prompt))
        history.sortedBy { it.optLong("createdAt") }.filter {
            it.optString("kind", "task") == "task" && it.optString("reply").isNotBlank() && it.optString("status") != "running"
        }.forEach { entry ->
            val file = attachments[entry.getString("id")]
            val text = entry.optString("source") + if (file?.optString("text").isNullOrBlank()) "" else "\n\n文件内容：\n${file!!.optString("text").take(30_000)}"
            list.put(user(text, file?.optJSONArray("images")?.strings().orEmpty()))
            list.put(JSONObject().put("role", "assistant").put("content", entry.getString("reply")))
        }
        return list.put(user(source, images))
    }
    fun run(messages: JSONArray, stopped: () -> Boolean, complete: (JSONArray) -> JSONObject,
            phone: (String, String) -> PhoneResult): AgentReply {
        var count = 0
        var last: PhoneResult? = null
        fun check() { check(!stopped()) { "任务已停止" } }
        repeat(8) {
            check()
            val message = complete(messages).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?: error("模型未返回消息")
            check()
            val calls = message.optJSONArray("tool_calls") ?: JSONArray()
            if (calls.length() == 0) {
                val reply = message.optString("content").trim()
                check(reply.isNotBlank() && reply != "null") { "模型返回了空回复" }
                return AgentReply(reply, last)
            }
            check(count + calls.length() <= 4) { "已达到手机操作次数限制" }
            messages.put(JSONObject().put("role", "assistant").put("content", message.opt("content") ?: JSONObject.NULL).put("tool_calls", calls))
            for (i in 0 until calls.length()) {
                check(); count++
                val call = calls.getJSONObject(i)
                val outcome = runCatching {
                    val function = call.getJSONObject("function")
                    require(function.getString("name") == "operate_phone") { "未知工具" }
                    val args = JSONObject(function.getString("arguments"))
                    val instruction = args.getString("instruction").trim()
                    val condition = args.getString("success_condition").trim()
                    require(instruction.isNotBlank() && condition.isNotBlank()) { "手机指令和成功条件不能为空" }
                    phone(instruction, condition)
                }.getOrElse { check(); PhoneResult(false, it.message ?: "手机操作失败") }
                check(); last = outcome
                messages.put(JSONObject().put("role", "tool").put("tool_call_id", call.getString("id")).put("content", outcome.json().toString()))
            }
        }
        error("已达到对话轮次限制")
    }
    fun verifiedTask(instruction: String, condition: String) = JSONObject().put("cases", JSONArray().put(
        JSONObject().put("name", "Tapilot verified phone operation").put("steps", JSONArray()
            .put(JSONObject().put("aiAct", JSONObject().put("prompt", instruction)))
            .put(JSONObject().put("aiAssert", JSONObject().put("prompt", condition)))))).toString()
    private fun user(text: String, images: List<String>): JSONObject {
        val content: Any = if (images.isEmpty()) text else JSONArray().put(JSONObject().put("type", "text").put("text", text)).apply {
            images.forEach { put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", it))) }
        }
        return JSONObject().put("role", "user").put("content", content)
    }
}

internal fun JSONArray.strings() = List(length()) { getString(it) }
internal fun JSONArray.objects() = List(length()) { getJSONObject(it) }
