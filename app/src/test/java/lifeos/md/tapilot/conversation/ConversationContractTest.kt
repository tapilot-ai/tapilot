package lifeos.md.tapilot.conversation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConversationContractTest {
    private val fixture = JSONObject(javaClass.getResource("/fixtures/conversation.json")!!.readText())

    @Test fun phoneOperationsAgreeWithNodeContract() {
        fixture.getJSONArray("valid").objects().forEach { test ->
            val actual = PhoneOperation.from(test.getJSONObject("input"))
            val expected = test.getJSONObject("expected")
            assertEquals(expected.getString("instruction"), actual.instruction)
            assertEquals(expected.getString("success_condition"), actual.successCondition)
            assertEquals(expected.getString("mode"), actual.mode.wireValue)
            assertEquals(expected.getString("app"), actual.app)
            assertEquals(expected.getString("effort"), actual.effort.wireValue)
        }
        fixture.getJSONArray("invalid").objects().forEach {
            assertThrows(IllegalArgumentException::class.java) { PhoneOperation.from(it) }
        }
    }
    @Test fun storedFailuresRenderInTheCurrentLanguageWithoutTranslatingUnknownDiagnostics() {
        fixture.getJSONArray("errors").objects().forEach { error ->
            val entry = JSONObject().put("status", "failed").put("errorCode", error.getString("code")).put("detail", "Old localized message")
            assertEquals(error.getString("en"), TaskFeedback.response(entry, false))
            assertEquals(error.getString("zh"), TaskFeedback.response(entry, true))
        }
        val unknown = JSONObject().put("status", "failed").put("errorCode", "provider_specific").put("detail", "Provider diagnostic")
        assertEquals("Provider diagnostic", TaskFeedback.response(unknown, false))
    }
    @Test fun nestedFailuresKeepTheirCode() {
        val failure = TaskFailure("request_timeout")
        assertSame(failure, TaskFailure.find(java.util.concurrent.CompletionException(failure)))
        assertEquals("task_stopped", (TaskFailure.from(JSONObject().put("errorCode", "task_stopped")) as TaskFailure).code)
    }
}
