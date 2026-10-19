package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeConversationAgentTest {
    @Test fun missingObservationCannotBecomeSuccessFromExecutionLogs() {
        for (value in listOf(JSONObject.NULL, "", "  ")) {
            val result = PhoneResult.fromRun(JSONObject().put("exitCode", 0).put("finalResponse", value)
                .put("output", JSONArray().put("Android 14; operation completed")))
            assertFalse(result.success)
            assertTrue(result.result.contains("before repeating"))
            assertFalse(result.result.contains("Android 14"))
        }
        assertFalse(PhoneResult.fromRun(JSONObject().put("exitCode", 1).put("finalResponse", "stale observation")).success)
        assertEquals(PhoneResult(true, "ColorOS 14.0; Android unknown"), PhoneResult.fromRun(
            JSONObject().put("exitCode", 0).put("finalResponse", JSONObject().put("success", true).put("observation", "ColorOS 14.0; Android unknown").toString())))
    }
    @Test fun verificationMustContainStrictBooleanAndIndependentObservation() {
        val malformed = listOf("operation completed", "{}", "null", "[]", "{broken",
            "{\"success\":\"true\",\"observation\":\"visible\"}",
            "{\"success\":1,\"observation\":\"visible\"}",
            "{\"success\":true,\"observation\":false}",
            "{\"success\":true,\"observation\":\"  \"}",
            "{\"success\":true}", "{\"observation\":\"visible\"}")
        for (value in malformed) assertFalse(value, PhoneResult.fromRun(
            JSONObject().put("exitCode", 0).put("finalResponse", value)).success)
    }

    @Test fun negativeVerificationRemainsFailureEvenWhenRunnerSucceeds() {
        val observed = JSONObject().put("success", false).put("observation", "Weather reply is not visible")
        assertEquals(PhoneResult(false, "Weather reply is not visible"), PhoneResult.fromRun(
            JSONObject().put("exitCode", 0).put("finalResponse", observed.toString())))
        observed.put("success", true)
        assertEquals(PhoneResult(false, "capture failed"), PhoneResult.fromRun(
            JSONObject().put("exitCode", 1).put("failure", "capture failed").put("finalResponse", observed.toString())))
    }

    @Test fun contextKeepsAttachmentAndOrder() {
        val history = listOf(JSONObject().put("id", "e1").put("createdAt", 1).put("source", "读文件").put("reply", "TP1047").put("status", "succeeded"))
        val messages = NativeConversationAgent.messages(history, "是什么编号", true, mapOf("e1" to JSONObject().put("text", "编号TP1047")))
        assertEquals(4, messages.length())
        assertTrue(messages.getJSONObject(1).getString("content").contains("编号TP1047"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
    }
    @Test fun interruptedTurnKeepsRequestAndVerifiedActionsWithoutClaimingCompletion() {
        val interrupted = JSONObject().put("id", "old").put("createdAt", 1).put("source", "打开计算器然后计算 2+2")
            .put("status", "stopped").put("completedOperations", JSONArray().put(JSONObject()
                .put("instruction", "打开计算器").put("success", true).put("result", "计算器已打开")))
        val messages = NativeConversationAgent.messages(listOf(interrupted), "不要计算了，返回桌面", true)
        assertEquals(4, messages.length())
        assertEquals("打开计算器然后计算 2+2", messages.getJSONObject(1).getString("content"))
        val context = messages.getJSONObject(2).getString("content")
        assertTrue(context.contains("用户已打断")); assertTrue(context.contains("不能当作任务完成"))
        assertTrue(context.contains("计算器已打开"))
        assertEquals("不要计算了，返回桌面", messages.getJSONObject(3).getString("content"))
        assertFalse(messages.toString().contains("tool_calls"))
    }

    @Test fun interruptedTurnWithoutReturnedActionsDoesNotInventProgress() {
        val interrupted = JSONObject().put("id", "old").put("createdAt", 1).put("source", "发送消息").put("status", "stopped")
        val messages = NativeConversationAgent.messages(listOf(interrupted), "先别发", false)
        assertTrue(messages.getJSONObject(2).getString("content").endsWith("[]"))
        assertEquals("先别发", messages.getJSONObject(3).getString("content"))
    }

    @Test fun launchedAppContextAvoidsDuplicateLaunchWithoutRemovingVerification() {
        val instruction = "Open calculator and compute 7 * 8"
        val steps = JSONObject(NativeConversationAgent.verifiedTask(instruction, "56 is visible", "act", "com.example/.Calculator"))
            .getJSONArray("cases").getJSONObject(0).getJSONArray("steps")
        assertEquals(3, steps.length())
        assertEquals("com.example/.Calculator", steps.getJSONObject(0).getJSONObject("launch").getString("uri"))
        val prompt = steps.getJSONObject(1).getJSONObject("aiAct").getString("prompt")
        assertTrue(prompt.contains("already returned successfully"))
        assertTrue(prompt.contains("recover as needed"))
        assertTrue(prompt.contains("Perform the explicitly requested interactions in this run"))
        assertTrue(prompt.contains(JSONObject.quote(instruction)))
        assertTrue(steps.getJSONObject(2).has("verifyPhone"))
        val observe = JSONObject(NativeConversationAgent.verifiedTask(instruction, "56 is visible", "observe", "com.example/.Calculator"))
            .getJSONArray("cases").getJSONObject(0).getJSONArray("steps")
        assertEquals(2, observe.length())
        assertTrue(observe.getJSONObject(1).has("verifyPhone"))
    }

    @Test fun generatedTaskKeepsUntrustedTextAsData() {
        val source = NativeConversationAgent.verifiedTask("点击\n\"删除\"", "显示：确认")
        val steps = JSONObject(source).getJSONArray("cases").getJSONObject(0).getJSONArray("steps")
        val action = steps.getJSONObject(0).getJSONObject("aiAct").getString("prompt")
        assertTrue(action.contains(JSONObject.quote("点击\n\"删除\"")))
        assertTrue(action.contains(JSONObject.quote("显示：确认")))
        assertEquals(2, steps.length())
        val query = steps.getJSONObject(1).getJSONObject("verifyPhone")
        assertEquals("点击\n\"删除\"", query.getString("instruction"))
        assertEquals("显示：确认", query.getString("condition"))
    }
}
