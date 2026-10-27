package lifeos.md.tapilot.chat

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray

class ChatStateTest {
    @Test fun preservesConversationAndRunnerReportWhenReadingEngineState() {
        val state = ChatState.parse("""{"protocolVersion":1,"ready":true,"theme":"dark","language":"en",
            "activeConversationId":"legacy","draft":"draft","draftEpoch":4,"configured":true,
            "messages":[{"id":"1-user","role":"user","text":"hello","status":"failed"},
            {"id":"1-assistant","role":"assistant","text":"error","status":"failed","reportId":"runner"}],
            "conversations":[{"id":"legacy","title":"Previous"}],"attachment":null,"confirmation":null}""")
        assertTrue(state.ready)
        assertTrue(state.dark)
        assertFalse(state.chinese)
        assertEquals("legacy", state.activeConversationId)
        assertEquals("runner", state.messages.last().reportId)
        assertEquals("draft", state.draft)
        assertEquals(4, state.draftEpoch)
        assertNull(state.attachment)
        assertNull(state.confirmation)
    }

    @Test fun readsAttachmentsAndExplicitOperationConfirmation() {
        val state = ChatState.parse("""{"protocolVersion":1,"messages":[],"conversations":[],
            "attachment":{"name":"sample.pdf","kind":"pdf","preview":"data:image/jpeg;base64,abc"},
            "confirmation":"打开设置并确认搜索框"}""")
        assertEquals("sample.pdf", state.attachment?.name)
        assertEquals("打开设置并确认搜索框", state.confirmation)
    }

    @Test fun rejectsUnsupportedBridgeVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatState.parse("""{"protocolVersion":2,"messages":[],"conversations":[]}""")
        }
    }

    @Test fun retainsEveryAgentReportInOrderInsteadOfOnlyTheFinalOperation() {
        val reports = ChatReport.from(JSONArray("""[
            {"reportId":"first","kind":"agent","instruction":"Read Android version","title":"Agent report"},
            {"reportId":"summary-1","kind":"test-run","title":"Summary"},
            {"reportId":"second","kind":"agent","instruction":"Read storage"},
            {"reportId":"third","kind":"agent","title":"Brightness"},
            {"reportId":"summary-3","kind":"test-run"}
        ]"""))
        assertEquals(listOf("first", "second", "third"), reports.map { it.id })
        assertEquals(listOf("Read Android version", "Read storage", "Brightness"), reports.map { it.title })
    }

    @Test fun fallsBackToRunnerReportsAndDoesNotOfferBlankOrDuplicateIds() {
        assertEquals(emptyList<ChatReport>(), ChatReport.from(null))
        val reports = ChatReport.from(JSONArray("""[
            {"reportId":"","kind":"agent"},
            {"reportId":"runner","kind":"test-run","title":"Failed before agent setup"},
            {"reportId":"runner","kind":"test-run"}
        ]"""))
        assertEquals(listOf(ChatReport("runner", "Failed before agent setup")), reports)
    }
}
