package lifeos.md.tapilot.conversation

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.AppTheme
import lifeos.md.tapilot.LaunchContext
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.bridge.ModelCredentialStore
import lifeos.md.tapilot.bridge.NativeJavascriptBridge
import lifeos.md.tapilot.chat.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Main-thread state; blocking model and Node work is confined to one worker. */
class NativeConversationCoordinator(
    private val activity: Context,
    private val services: NativeJavascriptBridge,
    private val store: ConversationStore,
    private val emit: (ChatState) -> Unit,
    private val focus: () -> Unit,
) : AutoCloseable {
    var data by mutableStateOf(ConversationStore.empty())
        private set
    var ready by mutableStateOf(false)
        private set
    var settingsOpen by mutableStateOf(false)
        private set
    var advancedOpen by mutableStateOf(false)
        private set
    var settingsStatus by mutableStateOf("")
        private set
    var settingsSection by mutableStateOf("")
        private set
    val dark: Boolean get() = AppTheme.isDark(activity)
    val chinese: Boolean get() = AppLanguage.resolved(activity) == "zh"
    var settingsBusy by mutableStateOf(false)
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val persistence = ConversationWriter(store) { failure -> handler.post {
        if (!closed) { stop(clearPending = false); fail(failure) }
    } }
    private val credentials = ModelCredentialStore(activity)
    private val pi = NativePiConversation(activity)
    private var activeTaskId = ""
    @Volatile private var closed = false
    var globalMode = false
    @Volatile var backgroundReplies = false
    private val pendingTurns = ArrayDeque<JSONObject>()
    private var running = false
    private var error = ""
    private var errorEpoch = 0
    private var draftEpoch = 0
    private var pendingAttachment: JSONObject? = null
    private var preview: ChatAttachment? = null
    private var progress: ChatProgress? = null
    private var confirmation: CompletableFuture<String>? = null
    private var confirmationText: String? = null
    private var cancellation = AtomicBoolean(false)
    @Volatile private var stopBarrier: CompletableFuture<Any?>? = null
    private var session = ""
    private val queuedContexts = ArrayDeque<JSONObject>()

    val preset: String get() = data.optString("preset", "doubao")
    fun profile(id: String = preset): JSONObject = JSONObject((data.optJSONObject("profiles")?.optJSONObject(id) ?: ModelProfiles.defaults(id)).toString())
    fun hasKey(id: String) = credentials.contains(id)
    private var initializing = false
    fun initialize() {
        if (ready || initializing || closed) return
        initializing = true
        worker.execute {
            val loaded = runCatching { store.read().also { state ->
                TaskLifecycle.recover(state)
                store.save(state)
            } }
            handler.post {
                if (closed) return@post
                initializing = false
                loaded.fold(onSuccess = { data = it
                    // Recover queued input into the composer; restarting never triggers phone actions.
                    val recovered = it.optJSONArray("pendingTurns")?.objects().orEmpty()
                    if (recovered.isNotEmpty()) {
                        data.put("draft", (listOf(data.optString("draft")) + recovered.map { turn -> turn.optString("text") }).filter { text -> text.isNotBlank() }.joinToString("\n"))
                        pendingAttachment = recovered.firstNotNullOfOrNull { turn -> turn.optJSONObject("attachment") }
                        save()
                    }
                    ready = true; publish(); receiveLaunchContext() }, onFailure = { fail(it) })
            }
        }
    }
    private val persistDraft = Runnable { if (ready && !closed) persistence.saveDraft(data.optString("draft")) }
    private fun save() { handler.removeCallbacks(persistDraft); data.put("pendingTurns", JSONArray(pendingTurns.toList())); persistence.save(data); data = JSONObject().also { copy -> data.keys().forEach { copy.put(it, data.get(it)) } } }
    private fun history() = data.getJSONArray("history").objects()
    private fun conversation(entry: JSONObject) = entry.optString("conversationId", entry.optString("id"))
    private fun active() = data.getString("active")
    private fun local(zh: String, en: String) = if (AppLanguage.resolved(activity) == "zh") zh else en
    fun publish() {
        if (closed) return
        val history = history()
        val messages = history.filter { conversation(it) == active() }.flatMap { e ->
            val id = e.getString("id")
            val status = e.optString("status")
            val reports = e.optJSONArray("reports")?.objects().orEmpty()
            val report = reports.lastOrNull { it.optString("kind") == "agent" } ?: reports.lastOrNull()
            val response = e.optString("reply").ifBlank {
                when (status) { "stopped" -> local("任务已停止", "Task stopped"); "failed" -> e.optString("detail"); else -> "" }
            }
            val user = e.optLong("createdAt") to ChatMessage("$id-user", "user", e.optString("inputText", e.optString("source")).ifBlank { e.optString("source") }, status, "", id,
                ChatAttachment.parse(e.optJSONObject("attachment")))
            if (e.optString("kind") == "instruction" && status !in listOf("stopped", "failed")) listOf(user)
            else listOf(user, (if (status == "running") Long.MAX_VALUE else e.optLong("completedAt", e.optLong("createdAt") + 1)) to
                ChatMessage("$id-assistant", "assistant", response, status, report?.optString("reportId").orEmpty(), id, task = ChatTask(e.optString("phase"),
                    e.optJSONArray("plan")?.strings().orEmpty(), e.optInt("currentStep"), e.optInt("modelTurns"),
                    e.optInt("phoneOperations"), e.optInt("attempt", 1), e.optJSONArray("completedOperations")?.objects().orEmpty()
                        .map { it.optString("instruction") to it.optBoolean("success") })))
        }.sortedBy { it.first }.map { it.second }
        val conversations = history.sortedByDescending { it.optLong("createdAt") }.distinctBy { conversation(it) }.map { e ->
            val first = history.filter { conversation(it) == conversation(e) }.minBy { it.optLong("createdAt") }
            ChatConversation(conversation(e), first.optString("inputText", first.optString("source")).take(28).ifBlank { local("新对话", "New conversation") })
        }.toMutableList()
        if (conversations.none { it.id == active() }) conversations.add(0, ChatConversation(active(), local("新对话", "New conversation")))
        val model = profile()
        emit(ChatState(ready = ready, language = AppLanguage.resolved(activity), dark = AppTheme.isDark(activity), paletteId = AppTheme.paletteId(activity),
            advancedMode = data.optBoolean("advanced"), activeConversationId = active(), messages = messages, conversations = conversations,
            draft = data.optString("draft"), draftEpoch = draftEpoch, running = running,
            configured = model.optString("baseUrl").isNotBlank() && model.optString("modelName").isNotBlank() && model.optString("modelFamily").isNotBlank() && hasKey(preset),
            modelName = model.optString("modelName"), speechProvider = data.optString("speechProvider", "volcengine"),
            error = error, errorEpoch = errorEpoch, needsAccessibility = MidsceneAccessibilityService.current() == null,
            attachment = ChatAttachment.parse(pendingAttachment), preview = preview, progress = progress, confirmation = confirmationText))
    }
    fun command(value: JSONObject) {
        if (closed || !ready) return
        if (value.optString("action") == "draft") {
            data.put("draft", value.optString("text"))
            handler.removeCallbacks(persistDraft)
            handler.postDelayed(persistDraft, 350)
            return
        }
        runCatching {
            val action = value.getString("action")
            when (action) {
                "send" -> {
                    val preservedDraft = if (value.optBoolean("preserveDraft")) data.optString("draft") else null
                    submit(value.optString("text", data.optString("draft")))
                    if (preservedDraft != null) { data.put("draft", preservedDraft); draftEpoch++; save() }
                }
                "stop" -> stop()
                "settings" -> if (!running) { settingsOpen = true; settingsStatus = "" }
                "advanced" -> if (!running && data.optBoolean("advanced")) advancedOpen = true
                "new" -> if (!running) { data.put("active", "chat-${UUID.randomUUID()}"); preview = null; save() }
                "select" -> if (!running && history().any { conversation(it) == value.optString("id") }) { data.put("active", value.getString("id")); preview = null; save() }
                "attachment" -> pendingAttachment = value.getJSONObject("attachment")
                "removeAttachment" -> pendingAttachment = null
                "closePreview" -> preview = null
                "preview", "editMessage", "retry" -> loadMessage(action, value.optString("id"))
                "report" -> services.call("report.open", JSONObject().put("reportId", value.getString("id"))) { it.onFailure(::fail) }
                "accessibility" -> services.call("app.openAccessibilitySettings") { it.onFailure(::fail) }
                "confirm" -> {
                    if (value.optBoolean("accepted") && value.optString("text").isNotBlank()) confirmation?.complete(value.getString("text").trim())
                    else stop()
                    confirmation = null; confirmationText = null
                }
            }
            publish()
        }.onFailure(::fail)
    }
    private var panelResumeId: String? = null
    private fun submit(text: String) {
        if (text.isBlank() && pendingAttachment == null) return
        if (!running) { val resume = panelResumeId?.takeUnless { TaskLifecycle.isNewTask(text) }; panelResumeId = null; start(text, global = globalMode, continueOf = resume); return }
        if (pendingAttachment == null && text.trim().lowercase() in listOf("停止", "取消", "停一下", "别做了", "stop", "cancel")) {
            val control = JSONObject().put("id", UUID.randomUUID().toString()).put("conversationId", active())
                .put("createdAt", System.currentTimeMillis()).put("kind", "control").put("source", text).put("inputText", text)
                .put("status", "succeeded").put("reply", local("正在停止任务", "Stopping task")).put("reports", JSONArray())
            data.put("history", JSONArray(listOf(control) + history())).put("draft", ""); draftEpoch++; save(); stop(); return
        }
        if (pendingAttachment == null && TaskLifecycle.isNewTask(text)) {
            pendingTurns.add(JSONObject().put("text", text).put("global", globalMode))
            data.put("draft", ""); draftEpoch++; save(); stop(clearPending = false)
            progress = progress?.copy(title = local("正在停止旧任务，随后开始新任务", "Stopping before starting the new task"))
            return
        }
        // Text amendments stay in the active Pi task. Persist before writing to its pipe.
        if (pendingAttachment == null && find(activeTaskId)?.optString("kind") == "task" && !cancellation.get()) {
            val inputId = UUID.randomUUID().toString()
            val amendment = JSONObject().put("id", inputId).put("conversationId", active()).put("createdAt", System.currentTimeMillis())
                .put("source", text).put("inputText", text).put("kind", "instruction").put("taskId", activeTaskId)
                .put("status", "queued").put("reports", JSONArray())
            data.put("history", JSONArray(listOf(amendment) + history())); save()
            if (pi.steer(inputId, text)) {
                data.put("draft", ""); draftEpoch++; save()
                progress = progress?.copy(title = local("已收到补充，将调整任务", "Update received, adjusting task")); publish()
                return
            }
            // Agent may still be provisioning or settling. Keep the input for the next attempt.
            find(inputId)?.let { it.put("status", "stopped"); TaskLifecycle.finish(it, "stopped") }; save()
        }
        pendingTurns.add(JSONObject().put("text", text).put("global", globalMode).apply {
            panelResumeId?.takeUnless { TaskLifecycle.isNewTask(text) }?.let { put("continueOf", it) }
            panelResumeId = null
            pendingAttachment?.let { put("attachment", JSONObject(it.toString())) }
        })
        data.put("draft", ""); pendingAttachment = null; draftEpoch++; save()
        stop(clearPending = false)
        progress = progress?.copy(title = local("正在打断，随后处理新消息", "Interrupting, then processing your message"))
    }
    private fun consumePendingTurn(): Boolean {
        if (pendingTurns.isEmpty()) return false
        val turn = pendingTurns.removeFirst()
        start(turn.getString("text"), turn.optJSONObject("attachment"), global = turn.optBoolean("global"),
            restoreDraft = data.optString("draft"), restoreAttachment = pendingAttachment, continueOf = turn.optString("continueOf").takeIf { it.isNotBlank() })
        return true
    }
    private fun loadMessage(action: String, id: String) {
        if (action != "preview" && running) return
        val entry = history().find { it.optString("id") == id && conversation(it) == active() }
        if (id.isNotEmpty() && entry == null) return
        val cid = active()
        val draftAttachment = pendingAttachment
        worker.execute {
            val result = runCatching { entry?.optJSONObject("attachment")?.let { await("app.readAttachment", JSONObject().put("id", it.getString("id"))) as JSONObject } ?: draftAttachment }
            handler.post {
                if (closed || cid != active() || (action != "preview" && running)) return@post
                result.fold(onSuccess = { file ->
                    when (action) {
                        "preview" -> preview = ChatAttachment.parse(file)
                        "editMessage" -> { data.put("draft", entry!!.optString("inputText", entry.optString("source"))); pendingAttachment = file; draftEpoch++; save(); focus() }
                        "retry" -> start(entry!!.optString("inputText", entry.optString("source")), file, entry.optString("id"), kind = entry.optString("kind", "task"))
                    }; publish()
                }, onFailure = ::fail)
            }
        }
    }
    fun openGlobalConversation() {
        // Opening a foreground panel changes the screen under Midscene. Stop safely,
        // then apply the next message to this task's saved context.
        if (running && activeTaskId.isNotBlank()) panelResumeId = activeTaskId
        settingsOpen = false; advancedOpen = false; preview = null
        stop(clearPending = false)
        publish()
    }
    fun receiveLaunchContext() {
        if (!ready || closed) return
        LaunchContext.consume()?.let { queuedContexts.add(it) }
        consumeQueue()
    }
    private fun consumeQueue() {
        if (running || queuedContexts.isEmpty() || !ready) return
        val context = queuedContexts.removeFirst()
        val text = context.optString("text")
        val file = context.optString("dataUrl").takeIf { it.isNotBlank() }?.let {
            JSONObject().put("name", context.optString("label", "当前屏幕")).put("kind", "image").put("images", JSONArray().put(it))
        }
        if (context.optBoolean("autoSend") && text.isNotBlank()) {
            val previousDraft = data.optString("draft")
            val previousAttachment = pendingAttachment
            start(text, file, global = context.optBoolean("fromPanel"), restoreDraft = previousDraft, restoreAttachment = previousAttachment)
        } else {
            data.put("draft", listOf(data.optString("draft"), text).filter { it.isNotBlank() }.joinToString("\n"))
            pendingAttachment = file; draftEpoch++; save(); publish()
            if (!context.optBoolean("voice")) focus()
        }
    }
    private fun start(text: String, file: JSONObject? = pendingAttachment, retryOf: String? = null, global: Boolean = globalMode,
                      restoreDraft: String? = null, restoreAttachment: JSONObject? = null, kind: String = "task", continueOf: String? = null) {
        if (running || (text.isBlank() && file == null)) return
        val model = profile()
        val key = credentials.get(preset).orEmpty()
        if (key.isBlank() || model.optString("modelName").isBlank() || model.optString("modelFamily").isBlank()) {
            fail(IllegalStateException(local("请先配置模型 API", "Configure model API first"))); settingsOpen = true; return
        }
        runCatching { ModelProfiles.endpoint(model.getString("baseUrl")) }.onFailure { fail(it); return }
        running = true; error = ""; preview = null; settingsOpen = false; advancedOpen = false
        val stopped = AtomicBoolean(false); cancellation = stopped; stopBarrier = null
        session = UUID.randomUUID().toString(); val runSession = session
        val cid = active()
        val id = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val limits = AgentLimits.from(data)
        val before = history().map { JSONObject(it.toString()) }
        val retry = before.find { it.optString("id") == (continueOf ?: retryOf) }
            ?: if (kind == "task") TaskLifecycle.continuation(before.filter { conversation(it) == cid }, text) else null
        val prior = before.filter { conversation(it) == cid && (retry == null || (it.optString("id") != retryOf && it.optLong("createdAt") <= retry.optLong("createdAt"))) }
        val source = text.trim().ifBlank { local("请查看附件", "Please inspect the attachment") }
        val entry = JSONObject().put("id", id).put("conversationId", cid).put("createdAt", startedAt)
            .put("source", source).put("inputText", text).put("kind", kind).put("modelName", model.getString("modelName"))
            .put("status", "running").put("reports", JSONArray())
        retry?.let { entry.put("retryOf", it.getString("id")) }
        TaskLifecycle.begin(entry, retry?.takeIf { continueOf != null || it.optString("phase") != "completed" })
        activeTaskId = id
        if (kind == "task") pi.prepare()
        data.put("history", JSONArray(listOf(entry) + before).let { all -> JSONArray(all.objects().take(200)) })
        data.put("draft", ""); pendingAttachment = null; draftEpoch++; save()
        progress = ChatProgress(local("正在理解任务", "Understanding task"), "", "", startedAt, 0)
        publish()
        worker.execute {
            val reports = JSONArray()
            val completedOperations = JSONArray()
            var phoneStarted = false
            var finalPhase = ""
            val outcome = runCatching {
                await("task.status", JSONObject().put("status", "starting"), runSession, stopped)
                val saved = file?.let { if (it.optString("id").isNotBlank()) it else await("app.archiveAttachment", JSONObject().put("attachment", it), runSession, stopped) as JSONObject }
                if (saved != null) onMain {
                    val ref = JSONObject().put("id", saved.getString("id")).put("name", saved.optString("name")).put("kind", saved.getString("kind"))
                    if (saved.has("pageCount")) ref.put("pageCount", saved.getInt("pageCount"))
                    find(id)?.put("attachment", ref); save(); publish()
                }
                val env = JSONObject().put("MIDSCENE_MODEL_BASE_URL", model.getString("baseUrl"))
                    .put("MIDSCENE_MODEL_API_KEY", key).put("MIDSCENE_MODEL_NAME", model.getString("modelName")).put("MIDSCENE_MODEL_FAMILY", model.getString("modelFamily"))
                if (kind == "yaml") {
                    await("app.goHome", sessionId = runSession, stopped = stopped)
                    val run = await("runtime.runTest", JSONObject().put("source", source).put("kind", "yaml").put("environment", env), runSession, stopped, 600) as JSONObject
                    run.optJSONArray("reports")?.objects()?.forEach { reports.put(it) }
                    check(run.optInt("exitCode", 1) == 0) { run.optString("failure", "执行失败") }
                    run.optString("finalResponse").ifBlank { "任务已完成" }
                } else {
                    val attachments = prior.filter { (it.optString("status") == "succeeded" || it.optString("status") == "stopped") && it.has("attachment") }
                        .sortedBy { it.optLong("createdAt") }.takeLast(2).associate { e ->
                            val ref = e.getJSONObject("attachment")
                            e.getString("id") to runCatching { await("app.readAttachment", JSONObject().put("id", ref.getString("id")), runSession, stopped) as JSONObject }
                                .getOrElse { JSONObject().put("text", "之前的附件已不可用。不能猜测内容，请用户重新提供。") }
                        }
                    val modelSource = source + saved?.optString("text").orEmpty().takeIf { it.isNotBlank() }?.let { "\n\n文件内容：\n${it.take(30_000)}" }.orEmpty() +
                        if (saved?.optString("name")?.contains("屏幕") == true) "\n图片是用户先前保存的屏幕快照，不是当前实时画面。" else ""
                    val messages = NativeConversationAgent.messages(prior, modelSource, AppLanguage.resolved(activity) == "zh", attachments, saved?.optJSONArray("images")?.strings().orEmpty())
                    val reply = pi.run(messages, retry?.takeIf { continueOf != null || it.optString("phase") != "completed" }?.optJSONArray("agentContext"), limits, stopped::get, complete = { chat, tools ->
                        updateProgress(if (phoneStarted) local("正在整理结果", "Preparing reply") else local("正在理解任务", "Understanding task"))
                        requestCompletion(model, key, chat, runSession, stopped, tools)
                    }, phone = { instruction, condition ->
                        updateProgress(local("准备操作手机", "Preparing phone operation"), instruction, increment = true)
                        val revised = confirmOperation(instruction, stopped)
                        check(!stopped.get()) { "任务已停止" }
                        val capabilities = await("device.getCapabilities", sessionId = runSession, stopped = stopped) as JSONObject
                        check(capabilities.optBoolean("canCapture") && capabilities.optBoolean("canPerformGestures")) { "请在无障碍设置中开启设备控制" }
                        if (!phoneStarted) {
                            await(if (global) "app.returnToPreviousApp" else "app.goHome", sessionId = runSession, stopped = stopped)
                            phoneStarted = true
                        }
                        updateProgress(local("正在操作手机", "Operating phone"), revised)
                        await("task.status", JSONObject().put("status", "running"), runSession, stopped)
                        val run = await("runtime.runTest", JSONObject().put("source", NativeConversationAgent.verifiedTask(revised, condition)).put("kind", "yaml").put("environment", env), runSession, stopped, 600) as JSONObject
                        run.optJSONArray("reports")?.objects()?.forEach { reports.put(it) }
                        val success = run.optInt("exitCode", 1) == 0
                        PhoneResult(success, run.optString(if (success) "finalResponse" else "failure").ifBlank { run.optJSONArray("output")?.strings()?.takeLast(10)?.joinToString("\n").orEmpty().ifBlank { if (success) "手机操作已完成" else "手机操作失败" } }).also { result ->
                            completedOperations.put(JSONObject().put("instruction", revised).put("success", result.success).put("result", result.result))
                            onMain { find(id)?.put("completedOperations", JSONArray(completedOperations.toString()))?.put("reports", JSONArray(reports.toString())); save(); publish() }
                        }
                    }, event = { event -> onMain {
                        find(id)?.let { TaskLifecycle.event(it, event) }
                        if (event.optString("type") == "plan") progress = progress?.copy(
                            title = local("正在推进任务", "Working on task"), detail = event.getJSONArray("steps").strings().joinToString(" · "))
                        if (event.optString("type") == "accepted") find(event.optString("id"))?.put("status", "running")
                        save(); publish()
                    } }, resumeInputs = TaskLifecycle.pendingInputs(retry?.takeIf { continueOf != null || it.optString("phase") != "completed" }, before), goal = entry.getString("goal"))
                    finalPhase = reply.getString("phase")
                    check(finalPhase != "failed") { reply.getString("reply") }
                    check(reply.optJSONObject("lastPhoneResult")?.optBoolean("success") != false || finalPhase == "waiting_user") { "手机操作未通过最终画面验证" }
                    reply.getString("reply")
                }
            }
            val stopAcknowledged = if (!stopped.get()) true else runCatching {
                stopBarrier?.get(10, TimeUnit.SECONDS)
            }.isSuccess
            val status = if (stopped.get()) "stopped" else if (outcome.isSuccess) "succeeded" else "failed"
            val message = if (stopped.get()) local("任务已停止", "Task stopped") else outcome.getOrElse { it.cause?.message ?: it.message ?: "任务失败" }
            runCatching { await("task.status", JSONObject().put("status", status), runSession) }
            runCatching { await("task.complete", sessionId = runSession) }
            if (global) runCatching { await("app.showGlobalResult", JSONObject().put("message", message), runSession) }
            else if (phoneStarted && !stopped.get() && !backgroundReplies) runCatching { await("app.bringToFront", sessionId = runSession) }
            handler.post {
                if (closed) return@post
                find(id)?.put("status", status)?.put("reports", reports)?.put("completedOperations", completedOperations)?.put(if (status == "succeeded") "reply" else "detail", message)
                find(id)?.let { TaskLifecycle.finish(it, status, finalPhase) }
                find(id)?.let { task ->
                    TaskLifecycle.settleInputs(task, history(), local("补充消息已保存，请重试或继续", "Update saved. Retry or continue."))
                        .forEach { input -> pendingTurns.add(JSONObject().put("text", input.getString("source")).put("global", global).put("continueOf", id)) }
                }
                running = false; activeTaskId = ""; progress = null; confirmation = null; confirmationText = null
                if (restoreDraft != null && data.optString("draft").isEmpty() && pendingAttachment == null) {
                    data.put("draft", restoreDraft); pendingAttachment = restoreAttachment; draftEpoch++
                }
                save(); publish()
                val retained = history().mapNotNull { it.optJSONObject("attachment")?.optString("id") }.toMutableSet()
                pendingAttachment?.optString("id")?.takeIf { it.isNotBlank() }?.let { retained.add(it) }
                pendingTurns.mapNotNull { it.optJSONObject("attachment")?.optString("id") }.forEach { retained.add(it) }
                services.call("app.pruneAttachments", JSONObject().put("ids", JSONArray(retained.toList()))) {}
                if (stopAcknowledged) {
                    if (!consumePendingTurn()) consumeQueue()
                } else {
                    error = local("旧任务尚未确认停止，新消息已保留，请稍后重试。", "Previous task has not confirmed stopping. Your message is retained.")
                    data.put("draft", pendingTurns.joinToString("\n") { it.optString("text") })
                    pendingAttachment = pendingTurns.firstOrNull()?.optJSONObject("attachment")
                    pendingTurns.clear(); draftEpoch++; save(); publish()
                }
            }
        }
    }
    private fun find(id: String) = history().find { it.optString("id") == id }
    private fun updateProgress(title: String, detail: String = "", increment: Boolean = false) = onMain {
        progress = progress?.copy(title = title, detail = detail, operation = "", step = progress!!.step + if (increment) 1 else 0); publish()
    }
    fun phoneProgress(method: String) {
        if (!running || cancellation.get()) return
        progress = progress?.copy(operation = if (method == "device.capture") local("正在查看屏幕", "Viewing screen") else if (method == "device.execute") local("正在执行屏幕操作", "Performing action") else progress!!.operation); publish()
    }
    private fun confirmOperation(text: String, stopped: AtomicBoolean): String {
        if (!ExecutionMode.from(data).requiresConfirmation(text)) return text
        val promise = CompletableFuture<String>()
        onMain { confirmation = promise; confirmationText = text; publish() }
        return waitFor(promise, stopped, 600)
    }
    private fun requestCompletion(model: JSONObject, key: String, messages: JSONArray, sessionId: String, stopped: AtomicBoolean, tools: JSONArray = JSONArray().put(NativeConversationAgent.tool)): JSONObject {
        val response = await("model.request", NativeModelRequest.params(model, key, messages, tools), sessionId, stopped) as JSONObject
        if (response.optInt("status") !in 200..299 && activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debug-only, private diagnostics. Never save the credential or raw response body.
            runCatching {
                val body = JSONObject(String(java.util.Base64.getDecoder().decode(response.getString("bodyBase64"))))
                val error = body.optJSONObject("error") ?: body
                val message = error.optString("message").replace(key, "[REDACTED]")
                    .replace(Regex("(?i)(sk-|ak-|Bearer\\s+)[a-zA-Z0-9._-]+"), "[REDACTED]").take(1_024)
                val code = error.optString("code", error.optString("type")).replace(key, "[REDACTED]").take(80)
                java.io.File(activity.filesDir, "native-model-error.json").writeText(JSONObject()
                    .put("status", response.getInt("status")).put("code", code).put("message", message).toString())
            }
        }
        return NativeModelRequest.decode(response)
    }

    private fun await(method: String, params: JSONObject = JSONObject(), sessionId: String = "native", stopped: AtomicBoolean = AtomicBoolean(false), seconds: Long = 120): Any? {
        check(!stopped.get() && !closed) { "任务已停止" }
        persistence.flush()
        val promise = CompletableFuture<Any?>()
        services.call(method, params, sessionId) { it.fold(promise::complete, promise::completeExceptionally) }
        return waitFor(promise, stopped, seconds)
    }
    private fun <T> waitFor(promise: CompletableFuture<T>, stopped: AtomicBoolean, seconds: Long): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (true) {
            check(!stopped.get() && !closed) { "任务已停止" }
            try { return promise.get(100, TimeUnit.MILLISECONDS) } catch (timeout: TimeoutException) {
                if (System.nanoTime() >= deadline) throw IllegalStateException("请求超时，请稍后重试")
            }
        }
    }
    private fun onMain(action: () -> Unit) {
        if (closed) return
        val done = CompletableFuture<Unit>()
        handler.post { if (!closed) runCatching(action).fold({ done.complete(Unit) }, done::completeExceptionally) else done.complete(Unit) }
        done.get(10, TimeUnit.SECONDS)
    }
    fun stop(clearPending: Boolean = true) {
        if (clearPending) { pendingTurns.clear(); if (ready) save() }
        if (!running) return
        if (cancellation.get()) { publish(); return }
        val barrier = CompletableFuture<Any?>(); stopBarrier = barrier
        cancellation.set(true)
        pi.stop()
        progress = progress?.copy(title = local("正在停止", "Stopping"))
        confirmation?.completeExceptionally(IllegalStateException("任务已停止")); confirmation = null; confirmationText = null
        services.call("task.cancel", sessionId = session) {}
        services.call("runtime.stop") { it.fold(barrier::complete, barrier::completeExceptionally) }
        publish()
    }
    fun back(): Boolean = when {
        confirmationText != null -> { stop(); true }
        preview != null -> { preview = null; publish(); true }
        advancedOpen -> { advancedOpen = false; true }
        settingsOpen -> { settingsOpen = false; publish(); true }
        else -> false
    }
    fun removeReports(ids: Set<String>) {
        if (!ready || ids.isEmpty()) return
        history().forEach { e -> e.put("reports", JSONArray(e.optJSONArray("reports")?.objects().orEmpty().filterNot { it.optString("reportId") in ids })) }
        save(); publish()
    }
    private fun fail(t: Throwable) { error = t.message ?: "操作失败"; errorEpoch++; publish() }
    fun selectPreset(id: String) { if (id !in ModelProfiles.ids || running) return; data.put("preset", id); settingsStatus = ""; save(); publish() }
    fun saveModel(profile: JSONObject, replacementKey: String = "", reset: Boolean = false) {
        if (running) return
        settingsSection = "model"
        runCatching {
            if (reset) credentials.delete(preset) else if (replacementKey.isNotBlank()) credentials.set(preset, replacementKey.trim())
            data.getJSONObject("profiles").put(preset, if (reset) ModelProfiles.defaults(preset) else JSONObject().apply { listOf("baseUrl", "modelName", "modelFamily").forEach { put(it, profile.optString(it)) } })
            save(); settingsStatus = local("已保存", "Saved"); publish()
        }.onFailure { settingsStatus = it.message ?: "保存失败" }
    }
    fun preference(name: String, value: Any) { if (!running) { data.put(name, value); save(); publish() } }
    fun saveSpeech(provider: String, key: String) {
        settingsSection = "speech"
        runCatching {
            require(provider in listOf("volcengine", "deepgram"))
            if (key.isNotBlank()) credentials.set("speech-$provider", key.trim())
            activity.getSharedPreferences("tapilot-speech", 0).edit().putString("provider", provider).commit()
            preference("speechProvider", provider); settingsStatus = local("语音配置已保存", "Speech configuration saved")
        }.onFailure { settingsStatus = it.message ?: "保存失败" }
    }
    fun resetSpeech(provider: String) {
        if (running) return
        settingsSection = "speech"
        runCatching {
            require(provider in listOf("volcengine", "deepgram"))
            credentials.delete("speech-$provider")
            settingsStatus = local("已清除该转写服务的密钥", "Transcription key removed"); publish()
        }.onFailure { settingsStatus = it.message.orEmpty() }
    }
    fun checkSpeech(provider: String) = settingsCall("speech.checkConnection", JSONObject().put("provider", provider))
    fun settingsCall(method: String, params: JSONObject = JSONObject()) {
        if (settingsBusy || running) return
        settingsSection = if (method.startsWith("speech.")) "speech" else "general"
        settingsBusy = true; settingsStatus = local("正在检查…", "Checking…")
        services.call(method, params) { result ->
            settingsBusy = false
            settingsStatus = result.fold({ local("检查通过", "Check passed") }, { it.message ?: "检查失败" }); publish()
        }
    }
    fun testModel() {
        if (settingsBusy || running) return
        settingsSection = "model"
        settingsBusy = true; settingsStatus = local("正在检查模型…", "Checking model…")
        val model = profile()
        worker.execute {
            val result = runCatching {
                val key = credentials.get(preset).orEmpty()
                check(key.isNotBlank()) { "请先保存模型 API Key" }
                requestCompletion(model, key, JSONArray().put(JSONObject().put("role", "user").put("content", "Reply OK.")), UUID.randomUUID().toString(), AtomicBoolean(false))
            }
            handler.post { if (!closed) { settingsBusy = false; settingsStatus = result.fold({ local("模型连接成功", "Model connection succeeded") }, { it.cause?.message ?: it.message ?: "模型连接失败" }) } }
        }
    }
    fun clearHistory() {
        if (running) return
        data.put("history", JSONArray()).put("active", "chat-${UUID.randomUUID()}"); preview = null; pendingAttachment = null; save(); publish()
        services.call("app.pruneAttachments", JSONObject().put("ids", JSONArray())) {}
    }
    fun executeYaml(source: String) { advancedOpen = false; start(source, file = null, kind = "yaml") }
    override fun close() { stop(); if (ready) persistence.saveDraft(data.optString("draft")); pi.close(); closed = true; worker.shutdownNow(); persistence.close(); handler.removeCallbacksAndMessages(null) }
}
