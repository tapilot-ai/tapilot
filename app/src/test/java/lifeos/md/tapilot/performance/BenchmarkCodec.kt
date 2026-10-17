package lifeos.md.tapilot.performance

import lifeos.md.tapilot.chatgpt.ChatGptResponses
import lifeos.md.tapilot.conversation.NativeConversationAgent
import lifeos.md.tapilot.conversation.PhoneOperationRoute
import org.json.JSONObject

/** Desktop benchmark only: use the actual Android policies, not a JS reimplementation. */
object BenchmarkCodec {
    @JvmStatic fun main(args: Array<String>) {
        println(JSONObject().put("ready", true))
        generateSequence(::readLine).forEach { line ->
            val request = JSONObject(line)
            val reply = JSONObject().put("id", request.getString("id"))
            try {
                val p = request.getJSONObject("params")
                val result: Any = when (request.getString("method")) {
                    "messages" -> NativeConversationAgent.messages(emptyList(), p.getString("source"), true)
                    "modelBody" -> ChatGptResponses.body(p.getString("model"), p.getJSONArray("messages"), p.getJSONArray("tools"))
                    "modelDecode" -> ChatGptResponses.decode(p)
                    "findApps" -> PhoneOperationRoute.findApps(p.getJSONArray("apps"), p.getString("query"))
                    "verifiedTask" -> NativeConversationAgent.verifiedTask(p.getString("instruction"), p.getString("success_condition"),
                        p.getString("mode"), PhoneOperationRoute.resolveApp(p.getJSONArray("apps"), p.getString("app")))
                    else -> error("Unknown benchmark codec method")
                }
                reply.put("ok", true).put("result", result)
            } catch (error: Exception) {
                // No input, response body or credentials in diagnostics.
                reply.put("ok", false).put("error", error.javaClass.simpleName)
            }
            println(reply)
        }
    }
}
