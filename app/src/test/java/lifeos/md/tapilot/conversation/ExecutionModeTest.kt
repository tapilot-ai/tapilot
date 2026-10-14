package lifeos.md.tapilot.conversation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExecutionModeTest {
    @Test fun oldSettingsRetainTheirConfirmationBehavior() {
        assertEquals(ExecutionMode.YOLO, ExecutionMode.from(JSONObject()))
        assertEquals(ExecutionMode.YOLO, ExecutionMode.from(ConversationStore.empty()))
        assertEquals(ExecutionMode.YOLO, ExecutionMode.from(ConversationStore.importLegacy(JSONObject())))
        assertEquals(ExecutionMode.CRITICAL, ExecutionMode.from(ConversationStore.importLegacy(JSONObject().put("tapilot.confirmEveryStep", "false"))))
        assertEquals(ExecutionMode.ALWAYS, ExecutionMode.from(ConversationStore.importLegacy(JSONObject().put("tapilot.confirmEveryStep", "true"))))
        assertEquals(ExecutionMode.CRITICAL, ExecutionMode.from(JSONObject().put("confirmEveryStep", false)))
        assertEquals(ExecutionMode.ALWAYS, ExecutionMode.from(JSONObject().put("confirmEveryStep", true)))
        assertEquals(ExecutionMode.ALWAYS, ExecutionMode.from(JSONObject().put("executionMode", "unknown").put("confirmEveryStep", true)))
    }

    @Test fun yoloSkipsConfirmationForEveryPhoneCallIncludingCriticalOperations() {
        val mode = ExecutionMode.from(JSONObject().put("executionMode", "yolo").put("confirmEveryStep", true))
        listOf("打开计算器", "发送消息", "删除测试文件", "Submit a form").forEach { assertFalse(mode.requiresConfirmation(it)) }
        assertEquals(mode, ExecutionMode.from(JSONObject(JSONObject().put("executionMode", mode.wireValue).toString())))
    }

    @Test fun criticalAndAlwaysRemainDistinct() {
        assertFalse(ExecutionMode.CRITICAL.requiresConfirmation("打开计算器"))
        assertFalse(ExecutionMode.CRITICAL.requiresConfirmation("Open display settings"))
        assertTrue(ExecutionMode.CRITICAL.requiresConfirmation("发送消息"))
        assertTrue(ExecutionMode.CRITICAL.requiresConfirmation("DELETE the test file"))
        assertTrue(ExecutionMode.ALWAYS.requiresConfirmation("打开计算器"))
    }
}
