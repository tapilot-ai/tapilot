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
        var background = false
        var success = true
        var discardError: Throwable? = null
        override fun status(status: TaskExecutionStatus) { actions += status.name }
        override fun archiveAttachment(attachment: JSONObject) = JSONObject(attachment.toString()).put("id", "saved")
        override fun readAttachment(id: String) = JSONObject().put("id", id)
        override fun capabilities(): JSONObject {
            actions += "capabilities"
            return JSONObject().put("backgroundScreen", background).put("canCapture", true).put("canPerformGestures", true).put("apps", JSONArray())
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

    @Test fun backgroundTaskKeepsMainScreenAndTellsAgentWhichScreenItControls() {
        val services = Services().apply { background = true }
        val result = runner(Agent { request, callbacks ->
            assertTrue(request.messages.toString().contains("isolated background screen"))
            terminal("completed", callbacks.phone(PhoneOperation("Read", "Visible")))
        }, services).run(input().copy(backgroundScreen = true)) { false }
        assertTrue(result.outcome.isSuccess)
        assertFalse(services.actions.contains("HOME"))
        assertFalse(services.actions.contains("PREVIOUS_APP"))
        assertTrue(services.actions.contains("run"))
        val steps = services.sources.single().getJSONArray("cases").getJSONObject(0).getJSONArray("steps")
        assertTrue(steps.getJSONObject(0).getJSONObject("aiAct").getString("prompt").contains("owned nonzero background display"))
        assertTrue(steps.getJSONObject(1).getJSONObject("verifyPhone").getString("instruction").contains("owned nonzero background display"))
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

    @Test fun eachOperationKeepsItsOwnReplayAndConfirmedInstruction() {
        val services = Services()
        val result = runner(Agent { _, callbacks ->
            callbacks.phone(PhoneOperation("Read version", "Version visible", PhoneMode.OBSERVE))
            terminal("completed", callbacks.phone(PhoneOperation("Read storage", "Storage visible", PhoneMode.OBSERVE)))
        }, services, confirm = { "$it (confirmed)" }).run(input()) { false }
        assertTrue(result.outcome.isSuccess)
        assertEquals(listOf("replay-1", "replay-2"), result.reports.objects().map { it.getString("reportId") })
        assertEquals(listOf("Read version (confirmed)", "Read storage (confirmed)"),
            result.reports.objects().map { it.getString("instruction") })
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
    @Test fun setupUsesHostReadinessInsteadOfPhoneScreenshotAndRetainsReports() {
        val services = Services().apply { success = false }
        val host = JSONObject().put("phase", "completed").put("reply", "Native setup ready")
        val agent = Agent { _, callbacks ->
            val result = callbacks.setupBackgroundScreen()
            JSONObject().put("phase", result.getString("phase")).put("reply", result.getString("reply"))
                .put("completionKind", "setup")
        }
        val result = ConversationTaskRunner(agent, services, { _, _ -> JSONObject() }, { it }, {},
            setupBackgroundScreen = { operate ->
                operate("Enable debugging", "Enabled or user confirmation shown")
                host
            }).run(input().copy(source = "帮我开启后台屏幕", userInput = "帮我开启后台屏幕", backgroundScreen = true)) { false }
        assertTrue(result.outcome.isSuccess)
        assertEquals("Native setup ready", result.outcome.getOrThrow())
        assertEquals(1, result.reports.length())
        assertFalse(services.sources.single().toString().contains("owned nonzero background display"))
        assertEquals("1", services.environments.single().getString("MIDSCENE_ANDROID_SETUP_NAVIGATION"))
        assertFalse(services.actions.contains("HOME"))
    }

    @Test fun setupCannotClaimHostSuccessWithoutRunningSetup() {
        val result = runner(Agent { _, _ -> JSONObject().put("phase", "completed").put("reply", "Invented")
            .put("completionKind", "setup") }, Services()).run(input().copy(source = "帮我开启后台屏幕", userInput = "帮我开启后台屏幕")) { false }
        assertTrue(result.outcome.isFailure)
    }

    @Test fun setupRejectsAlteredHostResult() {
        val agent = Agent { _, callbacks ->
            callbacks.setupBackgroundScreen()
            JSONObject().put("phase", "completed").put("reply", "Invented success").put("completionKind", "setup")
        }
        val result = ConversationTaskRunner(agent, Services(), { _, _ -> JSONObject() }, { it }, {},
            setupBackgroundScreen = { JSONObject().put("phase", "waiting_user").put("reply", "Consent needed") })
            .run(input().copy(source = "帮我开启后台屏幕", userInput = "帮我开启后台屏幕")) { false }
        assertTrue(result.outcome.isFailure)
    }

    @Test fun cancellingSetupConfirmationPreventsSetupSideEffects() {
        val stopped = AtomicBoolean()
        var setupCalled = false
        val agent = Agent { _, callbacks -> callbacks.setupBackgroundScreen(); terminal("completed") }
        val result = ConversationTaskRunner(agent, Services(), { _, _ -> JSONObject() },
            { stopped.set(true); it }, {}, setupBackgroundScreen = { setupCalled = true; JSONObject() })
            .run(input().copy(userInput = "帮我开启后台屏幕"), stopped::get)
        assertTrue(result.outcome.isFailure)
        assertFalse(setupCalled)
    }

    @Test fun ordinaryBackgroundWorkCannotInvokeSetupEvenIfTheAgentRequestsIt() {
        var called = false
        val task = ConversationTaskRunner(Agent { request, callbacks ->
            assertFalse(request.allowBackgroundSetup)
            callbacks.setupBackgroundScreen()
            terminal("completed")
        }, Services(), { _, _ -> JSONObject() }, { it }, {}, setupBackgroundScreen = { called = true; JSONObject() })
        val result = task.run(input().copy(userInput = "Open Android Settings on the background screen")) { false }
        assertTrue(result.outcome.isFailure)
        assertFalse(called)
    }

    @Test fun realTypedAndButtonContinuationsKeepNativeSetupCapability() {
        val previous = JSONObject().put("id", "setup").put("conversationId", "chat").put("createdAt", 1)
            .put("source", "帮我开启后台屏幕").put("goal", "帮我开启后台屏幕").put("phase", "waiting_user")
        for ((text, explicit, handoff) in listOf(Triple("继续", false, false),
            Triple("Continue the task. Inspect the current screen.", true, false), Triple("继续", true, true))) {
            val history = TaskHistorySnapshot.capture(listOf(previous), "chat", text, "task", null, if (explicit) "setup" else null)
            var called = false
            val agent = Agent { request, callbacks ->
                assertTrue(request.allowBackgroundSetup)
                callbacks.setupBackgroundScreen().put("completionKind", "setup")
            }
            val result = ConversationTaskRunner(agent, Services(), { _, _ -> JSONObject() }, { it }, {},
                setupBackgroundScreen = { called = true; JSONObject().put("phase", "completed").put("reply", "Native ready") })
                .run(input(history).copy(userInput = text, source = text, resume = explicit, phoneStarted = handoff, goal = "帮我开启后台屏幕")) { false }
            assertTrue(result.outcome.toString(), result.outcome.isSuccess)
            assertTrue(called)
        }
    }

    @Test fun setupCannotCompleteAsAnAnswerOrAnOrdinaryPhoneTask() {
        for (kind in listOf("answer", "phone")) {
            val agent = Agent { _, _ -> JSONObject().put("phase", "completed").put("reply", "Invented ready")
                .put("completionKind", kind).put("operations", 0).put("lastPhoneResult", PhoneResult(true, "Visible").json()) }
            val result = runner(agent, Services()).run(input().copy(userInput = "帮我开启后台屏幕")) { false }
            assertTrue(kind, result.outcome.isFailure)
        }
    }

}
