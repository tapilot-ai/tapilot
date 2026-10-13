package lifeos.md.tapilot.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReplyContentTest {
    @Test fun interruptedTaskRecordsCollapseWithoutLosingProseOrEscapedLogs() {
        val records = JSONArray().put(JSONObject().put("instruction", "打开飞书").put("success", false)
            .put("result", "Node aiAssert failed:\n截图中没有飞书图标 [home] {screen}"))
        val prefix = "用户已打断此轮，不能当作任务完成。已返回的手机操作记录："
        val parts = replyParts(prefix + records + "\n请重试。")
        assertEquals(3, parts.size)
        assertEquals(prefix, parts[0].text)
        assertEquals(1, parts[1].operationCount)
        assertEquals(records.toString(), parts[1].text)
        assertEquals(records.toString(), JSONArray(parts[1].formattedJson).toString())
        assertEquals("\n请重试。", parts[2].text)
    }
    @Test fun supportsNestedJsonAndMultiplePayloadsWithQuotedDelimiters() {
        val objectText = JSONObject().put("nested", JSONArray().put(JSONObject().put("text", "quote \" and \\ [ }"))).toString()
        val parts = replyParts("结果：$objectText\n另一项：[1,true,null]")
        assertEquals(4, parts.size)
        assertEquals(objectText, parts[1].text)
        assertEquals("[1,true,null]", parts[3].text)
        assertNull(parts[1].operationCount)
    }
    @Test fun fencedJsonDoesNotLeaveFenceMarkersInProse() {
        val parts = replyParts("说明\n```json\n{\"ok\":true}\n```\n后续说明")
        assertEquals(3, parts.size)
        assertEquals("说明\n", parts[0].text)
        assertEquals("{\"ok\":true}", parts[1].text)
        assertEquals("\n后续说明", parts[2].text)
    }
    @Test fun ordinaryProseAndBrokenPayloadsRemainVisible() {
        listOf("普通回复，没有操作记录。", "[点击这里] 和 [first]", "记录：[{\"result\":\"未结束", "记录：{\"bad\":]}").forEach { text ->
            val parts = replyParts(text)
            assertEquals(text, parts.joinToString("") { it.text })
            assertTrue(parts.all { it.formattedJson == null })
        }
    }
}
