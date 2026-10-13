package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TaskFeedbackTest {
    private fun stopped() = JSONObject().put("status", "stopped").put("detail", "任务已停止")
    private fun operation(success: Boolean, result: String) = JSONObject().put("success", success).put("result", result)

    @Test fun historicalStopShowsSavedAssertionAndInterruptedWorkWithoutInventingCause() {
        val task = stopped().put("currentInstruction", "检查其他短信分类")
            .put("completedOperations", JSONArray().put(operation(false, "Node aiAssert failed\nReason: 信息应用显示没有对话")))
        val text = TaskFeedback.response(task, true)
        assertTrue(text.contains("未保存停止原因"))
        assertTrue(text.contains("检查其他短信分类"))
        assertTrue(text.contains("信息应用显示没有对话"))
        assertFalse(text.contains("Node aiAssert"))
        assertFalse(text.contains("按你的要求"))
    }
    @Test fun panelCancellationAndUserCancellationAreDistinct() {
        assertTrue(TaskFeedback.response(stopped().put("stopReason", "panel"), true).contains("打开对话面板"))
        assertTrue(TaskFeedback.response(stopped().put("stopReason", "user"), false).contains("at your request"))
    }
    @Test fun recoveryAndPendingInputReasonsSurviveRendering() {
        assertEquals("应用中断，进度已保存", TaskFeedback.response(stopped().put("detail", "应用中断，进度已保存"), true))
        assertEquals("补充消息已保存，请重试或继续", TaskFeedback.response(stopped().put("detail", "补充消息已保存，请重试或继续"), true))
    }
    @Test fun laterSuccessfulOperationDoesNotPresentEarlierErrorAsCurrentFailure() {
        val task = stopped().put("stopReason", "user").put("completedOperations", JSONArray()
            .put(operation(false, "old failure")).put(operation(true, "recovered")))
        assertFalse(TaskFeedback.response(task, false).contains("old failure"))
    }
    @Test fun failurePreservesTransportErrorAndSuccessPreservesReply() {
        val task = JSONObject().put("status", "failed").put("detail", "HTTP 401: invalid credential")
        assertEquals("HTTP 401: invalid credential", TaskFeedback.response(task, false))
        task.put("status", "succeeded").put("reply", "完成")
        assertEquals("完成", TaskFeedback.response(task, true))
    }
}
