package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AgentLimitsTest {
    private fun call() = JSONObject().put("choices", JSONArray().put(JSONObject().put("message",
        JSONObject().put("tool_calls", JSONArray().put(JSONObject().put("id", "op").put("function",
            JSONObject().put("name", "operate_phone").put("arguments", """{"instruction":"打开计算器","success_condition":"显示键盘"}""")))))))
    private fun answer() = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", "完成"))))

    @Test fun defaultsAndDisabledValuesSurviveStorage() {
        assertEquals(AgentLimits(100, 100), AgentLimits())
        assertEquals(AgentLimits(), AgentLimits.from(JSONObject()))
        val settings = JSONObject().put("agentLimits", AgentLimits(null, 9).json())
        assertEquals(AgentLimits(null, 9), AgentLimits.from(JSONObject(settings.toString())))
        assertEquals(AgentLimits(null, null), AgentLimits.from(JSONObject().put("agentLimits", AgentLimits(null, null).json())))
        assertEquals(AgentLimits(3, 4), AgentLimits.from(JSONObject().put("agentLimits", AgentLimits(3, 4).json())))
    }
    @Test fun malformedNumbersUseDefaults() {
        for (value in listOf(-1, 2.5, "off", 2147483648L)) {
            assertEquals(AgentLimits(), AgentLimits.from(JSONObject().put("agentLimits", JSONObject().put("phoneOperations", value).put("modelTurns", value))))
        }
    }
    @Test fun customPhoneLimitIsFreshForEveryRetry() {
        repeat(2) {
            var operated = 0
            val error = assertThrows(IllegalStateException::class.java) {
                NativeConversationAgent.run(JSONArray(), { false }, { call() }, { _, _ ->
                    operated++; PhoneResult(true, "完成")
                }, AgentLimits(2, null))
            }
            assertEquals(2, operated)
            assertTrue(error.message!!.contains("手机操作上限（2"))
        }
    }
    @Test fun customModelLimitStopsBeforeNextRequest() {
        var requests = 0
        val error = assertThrows(IllegalStateException::class.java) {
            NativeConversationAgent.run(JSONArray(), { false }, { requests++; call() }, { _, _ -> PhoneResult(true, "完成") }, AgentLimits(null, 2))
        }
        assertEquals(2, requests)
        assertTrue(error.message!!.contains("对话轮次上限（2"))
    }
    @Test fun disabledLimitsAllowMoreThanDefaultCeilings() {
        var requests = 0
        var operations = 0
        val result = NativeConversationAgent.run(JSONArray(), { false }, { if (requests++ < 120) call() else answer() },
            { _, _ -> operations++; PhoneResult(true, "完成") }, AgentLimits(null, null))
        assertEquals(120, operations)
        assertEquals("完成", result.reply)
    }
    @Test fun unlimitedTaskStillStops() {
        var stopped = false
        var operations = 0
        assertThrows(IllegalStateException::class.java) {
            NativeConversationAgent.run(JSONArray(), { stopped }, { call() }, { _, _ ->
                operations++; stopped = true; PhoneResult(true, "完成")
            }, AgentLimits(null, null))
        }
        assertEquals(1, operations)
    }
}
