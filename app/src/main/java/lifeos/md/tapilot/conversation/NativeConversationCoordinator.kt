package lifeos.md.tapilot.conversation

import android.app.Activity
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
    private val activity: Activity,
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
    private val credentials = ModelCredentialStore(activity)
    @Volatile private var closed = false
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
    private var session = ""
    private val queuedContexts = ArrayDeque<JSONObject>()

    val preset: String get() = data.optString("preset", "doubao")
    fun profile(id: String = preset): JSONObject = JSONObject((data.optJSONObject("profiles")?.optJSONObject(id) ?: ModelProfiles.defaults(id)).toString())
    fun hasKey(id: String) = credentials.getAll()[id]?.isNotBlank() == true
    fun initialize() {
        worker.execute {
            val loaded = runCatching { store.read().also { state ->
                state.getJSONArray("history").objects().filter { it.optString("status") == "running" }
                    .forEach { it.put("status", "stopped").put("detail", "应用中断，任务已停止") }
                store.save(state)
            } }
            handler.post {
                if (closed) return@post
                loaded.fold(onSuccess = { data = it; ready = true; publish(); receiveLaunchContext() }, onFailure = { fail(it) })
            }
        }
    }
    private fun save() { store.save(data); data = JSONObject(data.toString()) }
    private fun history() = data.getJSONArray("history").objects()
    private fun conversation(entry: JSONObject) = entry.optString("conversationId", entry.optString("id"))
    private fun active() = data.getString("active")
    private fun local(zh: String, en: String) = if (AppLanguage.resolved(activity) == "zh") zh else en
    fun publish() {
        if (closed) return
        val history = history()
        val messages = history.filter { conversation(it) == active() }.sortedBy { it.optLong("createdAt") }.flatMap { e ->
            val id = e.getString("id")
            val status = e.optString("status")
            val reports = e.optJSONArray("reports")?.objects().orEmpty()
            val report = reports.lastOrNull { it.optString("kind") == "agent" } ?: reports.lastOrNull()
            val response = e.optString("reply").ifBlank {
                when (status) { "stopped" -> local("任务已停止", "Task stopped"); "failed" -> e.optString("detail"); else -> "" }
            }
            listOf(ChatMessage("$id-user", "user", e.optString("inputText", e.optString("source")).ifBlank { e.optString("source") }, status, "", id,
                ChatAttachment.parse(e.optJSONObject("attachment"))), ChatMessage("$id-assistant", "assistant", response, status, report?.optString("reportId").orEmpty(), id))
        }
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
        runCatching {
            val action = value.getString("action")
            when (action) {
                "draft" -> { data.put("draft", value.optString("text")); save() }
                "send" -> if (!running) start(value.optString("text", data.optString("draft")))
                "stop" -> stop()
                "settings" -> if (!running) { settingsOpen = true; settingsStatus = "" }
                "advanced" -> if (!running && data.optBoolean("advanced")) advancedOpen = true
                "new" -> if (!running) { data.put("active", "chat-${UUID.randomUUID()}"); preview = null; save() }
                "select" -> if (!running && history().any { conversation(it) == value.optString("id") }) { data.put("active", value.getString("id")); preview = null; save() }
                "attachment" -> if (!running) pendingAttachment = value.getJSONObject("attachment")
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
            pendingAttachment = file; draftEpoch++; save(); publish(); focus()
        }
    }
    private fun start(text: String, file: JSONObject? = pendingAttachment, retryOf: String? = null, global: Boolean = false,
                      restoreDraft: String? = null, restoreAttachment: JSONObject? = null, kind: String = "task") {
        if (running || (text.isBlank() && file == null)) return
        val model = profile()
        val key = credentials.getAll()[preset].orEmpty()
        if (key.isBlank() || model.optString("modelName").isBlank() || model.optString("modelFamily").isBlank()) {
            fail(IllegalStateException(local("请先配置模型 API", "Configure model API first"))); settingsOpen = true; return
        }
        runCatching { ModelProfiles.endpoint(model.getString("baseUrl")) }.onFailure { fail(it); return }
        running = true; error = ""; preview = null; settingsOpen = false; advancedOpen = false
        val stopped = AtomicBoolean(false); cancellation = stopped
        session = UUID.randomUUID().toString(); val runSession = session
        val cid = active()
        val id = UUID.randomUUID().toString()
        val before = history().map { JSONObject(it.toString()) }
        val retry = before.find { it.optString("id") == retryOf }
        val prior = before.filter { conversation(it) == cid && (retry == null || (it.optString("id") != retryOf && it.optLong("createdAt") <= retry.optLong("createdAt"))) }
        val source = text.trim().ifBlank { local("请查看附件", "Please inspect the attachment") }
        val entry = JSONObject().put("id", id).put("conversationId", cid).put("createdAt", System.currentTimeMillis())
            .put("source", source).put("inputText", text).put("kind", kind).put("modelName", model.getString("modelName"))
            .put("status", "running").put("reports", JSONArray())
        data.put("history", JSONArray(listOf(entry) + before).let { all -> JSONArray(all.objects().take(200)) })
        data.put("draft", ""); pendingAttachment = null; draftEpoch++; save()
        progress = ChatProgress(local("正在理解任务", "Understanding task"), "", "", System.currentTimeMillis(), 0)
        publish()
        worker.execute {
            val reports = JSONArray()
            var phoneStarted = false
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
                    val attachments = prior.filter { it.optString("status") == "succeeded" && it.has("attachment") && it.optString("reply").isNotBlank() }
                        .sortedBy { it.optLong("createdAt") }.takeLast(2).associate { e ->
                            val ref = e.getJSONObject("attachment")
                            e.getString("id") to runCatching { await("app.readAttachment", JSONObject().put("id", ref.getString("id")), runSession, stopped) as JSONObject }
                                .getOrElse { JSONObject().put("text", "之前的附件已不可用。不能猜测内容，请用户重新提供。") }
                        }
                    val modelSource = source + saved?.optString("text").orEmpty().takeIf { it.isNotBlank() }?.let { "\n\n文件内容：\n${it.take(30_000)}" }.orEmpty() +
                        if (saved?.optString("name")?.contains("屏幕") == true) "\n图片是用户先前保存的屏幕快照，不是当前实时画面。" else ""
                    val messages = NativeConversationAgent.messages(prior, modelSource, AppLanguage.resolved(activity) == "zh", attachments, saved?.optJSONArray("images")?.strings().orEmpty())
                    val reply = NativeConversationAgent.run(messages, stopped::get, complete = { chat ->
                        updateProgress(if (phoneStarted) local("正在整理结果", "Preparing reply") else local("正在理解任务", "Understanding task"))
                        requestCompletion(model, key, chat, runSession, stopped)
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
                        PhoneResult(success, run.optString(if (success) "finalResponse" else "failure").ifBlank { run.optJSONArray("output")?.strings()?.takeLast(10)?.joinToString("\n").orEmpty().ifBlank { if (success) "手机操作已完成" else "手机操作失败" } })
                    })
                    check(reply.lastPhoneResult?.success != false) { "手机操作未通过最终画面验证：${reply.lastPhoneResult?.result}" }
                    reply.reply
                }
            }
            val status = if (stopped.get()) "stopped" else if (outcome.isSuccess) "succeeded" else "failed"
            val message = if (stopped.get()) local("任务已停止", "Task stopped") else outcome.getOrElse { it.cause?.message ?: it.message ?: "任务失败" }
            runCatching { await("task.status", JSONObject().put("status", status), runSession) }
            runCatching { await("task.complete", sessionId = runSession) }
            if (global && !phoneStarted) runCatching { await("app.returnToPreviousApp", sessionId = runSession) }
            if (global) runCatching { await("app.showGlobalResult", JSONObject().put("message", message), runSession) }
            else if (phoneStarted) runCatching { await("app.bringToFront", sessionId = runSession) }
            handler.post {
                if (closed) return@post
                find(id)?.put("status", status)?.put("reports", reports)?.put(if (status == "succeeded") "reply" else "detail", message)
                running = false; progress = null; confirmation = null; confirmationText = null
                if (restoreDraft != null && data.optString("draft").isEmpty() && pendingAttachment == null) {
                    data.put("draft", restoreDraft); pendingAttachment = restoreAttachment; draftEpoch++
                }
                save(); publish()
                val retained = history().mapNotNull { it.optJSONObject("attachment")?.optString("id") }.toMutableSet()
                pendingAttachment?.optString("id")?.takeIf { it.isNotBlank() }?.let { retained.add(it) }
                services.call("app.pruneAttachments", JSONObject().put("ids", JSONArray(retained.toList()))) {}
                consumeQueue()
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
        if (!data.optBoolean("confirmEveryStep") && !Regex("发送|转账|付款|支付|购买|下单|删除|清空|卸载|发布|提交|授权|分享给|send|transfer|pay|purchase|delete|uninstall|publish|submit", RegexOption.IGNORE_CASE).containsMatchIn(text)) return text
        val promise = CompletableFuture<String>()
        onMain { confirmation = promise; confirmationText = text; publish() }
        return waitFor(promise, stopped, 600)
    }
    private fun requestCompletion(model: JSONObject, key: String, messages: JSONArray, sessionId: String, stopped: AtomicBoolean): JSONObject {
        val response = await("model.request", NativeModelRequest.params(model, key, messages), sessionId, stopped) as JSONObject
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
        val promise = CompletableFuture<Any?>()
        services.call(method, params, sessionId) { it.fold(promise::complete, promise::completeExceptionally) }
        return waitFor(promise, stopped, seconds)
    }
    private fun <T> waitFor(promise: CompletableFuture<T>, stopped: AtomicBoolean, seconds: Long): T {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (true) {
            check(!stopped.get() && !closed) { "任务已停止" }
            try { return promise.get(1, TimeUnit.SECONDS) } catch (timeout: TimeoutException) {
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
    fun stop() {
        if (!running) return
        cancellation.set(true)
        confirmation?.completeExceptionally(IllegalStateException("任务已停止")); confirmation = null; confirmationText = null
        services.call("task.cancel", sessionId = session) {}
        services.call("runtime.stop") {}
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
        val model = profile(); val key = credentials.getAll()[preset].orEmpty()
        worker.execute {
            val result = runCatching {
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
    override fun close() { stop(); closed = true; worker.shutdownNow(); handler.removeCallbacksAndMessages(null) }
}
