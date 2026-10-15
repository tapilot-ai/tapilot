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
            assertTrue(result.result.contains("不要重复"))
            assertFalse(result.result.contains("Android 14"))
        }
        assertFalse(PhoneResult.fromRun(JSONObject().put("exitCode", 1).put("finalResponse", "stale observation")).success)
        assertEquals(PhoneResult(true, "ColorOS 14.0; Android unknown"), PhoneResult.fromRun(
            JSONObject().put("exitCode", 0).put("finalResponse", "ColorOS 14.0; Android unknown")))
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

    @Test fun generatedTaskKeepsUntrustedTextAsData() {
        val source = NativeConversationAgent.verifiedTask("点击\n\"删除\"", "显示：确认")
        val steps = JSONObject(source).getJSONArray("cases").getJSONObject(0).getJSONArray("steps")
        assertEquals("点击\n\"删除\"", steps.getJSONObject(0).getJSONObject("aiAct").getString("prompt"))
        assertEquals("显示：确认", steps.getJSONObject(1).getJSONObject("aiAssert").getString("prompt"))
        assertEquals(3, steps.length())
        val observation = steps.getJSONObject(2).getJSONObject("aiAsk").getString("prompt")
        assertTrue(observation.contains("current screenshot"))
        assertTrue(observation.contains("unknown"))
        assertTrue(observation.contains(JSONObject.quote("点击\n\"删除\"")))
        assertFalse(observation.contains("显示：确认"))
    }
}
