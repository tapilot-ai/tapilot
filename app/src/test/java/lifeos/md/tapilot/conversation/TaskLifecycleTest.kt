package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TaskLifecycleTest {
    private fun entry(id: String = "one") = JSONObject().put("id", id).put("source", "打开计算器").put("createdAt", 1).put("status", "running")
    @Test fun retryKeepsGoalAndCheckpointButResetsAttemptCounters() {
        val old = entry(); TaskLifecycle.begin(old)
        old.put("agentContext", JSONArray().put(JSONObject().put("role", "user").put("content", "原任务")))
            .put("phoneOperations", 9).put("modelTurns", 12).put("plan", JSONArray().put("检查屏幕"))
        val next = entry("two"); TaskLifecycle.begin(next, old)
        assertEquals("one", next.getString("taskId")); assertEquals("打开计算器", next.getString("goal"))
        assertEquals(2, next.getInt("attempt")); assertEquals(0, next.getInt("phoneOperations")); assertEquals(0, next.getInt("modelTurns"))
        assertEquals(old.getJSONArray("agentContext").toString(), next.getJSONArray("agentContext").toString())
    }
    @Test fun processDeathPreservesQueueAndMarksTasksPausedWithoutAutoRunning() {
        val task = entry(); TaskLifecycle.begin(task)
        val update = entry("update").put("kind", "instruction").put("status", "queued")
        val state = ConversationStore.empty().put("history", JSONArray().put(task).put(update))
        TaskLifecycle.recover(state)
        assertEquals(2, state.getJSONArray("history").length())
        assertEquals("paused", task.getString("phase")); assertEquals("stopped", update.getString("status"))
        assertEquals("打开计算器", update.getString("source"))
    }
    @Test fun clarificationResumesLatestWaitingTaskAndExplicitContinueResumesPausedTask() {
        val task = entry().put("phase", "waiting_user")
        assertSame(task, TaskLifecycle.continuation(listOf(task), "系统计算器"))
        task.put("phase", "paused")
        assertNull(TaskLifecycle.continuation(listOf(task), "天气如何"))
        assertSame(task, TaskLifecycle.continuation(listOf(task), "继续"))
        task.put("phase", "completed")
        assertNull(TaskLifecycle.continuation(listOf(task), "继续"))
    }
    @Test fun checkpointSavesCurrentPlanAndCounters() {
        val task = entry(); TaskLifecycle.begin(task)
        TaskLifecycle.event(task, JSONObject().put("type", "plan").put("steps", JSONArray().put("查看屏幕")).put("currentStep", 0))
        TaskLifecycle.event(task, JSONObject().put("type", "checkpoint").put("messages", JSONArray()).put("turns", 2).put("operations", 1))
        assertEquals("查看屏幕", task.getJSONArray("plan").getString(0)); assertEquals(2, task.getInt("modelTurns")); assertEquals(1, task.getInt("phoneOperations"))
        TaskLifecycle.finish(task, "succeeded", "waiting_user"); assertEquals("waiting_user", task.getString("phase"))
    }
    @Test fun resumeReplaysOnlyUnconsumedAmendments() {
        val task = entry().put("agentContext", JSONArray().put(JSONObject().put("inputId", "used")))
        val used = entry("used").put("kind", "instruction").put("taskId", "one")
        val pending = entry("pending").put("kind", "instruction").put("taskId", "one").put("source", "改成明天")
        val result = TaskLifecycle.pendingInputs(task, listOf(used, pending))
        assertEquals(1, result.length()); assertEquals("改成明天", result.getJSONObject(0).getString("text"))
    }
    @Test fun failedAttemptDoesNotAutomaticallyRetryConsumedOrPendingUpdates() {
        val task = entry().put("status", "failed").put("detail", "已达到轮次上限")
            .put("agentContext", JSONArray().put(JSONObject().put("inputId", "used")))
        val used = entry("used").put("kind", "instruction").put("taskId", "one")
        val pending = entry("pending").put("kind", "instruction").put("taskId", "one")
        assertTrue(TaskLifecycle.settleInputs(task, listOf(used, pending), "补充消息已保存").isEmpty())
        assertEquals("failed", used.getString("status")); assertEquals("stopped", pending.getString("status"))
        val retry = TaskLifecycle.pendingInputs(task, listOf(used, pending))
        assertEquals(1, retry.length()); assertEquals("pending", retry.getJSONObject(0).getString("id"))
    }
    @Test fun explicitTaskSwitchDoesNotResumeWaitingTask() {
        val task = entry().put("phase", "waiting_user")
        assertNull(TaskLifecycle.continuation(listOf(task), "换个任务：打开设置"))
        assertTrue(TaskLifecycle.isNewTask("New task: open settings"))
        assertTrue(TaskLifecycle.isNewTask("换个任务打开设置"))
        assertFalse(TaskLifecycle.isNewTask("请不要切换任务"))
    }
}
