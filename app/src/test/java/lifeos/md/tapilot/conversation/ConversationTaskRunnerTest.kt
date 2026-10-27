package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.TaskExecutionStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class ConversationTaskRunnerTest {
    private class Agent(private val execute: (AgentInput, AgentCallbacks) -> JSONObject) : ConversationAgent {
        override fun prepare() = Unit
        override fun steer(id: String, text: String, followUp: Boolean) = true
        override fun stop() = Unit
        override fun close() = Unit
        override fun run(input: AgentInput, stopped: () -> Boolean, callbacks: AgentCallbacks) = execute(input, callbacks)
    }
    private class Services : ConversationTaskServices {
        val actions = mutableListOf<String>()
        val sources = mutableListOf<JSONObject>()
        val environments = mutableListOf<JSONObject>()
        var afterRun: () -> Unit = {}
        var success = true
        var discardError: Throwable? = null
        override fun status(status: TaskExecutionStatus) { actions += status.name }
        override fun archiveAttachment(attachment: JSONObject) = JSONObject(attachment.toString()).put("id", "saved")
        override fun readAttachment(id: String) = JSONObject().put("id", id)
        override fun capabilities(): JSONObject {
            actions += "capabilities"
            return JSONObject().put("canCapture", true).put("canPerformGestures", true).put("apps", JSONArray())
        }
        override fun navigate(destination: TaskNavigation) { actions += destination.name }
        override fun prepare(environment: JSONObject) { actions += "prepare" }
        override fun run(source: String, environment: JSONObject): JSONObject {
            actions += "run"; sources += JSONObject(source); environments += JSONObject(environment.toString())
            afterRun()
            return JSONObject().put("exitCode", 0).put("finalResponse", JSONObject().put("success", success).put("observation", "Visible result").toString())
                .put("reports", JSONArray().put(JSONObject().put("reportId", "replay-${sources.size}")))
        }
        override fun discardPrepared() { actions += "discard"; discardError?.let { throw it } }
    }
    private fun input(history: TaskHistorySnapshot = TaskHistorySnapshot(null, emptyList(), JSONArray())) = ConversationTaskInput(
        "Read the current screen", "Read the current screen", "task", history, false, "Read the current screen",
        AgentLimits(), "en", null, false, false, false, JSONObject().put("MIDSCENE_MODEL_API_KEY", "test-only-key"))
    private fun terminal(phase: String, phone: PhoneResult? = null) = JSONObject().put("phase", phase).put("reply", "Result")
        .put("operations", if (phone == null) 0 else 1).apply { phone?.let { put("lastPhoneResult", it.json()) } }
    private fun runner(agent: Agent, services: Services, confirm: (String) -> String = { it }, events: MutableList<TaskRunEvent> = mutableListOf()) =
        ConversationTaskRunner(agent, services, { _, _ -> JSONObject() }, confirm, events::add)

    @Test fun clarificationAnswerKeepsGoalAndResumeContextThenExecutesPhone() {
        val services = Services()
        val question = runner(Agent { _, _ -> terminal("waiting_user") }, services).run(input()) { false }
        assertEquals("waiting_user", question.phase)
        assertTrue(question.outcome.isSuccess)
        assertFalse(services.actions.contains("run"))
        val context = JSONArray().put(JSONObject().put("role", "user").put("content", "Read the current screen"))
        val previous = JSONObject().put("phase", "waiting_user").put("agentContext", context)
        val resumed = input(TaskHistorySnapshot(previous, emptyList(), JSONArray())).copy(userInput = "Settings", resume = true)
        val result = runner(Agent { request, callbacks ->
            assertEquals("Read the current screen", request.goal)
            assertEquals("Settings", request.userInput)
            assertEquals(context.toString(), request.resume.toString())
            assertFalse(request.messages.toString().contains("test-only-key"))
            terminal("completed", callbacks.phone(PhoneOperation("Read", "Label visible", PhoneMode.OBSERVE)))
        }, services).run(resumed) { false }
        assertTrue(result.outcome.isSuccess)
        assertEquals(listOf("PREVIOUS_APP"), services.actions.filter { it in listOf("HOME", "PREVIOUS_APP") })
    }

    @Test fun cancellationDuringConfirmationPreventsAllPhoneActions() {
        val stopped = AtomicBoolean()
        val services = Services()
        val task = runner(Agent { _, callbacks -> terminal("completed", callbacks.phone(PhoneOperation("Read", "Visible"))) }, services,
            confirm = { stopped.set(true); it })
        val result = task.run(input(), stopped::get)
        assertTrue(result.outcome.isFailure)
        assertFalse(services.actions.contains("capabilities"))
        assertFalse(services.actions.contains("run"))
        assertEquals("discard", services.actions.last())
    }

    @Test fun latePhoneSuccessAfterCancellationCannotPublishCompletion() {
        val stopped = AtomicBoolean()
        val services = Services().apply { afterRun = { stopped.set(true) } }
        val events = mutableListOf<TaskRunEvent>()
        val result = runner(Agent { _, callbacks -> terminal("completed", callbacks.phone(PhoneOperation("Read", "Visible"))) }, services,
            events = events).run(input(), stopped::get)
        assertTrue(result.outcome.isFailure)
        assertEquals(1, result.reports.length())
        assertFalse(events.any { it is TaskRunEvent.Operation })
    }

    @Test fun failedVerificationPreservesEvidenceAndRejectsCompletedResult() {
        val services = Services().apply { success = false }
        val result = runner(Agent { _, callbacks -> terminal("completed", callbacks.phone(PhoneOperation("Read", "Visible"))) }, services)
            .run(input()) { false }
        assertTrue(result.outcome.isFailure)
        assertEquals(1, result.reports.length())
        assertFalse(result.completedOperations.getJSONObject(0).getBoolean("success"))
    }

    @Test fun deepPlanningDoesNotChangeLaterFastOperationAndPreloadOccursOnce() {
        val services = Services()
        val result = runner(Agent { request, callbacks ->
            callbacks.complete(request.messages, JSONArray())
            callbacks.phone(PhoneOperation("First", "Visible", effort = PlanningEffort.DEEP_THINK))
            callbacks.complete(request.messages, JSONArray())
            terminal("completed", callbacks.phone(PhoneOperation("Second", "Visible")))
        }, services).run(input()) { false }
        assertTrue(result.outcome.isSuccess)
        assertEquals(1, services.actions.count { it == "prepare" })
        assertEquals(1, services.actions.count { it == "HOME" })
        assertEquals("120000", services.environments[0].getString("MIDSCENE_MODEL_TIMEOUT"))
        assertFalse(services.environments[1].has("MIDSCENE_MODEL_TIMEOUT"))
        assertEquals(2, result.completedOperations.length())
    }

    @Test fun pendingAmendmentsReachAgentWithoutResettingVerifiedHistory() {
        val pending = JSONArray().put(JSONObject().put("id", "amendment").put("text", "Also read storage"))
        val prior = JSONObject().put("id", "old").put("createdAt", 1).put("status", "succeeded")
            .put("source", "Read Android version").put("reply", "Android 14")
        val result = runner(Agent { request, _ ->
            assertTrue(request.messages.toString().contains("Android 14"))
            assertEquals(pending.toString(), request.resumeInputs.toString())
            terminal("waiting_user")
        }, Services()).run(input(TaskHistorySnapshot(null, listOf(prior), pending))) { false }
        assertTrue(result.outcome.isSuccess)
    }

    @Test fun generalAnswerDoesNotRequirePhoneCapabilities() {
        val services = Services()
        val result = runner(Agent { _, _ -> terminal("completed").put("completionKind", "answer") }, services).run(input()) { false }
        assertTrue(result.outcome.isSuccess)
        assertEquals(listOf("STARTING", "discard"), services.actions)
    }

    @Test fun cleanupFailureSettlesAsFailureInsteadOfLeavingTaskRunning() {
        val services = Services().apply { discardError = IllegalStateException("cleanup failed") }
        val result = runner(Agent { _, _ -> terminal("waiting_user") }, services).run(input()) { false }
        assertEquals("cleanup failed", result.outcome.exceptionOrNull()?.message)
    }

    @Test fun invalidTerminalPhaseFailsClosed() {
        val result = runner(Agent { _, _ -> terminal("unknown") }, Services()).run(input()) { false }
        assertTrue(result.outcome.isFailure)
    }

    @Test fun failedFinalVerificationCannotBeSavedAsACompletedTask() {
        val result = runner(Agent { _, _ -> terminal("completed") }, Services()).run(input()) { false }
        assertEquals("task_verification_missing", TaskFailure.find(result.outcome.exceptionOrNull())?.code)
        val entry = JSONObject().put("id", "failed-verification").put("source", "Read screen")
        TaskLifecycle.finish(entry, "failed", result.phase)
        assertEquals("failed", entry.getString("phase"))
        assertSame(entry, TaskLifecycle.continuation(listOf(entry), "continue"))
    }

    @Test fun cleanupFailureCannotRemainCompletedOrAutomaticallyResumeAsClarification() {
        for (phase in listOf("completed", "waiting_user")) {
            val services = Services().apply { discardError = IllegalStateException("cleanup failed") }
            val result = runner(Agent { _, _ -> terminal(phase).put("completionKind", "answer") }, services).run(input()) { false }
            assertTrue(result.outcome.isFailure)
            val entry = JSONObject().put("id", "cleanup-failed").put("source", "Original task")
            TaskLifecycle.finish(entry, "failed", result.phase)
            assertEquals("failed", entry.getString("phase"))
            assertNull(TaskLifecycle.continuation(listOf(entry), "A separate question"))
        }
    }
}
