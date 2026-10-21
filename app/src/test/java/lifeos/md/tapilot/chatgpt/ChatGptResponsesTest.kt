package lifeos.md.tapilot.chatgpt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ChatGptResponsesTest {
    private fun response(text: String, status: Int = 200) = JSONObject().put("status", status).put("bodyBase64", Base64.getEncoder().encodeToString(text.toByteArray()))
    @Test fun completedItemsArePreservedWhenFinalEventOmitsOutput() {
        val items = """data: {"type":"response.output_item.done","output_index":1,"item":{"type":"message","content":[{"type":"output_text","text":"OK"}]}}

data: {"type":"response.output_item.done","output_index":0,"item":{"type":"reasoning","encrypted_content":"opaque"}}

"""
        assertThrows(IllegalStateException::class.java) { ChatGptResponses.decode(response(items)) }
        val result = ChatGptResponses.decode(response(items + "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n"))
        assertEquals("OK", result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
        assertEquals("opaque", result.getJSONArray("tapilotResponsesOutput").getJSONObject(0).getString("encrypted_content"))
        assertThrows(IllegalStateException::class.java) { ChatGptResponses.decode(response(items + "data: {\"type\":\"error\",\"code\":\"subscription_sharing_usage_limit_exceeded\"}\n\n")) }
    }
    @Test fun requestConvertsSystemImagesToolsAndToolResultsWithoutUnsupportedParameters() {
        val messages = JSONArray("""[{"role":"system","content":"plan"},{"role":"user","content":[{"type":"text","text":"look"},{"type":"image_url","image_url":{"url":"data:image/png;base64,test"}}]},{"role":"assistant","tool_calls":[{"id":"call","function":{"name":"operate_phone","arguments":"{}"}}]},{"role":"tool","tool_call_id":"call","content":"done"}]""")
        val tools = JSONArray("""[{"type":"function","function":{"name":"operate_phone","parameters":{"type":"object"}}}]""")
        val body = ChatGptResponses.body("selected", messages, tools)
        assertFalse(body.getBoolean("parallel_tool_calls"))
        assertFalse(body.getBoolean("store")); assertTrue(body.getBoolean("stream"))
        assertFalse(body.has("max_tokens")); assertFalse(body.has("temperature")); assertFalse(body.has("previous_response_id"))
        val input = body.getJSONArray("input")
        assertEquals("developer", input.getJSONObject(0).getString("role"))
        assertEquals("input_image", input.getJSONObject(1).getJSONArray("content").getJSONObject(1).getString("type"))
        assertEquals("call", input.getJSONObject(2).getString("call_id"))
        assertEquals("function_call_output", input.getJSONObject(3).getString("type"))
        assertEquals("namespace", body.getJSONArray("tools").getJSONObject(0).getString("type"))
    }
    @Test fun completedResponseReturnsTextToolCallsAndOpaqueReasoningForReplay() {
        val output = JSONArray("""[{"type":"reasoning","encrypted_content":"opaque"},{"type":"message","content":[{"type":"output_text","text":"Working"}]},{"type":"function_call","namespace":"tapilot","call_id":"call","name":"operate_phone","arguments":"{}"}]""")
        val event = JSONObject().put("type", "response.completed").put("response", JSONObject().put("status", "completed").put("output", output))
        val result = ChatGptResponses.decode(response("data: $event\n\n"))
        val message = result.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        assertEquals("Working", message.getString("content")); assertEquals("call", message.getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        message.put("tapilotResponsesOutput", result.getJSONArray("tapilotResponsesOutput"))
        assertEquals(output.toString(), ChatGptResponses.body("model", JSONArray().put(message), JSONArray()).getJSONArray("input").toString())
    }
    @Test fun truncatedStreamAndLateUsageFailureNeverReturnPartialSuccess() {
        val delta = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"OK\"}\n\n"
        assertThrows(IllegalStateException::class.java) { ChatGptResponses.decode(response(delta)) }
        val failure = "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n\n"
        val error = assertThrows(IllegalStateException::class.java) { ChatGptResponses.decode(response(delta + failure)) }
        assertTrue(error.message!!.contains("quota exhausted"))
        assertThrows(IllegalStateException::class.java) { ChatGptResponses.decode(response("data: {\"type\":\"response.incomplete\"}\n\n")) }
    }
    @Test fun responseMetricsPreserveActualModelAndOnlyNumericUsage() {
        val event = """data: {"type":"response.completed","response":{"status":"completed","model":"actual-model","usage":{"input_tokens":12,"output_tokens":4,"total_tokens":16,"secret":"never-log","output_tokens_details":{"reasoning_tokens":2}},"output":[{"type":"message","content":[{"type":"output_text","text":"OK"}]}]}}

"""
        val metrics = ChatGptResponses.decode(response(event)).getJSONObject("modelMetrics")
        assertEquals("actual-model", metrics.getString("model"))
        assertEquals(12, metrics.getInt("input_tokens"))
        assertEquals(2, metrics.getInt("reasoning_tokens"))
        assertFalse(metrics.toString().contains("never-log"))
        assertEquals(5, metrics.length())
    }
    @Test fun authorizationFailureAsksForLoginWithoutEchoingResponseSecrets() {
        val error = assertThrows(IllegalStateException::class.java) { ChatGptResponses.decode(response("{\"error\":{\"message\":\"secret-token\"}}", 401)) }
        assertTrue(error.message!!.contains("Sign in again")); assertFalse(error.message!!.contains("secret-token"))
    }
}
