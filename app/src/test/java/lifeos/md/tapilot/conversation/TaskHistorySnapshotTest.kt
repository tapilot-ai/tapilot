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

    @Test fun stalePanelResumeCannotBringAnOldGoalIntoANewConversation() {
        val old = entry("old-task", "old-chat", 1).put("phase", "paused").put("goal", "old goal")
        val amendment = entry("amendment", "old-chat", 2).put("kind", "instruction").put("taskId", "old-task")
        for ((retry, resume) in listOf(null to "old-task", "old-task" to null)) {
            val snapshot = TaskHistorySnapshot.capture(listOf(old, amendment), "new-chat", "a new goal", "task", retry, resume)
            assertNull(snapshot.retry)
            assertTrue(snapshot.prior.isEmpty())
            assertEquals(0, snapshot.pendingInputs.length())
        }
    }

    @Test fun continueRetainsPreviousTaskAndLegacyConversationIdentity() {
        val previous = JSONObject().put("id", "legacy").put("source", "read screen").put("createdAt", 1).put("phase", "paused")
        val snapshot = TaskHistorySnapshot.capture(listOf(previous), "legacy", "continue", "task", null, "legacy")
        assertEquals("legacy", snapshot.retry!!.getString("id"))
        assertEquals(1, snapshot.prior.size)
        assertNotSame(previous, snapshot.prior.single())
    }

    @Test fun explicitAmendmentAfterFailureRetainsOriginalGoalAndCheckpoint() {
        val previous = entry("calculation", "chat", 1).put("phase", "failed")
            .put("goal", "Multiply, add to the result, then divide the total")
            .put("agentContext", JSONArray().put(JSONObject().put("role", "user").put("content", "original request")))
        for (text in listOf("修改要求：最后改为除以4", "Change the requirement: divide the total by four")) {
            val snapshot = TaskHistorySnapshot.capture(listOf(previous), "chat", text, "task", null, null)
            val retry = requireNotNull(snapshot.retry)
            val next = entry("amended", "chat", 2)
            TaskLifecycle.begin(next, retry)
            assertEquals(previous.getString("goal"), next.getString("goal"))
            assertEquals("calculation", next.getString("taskId"))
            assertEquals(previous.getJSONArray("agentContext").toString(), next.getJSONArray("agentContext").toString())
        }
    }

    @Test fun amendmentPrefixDoesNotResumeAnUnrelatedOrCompletedTask() {
        val previous = entry("old", "chat", 1).put("phase", "failed")
        for (text in listOf("新任务：修改要求说明", "解释什么是修改要求", "Change the wallpaper")) {
            assertNull(TaskHistorySnapshot.capture(listOf(previous), "chat", text, "task", null, null).retry)
        }
        assertNull(TaskHistorySnapshot.capture(listOf(previous), "other-chat", "修改要求：继续计算", "task", null, null).retry)
        previous.put("phase", "completed")
        assertNull(TaskHistorySnapshot.capture(listOf(previous), "chat", "修改要求：继续计算", "task", null, null).retry)
    }

    @Test fun retryingAnAmendmentResumesItsOwningTaskAndPendingRequirements() {
        val owner = entry("task", "chat", 1).put("kind", "task").put("phase", "failed")
            .put("goal", "Read the battery level").put("agentContext", JSONArray()
                .put(JSONObject().put("inputId", "consumed").put("content", "Only read; do not change settings")))
        val used = entry("consumed", "chat", 2).put("kind", "instruction").put("taskId", "task")
        val pending = entry("pending", "chat", 3).put("kind", "instruction").put("taskId", "task")
            .put("source", "Only report the percentage")
        val later = entry("later", "chat", 4).put("source", "Unrelated new task")
        for (id in listOf("consumed", "pending")) {
            val snapshot = TaskHistorySnapshot.capture(listOf(owner, used, pending, later), "chat", "retry", "task", id, null)
            assertEquals("task", snapshot.retry!!.getString("id"))
            assertEquals("Read the battery level", snapshot.retry.getString("goal"))
            assertEquals("consumed", snapshot.retry.getJSONArray("agentContext").getJSONObject(0).getString("inputId"))
            assertEquals("pending", snapshot.pendingInputs.getJSONObject(0).getString("id"))
            assertTrue(snapshot.prior.isEmpty())
        }
    }

    @Test fun orphanedOrCrossConversationAmendmentCannotRunAsAnIndependentTask() {
        val amendment = entry("input", "chat", 2).put("kind", "instruction").put("taskId", "missing")
        val foreign = entry("missing", "other-chat", 1).put("kind", "task")
        for (history in listOf(listOf(amendment), listOf(foreign, amendment))) {
            assertThrows(IllegalStateException::class.java) {
                TaskHistorySnapshot.capture(history, "chat", "retry", "task", "input", null)
            }
        }
    }

    @Test fun retryingALateAmendmentRetainsTheCompletedTaskAndUnconsumedInput() {
        val owner = entry("task", "chat", 1).put("kind", "task").put("phase", "completed")
            .put("goal", "Read the battery level")
        val input = entry("input", "chat", 2).put("kind", "instruction").put("taskId", "task")
        val snapshot = TaskHistorySnapshot.capture(listOf(owner, input), "chat", "retry", "task", "input", null)
        assertEquals("task", snapshot.retry!!.getString("kind"))
        assertEquals("Read the battery level", snapshot.retry.getString("goal"))
        assertEquals("input", snapshot.pendingInputs.getJSONObject(0).getString("id"))
    }

    @Test fun missingExplicitTaskDoesNotResumeADifferentWaitingTask() {
        val waiting = entry("waiting", "chat", 1).put("kind", "task").put("phase", "waiting_user")
            .put("goal", "A different pending goal")
        for ((retry, resume) in listOf("missing" to null, null to "missing")) {
            val snapshot = TaskHistorySnapshot.capture(listOf(waiting), "chat", "read screen", "task", retry, resume)
            assertNull(snapshot.retry)
            assertEquals(0, snapshot.pendingInputs.length())
        }
    }

    private fun resume(owner: JSONObject, id: String, history: List<JSONObject>): JSONObject {
        val snapshot = TaskHistorySnapshot.capture(history, "chat", "continue", "task", null, owner.getString("id"))
        return entry(id, "chat", owner.getLong("createdAt") + 10).put("kind", "task").also {
            TaskLifecycle.begin(it, snapshot.retry)
        }
    }

    @Test fun repeatedInterruptedRetriesRetainEveryUnconsumedAmendment() {
        val original = entry("original", "chat", 1).put("kind", "task").put("phase", "failed")
        val update = entry("update", "chat", 2).put("kind", "instruction").put("taskId", "original")
            .put("source", "Do not change any settings")
        val second = resume(original, "second", listOf(original, update)).put("status", "stopped")
        val history = listOf(second, update, original)
        val thirdSnapshot = TaskHistorySnapshot.capture(history, "chat", "continue", "task", null, "second")
        assertEquals(listOf("update"), thirdSnapshot.pendingInputs.objects().map { it.getString("id") })
        val third = resume(second, "third", history).put("status", "stopped")
        // History pruning must not break an input already handed to the current attempt.
        val retained = TaskHistorySnapshot.capture(listOf(third, update), "chat", "continue", "task", null, "third")
        assertEquals("update", retained.pendingInputs.getJSONObject(0).getString("id"))
        assertFalse(original.has("amendmentIds"))
    }

    @Test fun successfulRetrySettlesItsInheritedAmendmentsWithoutTouchingAnotherConversation() {
        val original = entry("original", "chat", 1).put("kind", "task").put("phase", "failed")
        val update = entry("update", "chat", 2).put("kind", "instruction").put("taskId", "original").put("status", "stopped")
        val foreign = entry("foreign", "other-chat", 2).put("kind", "instruction").put("taskId", "original").put("status", "stopped")
        val retry = resume(original, "retry", listOf(original, update, foreign)).put("status", "succeeded")
            .put("agentContext", JSONArray().put(JSONObject().put("inputId", "update")))
        assertTrue(TaskLifecycle.settleInputs(retry, listOf(retry, original, update, foreign), "saved").isEmpty())
        assertEquals("succeeded", update.getString("status"))
        assertEquals("stopped", foreign.getString("status"))
        assertEquals(0, TaskLifecycle.pendingInputs(retry, listOf(update, foreign)).length())
    }

    @Test fun unconsumedInheritedInputContinuesOnceAfterASuccessfulAttempt() {
        val original = entry("original", "chat", 1).put("kind", "task").put("phase", "failed")
        val update = entry("update", "chat", 2).put("kind", "instruction").put("taskId", "original").put("status", "stopped")
        val retry = resume(original, "retry", listOf(original, update)).put("status", "succeeded")
        assertEquals(listOf(update), TaskLifecycle.settleInputs(retry, listOf(retry, original, update), "saved"))
        assertEquals("stopped", update.getString("status"))
    }

    @Test fun amendmentRetryUsesTheLatestOwningCheckpoint() {
        val original = entry("original", "chat", 1).put("kind", "task").put("phase", "failed")
        val update = entry("update", "chat", 2).put("kind", "instruction").put("taskId", "original")
        val retry = resume(original, "retry", listOf(original, update)).put("phase", "paused")
            .put("agentContext", JSONArray().put(JSONObject().put("inputId", "update").put("content", "new checkpoint")))
        for (history in listOf(listOf(original, retry, update), listOf(retry, update))) {
            val target = TaskHistorySnapshot.retryTarget(update, history)
            assertSame(retry, target)
            val snapshot = TaskHistorySnapshot.capture(history, "chat", "continue", "task", "update", null)
            assertEquals("new checkpoint", snapshot.retry!!.getJSONArray("agentContext").getJSONObject(0).getString("content"))
            assertEquals(0, snapshot.pendingInputs.length())
        }
    }

    @Test fun legacyCheckpointRecoversConsumedOwnershipWithoutTakingSiblingInputs() {
        val retry = entry("retry", "chat", 10).put("kind", "task").put("status", "succeeded")
            .put("agentContext", JSONArray().put(JSONObject().put("inputId", "used")))
        val used = entry("used", "chat", 2).put("kind", "instruction").put("taskId", "pruned-original").put("status", "failed")
        val sibling = entry("sibling", "chat", 3).put("kind", "instruction").put("taskId", "other-attempt").put("status", "stopped")
        val foreign = entry("foreign", "other", 3).put("kind", "instruction").put("taskId", "retry").put("status", "stopped")
        TaskLifecycle.settleInputs(retry, listOf(used, sibling, foreign), "saved")
        assertEquals("succeeded", used.getString("status"))
        assertEquals("stopped", sibling.getString("status"))
        assertEquals("stopped", foreign.getString("status"))
        assertSame(retry, TaskHistorySnapshot.retryTarget(used, listOf(retry, used, sibling, foreign)))
        assertEquals(0, TaskLifecycle.pendingInputs(retry, listOf(used, sibling, foreign)).length())
    }
}
