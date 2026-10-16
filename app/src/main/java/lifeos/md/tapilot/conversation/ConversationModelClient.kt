package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.AppText.text as appText
import lifeos.md.tapilot.chatgpt.ChatGptResponses
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

internal data class ModelProfile(val baseUrl: String, val modelName: String, val modelFamily: String) {
    fun json() = JSONObject().put("baseUrl", baseUrl).put("modelName", modelName).put("modelFamily", modelFamily)
    companion object {
        fun from(value: JSONObject) = ModelProfile(value.getString("baseUrl"), value.getString("modelName"), value.getString("modelFamily"))
    }
}

/** Provider-specific encoding and replay isolation belong outside conversation/UI state. */
internal class ConversationModelClient(
    private val accessToken: (String) -> String,
    private val request: (JSONObject, String, AtomicBoolean) -> JSONObject,
    private val apiFailure: (JSONObject, String) -> Unit = { _, _ -> },
) {
    fun chatGpt(account: String, model: String, messages: JSONArray, tools: JSONArray, session: String, stopped: AtomicBoolean): JSONObject {
        check(!stopped.get()) { appText("任务已停止", "Task stopped") }
        val token = accessToken(account)
        val history = JSONArray(messages.toString())
        for (i in 0 until history.length()) history.getJSONObject(i).let { message ->
            if (message.optString("tapilotResponsesAccount") != account) message.remove("tapilotResponsesOutput")
        }
        val params = ChatGptResponses.params(model, token, history, tools)
        return ChatGptResponses.decode(request(params, session, stopped)).put("tapilotResponsesAccount", account)
    }
    fun complete(model: ModelProfile, key: String, messages: JSONArray, tools: JSONArray, session: String, stopped: AtomicBoolean): JSONObject {
        val response = request(NativeModelRequest.params(model.json(), key, messages, tools), session, stopped)
        if (response.optInt("status") !in 200..299) apiFailure(response, key)
        return NativeModelRequest.decode(response)
    }
}
