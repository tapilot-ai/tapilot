package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TaskHistorySnapshotTest {
    private fun entry(id: String, conversation: String, time: Long) = JSONObject()
        .put("id", id).put("conversationId", conversation).put("createdAt", time).put("source", id)

    @Test fun newConversationDoesNotCopyOrMutateOtherConversations() {
        val other = entry("old", "other", 1).put("agentContext", JSONArray().put(JSONObject().put("text", "retained")))
        val snapshot = TaskHistorySnapshot.capture(listOf(other), "new", "read screen", "task", null, null)
        assertNull(snapshot.retry)
        assertTrue(snapshot.prior.isEmpty())
        assertEquals("retained", other.getJSONArray("agentContext").getJSONObject(0).getString("text"))
    }

    @Test fun retryCutoffAndAmendmentsAreDetachedFromUiMutations() {
        val older = entry("older", "current", 1)
        val retry = entry("retry", "current", 2).put("phase", "paused")
            .put("agentContext", JSONArray().put(JSONObject().put("inputId", "consumed")))
        val pending = entry("pending", "current", 3).put("kind", "instruction").put("taskId", "retry")
        val consumed = entry("consumed", "current", 3).put("kind", "instruction").put("taskId", "retry")
        val history = listOf(older, retry, pending, consumed, entry("newer", "current", 4), entry("unrelated", "other", 1))
        val snapshot = TaskHistorySnapshot.capture(history, "current", "retry", "task", "retry", null)
        older.put("source", "changed"); pending.put("source", "changed")
        retry.getJSONArray("agentContext").getJSONObject(0).put("inputId", "changed")
        assertEquals(listOf("older"), snapshot.prior.map { it.getString("id") })
        assertEquals("older", snapshot.prior.single().getString("source"))
        assertEquals("consumed", snapshot.retry!!.getJSONArray("agentContext").getJSONObject(0).getString("inputId"))
        assertEquals(1, snapshot.pendingInputs.length())
        assertEquals("pending", snapshot.pendingInputs.getJSONObject(0).getString("text"))
    }

    @Test fun continueRetainsPreviousTaskAndLegacyConversationIdentity() {
        val previous = JSONObject().put("id", "legacy").put("source", "read screen").put("createdAt", 1).put("phase", "paused")
        val snapshot = TaskHistorySnapshot.capture(listOf(previous), "legacy", "continue", "task", null, "legacy")
        assertEquals("legacy", snapshot.retry!!.getString("id"))
        assertEquals(1, snapshot.prior.size)
        assertNotSame(previous, snapshot.prior.single())
    }
}
