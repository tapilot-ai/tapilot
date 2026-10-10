package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeConversationAgentTest {
    private fun answer(text: String) = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", text))))
    private fun calls(count: Int = 1) = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("tool_calls", JSONArray().apply {
        repeat(count) { put(JSONObject().put("id", "tool-$it").put("function", JSONObject().put("name", "operate_phone").put("arguments", JSONObject().put("instruction", "打开计算器").put("success_condition", "显示计算器键盘").toString()))) }
    }))))
    @Test fun contextKeepsAttachmentAndOrder() {
        val history = listOf(JSONObject().put("id", "e1").put("createdAt", 1).put("source", "读文件").put("reply", "TP1047").put("status", "succeeded"))
        val messages = NativeConversationAgent.messages(history, "是什么编号", true, mapOf("e1" to JSONObject().put("text", "编号TP1047")))
        assertEquals(4, messages.length())
        assertTrue(messages.getJSONObject(1).getString("content").contains("编号TP1047"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
    }
    @Test fun toolReceivesConcreteInstructionAndConditionOnly() {
        var requests = 0
        var lastMessages = JSONArray()
        val result = NativeConversationAgent.run(NativeConversationAgent.messages(emptyList(), "帮我打开计算器", true), { false }, {
            requests++; lastMessages = it
            if (requests == 1) calls() else answer("已打开")
        }, { instruction, condition ->
            assertEquals("打开计算器", instruction); assertEquals("显示计算器键盘", condition)
            PhoneResult(true, "真实成功")
        })
        assertEquals("已打开", result.reply)
        assertTrue(lastMessages.getJSONObject(3).getString("content").contains("真实成功"))
    }
    @Test fun cancellationAfterModelResponsePreventsTool() {
        var stopped = false
        var operations = 0
        assertThrows(IllegalStateException::class.java) {
            NativeConversationAgent.run(JSONArray(), { stopped }, { stopped = true; calls() }, { _, _ -> operations++; PhoneResult(true, "") })
        }
        assertEquals(0, operations)
    }
    @Test fun failedToolCannotBecomeSuccessfulOutcome() {
        var turn = 0
        val reply = NativeConversationAgent.run(JSONArray(), { false }, { if (turn++ == 0) calls() else answer("已完成") }, { _, _ -> PhoneResult(false, "最终画面不符") })
        assertFalse(reply.lastPhoneResult!!.success)
    }
    @Test fun toolLimitStopsBeforeOperating() {
        var count = 0
        assertThrows(IllegalStateException::class.java) {
            NativeConversationAgent.run(JSONArray(), { false }, { calls(NativeConversationAgent.MAX_PHONE_OPERATIONS + 1) }, { _, _ -> count++; PhoneResult(true, "") })
        }
        assertEquals(0, count)
    }
    @Test fun longTaskCanCompleteAfterMoreThanEightModelTurns() {
        var requests = 0
        var operations = 0
        val reply = NativeConversationAgent.run(JSONArray(), { false }, {
            if (requests++ < NativeConversationAgent.MAX_PHONE_OPERATIONS) calls() else answer("长任务已完成")
        }, { _, _ -> operations++; PhoneResult(true, "成功") }, AgentLimits(modelTurns = 101))
        assertEquals(NativeConversationAgent.MAX_PHONE_OPERATIONS, operations)
        assertEquals(NativeConversationAgent.MAX_PHONE_OPERATIONS + 1, requests)
        assertEquals("长任务已完成", reply.reply)
    }
    @Test fun batchCanExceedOldFourOperationLimit() {
        var requests = 0
        var operations = 0
        val reply = NativeConversationAgent.run(JSONArray(), { false }, {
            if (requests++ == 0) calls(5) else answer("已完成")
        }, { _, _ -> operations++; PhoneResult(true, "成功") })
        assertEquals(5, operations)
        assertEquals("已完成", reply.reply)
    }
    @Test fun repeatedToolRequestsStopAtEmergencyCeiling() {
        var operations = 0
        val error = assertThrows(IllegalStateException::class.java) {
            NativeConversationAgent.run(JSONArray(), { false }, { calls() }, { _, _ ->
                operations++; PhoneResult(true, "成功")
            })
        }
        assertEquals(NativeConversationAgent.MAX_PHONE_OPERATIONS, operations)
        assertTrue(error.message!!.contains("100"))
    }
    @Test fun generatedTaskKeepsUntrustedTextAsData() {
        val source = NativeConversationAgent.verifiedTask("点击\n\"删除\"", "显示：确认")
        val steps = JSONObject(source).getJSONArray("cases").getJSONObject(0).getJSONArray("steps")
        assertEquals("点击\n\"删除\"", steps.getJSONObject(0).getJSONObject("aiAct").getString("prompt"))
        assertEquals("显示：确认", steps.getJSONObject(1).getJSONObject("aiAssert").getString("prompt"))
    }
}
