package lifeos.md.tapilot.bridge

import android.content.Context
import android.util.Log
import lifeos.md.tapilot.AppLanguage
import lifeos.md.tapilot.TaskExecutionStatus
import lifeos.md.tapilot.TaskRuntimeService
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.report.ReportRepository
import lifeos.md.tapilot.runtime.InstalledNodeRuntime
import lifeos.md.tapilot.runtime.NodeRuntimeEvent
import lifeos.md.tapilot.runtime.NodeRuntimeListener
import lifeos.md.tapilot.runtime.NodeRuntimeProcessSupervisor
import lifeos.md.tapilot.runtime.NodeRuntimeProvisioner
import lifeos.md.tapilot.runtime.NodeTestRunRequest
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

class NodeTestRunCoordinator(
    context: Context,
    private val reportRepository: ReportRepository,
    private val supervisor: NodeRuntimeProcessSupervisor = NodeRuntimeProcessSupervisor(),
    private val onProgress: (String) -> Unit = {},
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var completion = CompletableFuture.completedFuture(Unit)
    @Volatile private var activeSessionId: String? = null
    @Volatile private var stopRegistration: ActiveTaskStopRegistry.Registration? = null

    fun run(
        source: String,
        kind: String,
        environment: Map<String, String>,
        callback: (Result<JSONObject>) -> Unit,
    ) {
        require(source.isNotBlank()) { "YAML or task source must not be blank" }
        require(source.length <= MAX_SOURCE_CHARACTERS) { "YAML source is too large" }
        require(kind == "task" || kind == "yaml") { "Unsupported Node run kind: $kind" }
        check(completion.isDone && !supervisor.isRunning()) { "A Node runtime task is already running" }
        // Acquiring can fail while a wireless controller owns the device. Do not leave
        // an unfinished completion future behind when the task was never started.
        stopRegistration = ActiveTaskStopRegistry.register(::stop)
        completion = CompletableFuture()
        stopRequested.set(false)
        updateStatus(TaskExecutionStatus.STARTING)
        executor.execute {
            runCatching {
                val runtime = NodeRuntimeProvisioner(appContext).install()
                throwIfStopRequested()
                val projectRoot = prepareProject(source, kind)
                throwIfStopRequested()
                start(runtime, projectRoot, environment, callback)
            }.onFailure { error ->
                val finalError = if (stopRequested.get()) NodeRunStoppedException() else error
                updateStatus(
                    if (finalError is NodeRunStoppedException) {
                        TaskExecutionStatus.STOPPED
                    } else {
                        TaskExecutionStatus.FAILED
                    },
                    finalError.message,
                )
                clearStopRegistration()
                completion.complete(Unit)
                callback(Result.failure(finalError))
            }
        }
    }

    fun stop(): Boolean {
        val taskWasActive = stopRegistration != null
        stopRequested.set(true)
        activeSessionId?.let { MidsceneAccessibilityService.current()?.cancelSession(it) }
        return supervisor.stop() || taskWasActive
    }

    /** Called off the UI thread; includes cancellation during runtime preparation. */
    fun stopAndWait(): Boolean {
        val finished = completion
        val stopped = stop()
        finished.get(8, TimeUnit.SECONDS)
        return stopped
    }

    private fun throwIfStopRequested() {
        if (stopRequested.get()) throw NodeRunStoppedException()
    }

    private fun clearStopRegistration() {
        val registration = stopRegistration
        stopRegistration = null
        ActiveTaskStopRegistry.unregister(registration)
    }

    private fun prepareProject(source: String, kind: String): File {
        val runsRoot = File(appContext.filesDir, "node-runs")
        check(runsRoot.exists() || runsRoot.mkdirs()) { "Unable to create Node run directory" }
        val projectRoot = File(runsRoot, UUID.randomUUID().toString())
        check(projectRoot.mkdirs()) { "Unable to create Node test project" }
        val testSource = if (kind == "task") NaturalLanguageTaskWorkflow.asYaml(source) else source
        File(projectRoot, "task.yaml").writeText(testSource)
        pruneRuns(runsRoot, projectRoot)
        return projectRoot
    }

    private fun start(
        runtime: InstalledNodeRuntime,
        projectRoot: File,
        environment: Map<String, String>,
        callback: (Result<JSONObject>) -> Unit,
    ) {
        val completed = AtomicBoolean(false)
        val logs = ArrayDeque<String>()
        var reportPath: String? = null
        var testRunReportPath: String? = null
        var agentReports = JSONArray()
        var finalResponse: String? = null
        var failureMessage: String? = null

        fun finish(result: Result<JSONObject>) {
            if (!completed.compareAndSet(false, true)) return
            activeSessionId?.let { MidsceneAccessibilityService.current()?.completeSession(it) }
            activeSessionId = null
            clearStopRegistration()
            completion.complete(Unit)
            callback(result)
        }

        supervisor.runTest(
            runtime,
            NodeTestRunRequest(
                projectRoot = projectRoot,
                resultDirectory = File(projectRoot, "results"),
                configPath = File(runtime.runtimeDirectory, "android.config.mjs").absolutePath,
                projects = listOf("android-accessibility"),
                environment = mapOf(
                    "MIDSCENE_MODEL_TIMEOUT" to "45000",
                    "MIDSCENE_MODEL_RETRY_COUNT" to "1",
                ) + environment + (
                    "MIDSCENE_ANDROID_ASSISTANT_LANGUAGE" to AppLanguage.resolved(appContext)
                ),
            ),
            object : NodeRuntimeListener {
                override fun onLog(line: String) {
                    if (logs.size == MAX_LOG_LINES) logs.removeFirst()
                    logs.addLast(line)
                    Log.d(LOG_TAG, "Node test: $line")
                }

                override fun onEvent(event: NodeRuntimeEvent) {
                    val payload = JSONObject(event.payload)
                    when (event.type) {
                        "rpc.request" -> dispatchRpc(payload)
                        "test.started" -> updateStatus(TaskExecutionStatus.RUNNING)
                        "test.completed" -> {
                            reportPath = payload.optString("reportPath")
                            testRunReportPath = payload.optString("testRunReportPath")
                            agentReports = payload.optJSONArray("agentReports") ?: JSONArray()
                            finalResponse = payload.optString("finalResponse")
                                .takeIf { it.isNotBlank() }
                            failureMessage = payload.optString("failure")
                                .takeIf { it.isNotBlank() }
                        }
                        "run.failed" -> failureMessage = payload.optString("message")
                    }
                }

                override fun onComplete(exitCode: Int) {
                    if (stopRequested.get()) {
                        updateStatus(TaskExecutionStatus.STOPPED)
                        finish(Result.failure(NodeRunStoppedException()))
                        return
                    }
                    runCatching {
                        updateStatus(TaskExecutionStatus.FINALIZING)
                        val importedReports = JSONArray()
                        for (index in 0 until agentReports.length()) {
                            val descriptor = agentReports.getJSONObject(index)
                            val source = File(descriptor.getString("path"))
                            val reportId = reportRepository.importFile(source)
                            importedReports.put(
                                JSONObject()
                                    .put("reportId", reportId)
                                    .put("kind", "agent")
                                    .put("title", descriptor.optString("caseName", "Agent report"))
                                    .putOptional("projectName", descriptor.optString("projectName"))
                                    .putOptional("caseName", descriptor.optString("caseName"))
                                    .putOptional("attempt", descriptor.optInt("attempt").takeIf { it > 0 }),
                            )
                        }
                        val summarySource = testRunReportPath?.takeIf { it.isNotBlank() }?.let(::File)
                        if (summarySource?.isFile == true) {
                            importedReports.put(
                                JSONObject()
                                    .put("reportId", reportRepository.importFile(summarySource))
                                    .put("kind", "test-runner")
                                    .put("title", "Test Runner summary"),
                            )
                        }
                        if (importedReports.length() == 0) {
                            val source = reportPath?.takeIf { it.isNotBlank() }?.let(::File)
                                ?: error("Node Test Runner did not return a report")
                            importedReports.put(
                                JSONObject()
                                    .put("reportId", reportRepository.importFile(source))
                                    .put("kind", "test-runner")
                                    .put("title", "Execution report"),
                            )
                        }
                        val primaryReportId = (0 until importedReports.length())
                            .map { importedReports.getJSONObject(it) }
                            .lastOrNull { it.getString("kind") == "agent" }
                            ?.getString("reportId")
                            ?: importedReports.getJSONObject(0).getString("reportId")
                        JSONObject()
                            .put("exitCode", exitCode)
                            .put("reportId", primaryReportId)
                            .put("reports", importedReports)
                            .put("output", JSONArray(logs.toList()))
                            .putOptional("finalResponse", finalResponse)
                            .put(
                                "failure",
                                failureMessage?.takeIf { it.isNotBlank() }
                                    ?: if (exitCode == 0) JSONObject.NULL
                                    else "Node Test Runner exited with code $exitCode",
                            )
                    }.fold(
                        onSuccess = { result ->
                            if (exitCode == 0) {
                                updateStatus(TaskExecutionStatus.SUCCEEDED)
                            } else {
                                updateStatus(
                                    TaskExecutionStatus.FAILED,
                                    result.optString("failure"),
                                )
                            }
                            finish(Result.success(result))
                        },
                        onFailure = { error ->
                            updateStatus(TaskExecutionStatus.FAILED, error.message)
                            finish(Result.failure(error))
                        },
                    )
                }

                override fun onFailure(error: Throwable) {
                    val finalError = nodeRunFailure(error, stopRequested.get())
                    updateStatus(
                        if (finalError is NodeRunStoppedException) {
                            TaskExecutionStatus.STOPPED
                        } else {
                            TaskExecutionStatus.FAILED
                        },
                        finalError.message,
                    )
                    finish(Result.failure(finalError))
                }
            },
        )
    }

    private fun dispatchRpc(request: JSONObject) {
        val requestId = request.getString("requestId")
        val sessionId = request.getString("sessionId")
        activeSessionId = sessionId
        val params = request.optJSONObject("params") ?: JSONObject()
        val service = MidsceneAccessibilityService.current()
        appContext.mainExecutor.execute {
            runCatching {
                onProgress(request.getString("method"))
                when (request.getString("method")) {
                    "device.getCapabilities" -> respondSuccess(
                        requestId,
                        (service?.capabilityDetails() ?: JSONObject())
                            .put("protocolVersion", 1)
                            .put("canCapture", service != null)
                            .put("canPerformGestures", service != null)
                            .put("canInputText", service != null),
                    )
                    "device.getSize" -> {
                        val metrics = appContext.resources.displayMetrics
                        respondSuccess(
                            requestId,
                            JSONObject().put("width", metrics.widthPixels)
                                .put("height", metrics.heightPixels),
                        )
                    }
                    "device.capture" -> requireService(service).capture(sessionId) { result ->
                        result.fold(
                            onSuccess = { capture ->
                                respondSuccess(
                                    requestId,
                                    JSONObject()
                                        .put("dataUrl", capture.dataUrl)
                                        .put(
                                            "geometry",
                                            JSONObject()
                                                .put("frameId", capture.frameId)
                                                .put("capturedAt", capture.capturedAt)
                                                .put("displayId", 0)
                                                .put("rotation", capture.rotation)
                                                .put("screenWidth", capture.screenWidth)
                                                .put("screenHeight", capture.screenHeight)
                                                .put("imageWidth", capture.imageWidth)
                                                .put("imageHeight", capture.imageHeight),
                                        ),
                                )
                            },
                            onFailure = { respondError(requestId, it) },
                        )
                    }
                    "device.execute" -> {
                        params.optString("frameId").takeIf { it.isNotBlank() }?.let {
                            requireService(service).validateFrame(it)
                        }
                        val action = NativeActionParser.parse(params.getJSONObject("action"))
                        requireService(service).execute(sessionId, action) { result ->
                            result.fold(
                                onSuccess = { respondSuccess(requestId, JSONObject.NULL) },
                                onFailure = { respondError(requestId, it) },
                            )
                        }
                    }
                    "task.cancel" -> {
                        service?.cancelSession(sessionId)
                        respondSuccess(requestId, JSONObject.NULL)
                    }
                    "task.complete" -> {
                        service?.completeSession(sessionId)
                        respondSuccess(requestId, JSONObject.NULL)
                    }
                    "task.status" -> {
                        val status = TaskExecutionStatus.valueOf(
                            params.getString("status").uppercase(),
                        )
                        updateStatus(status, params.optString("detail"))
                        respondSuccess(requestId, JSONObject.NULL)
                    }
                    else -> throw UnsupportedOperationException(
                        "Unsupported Node RPC method: ${request.getString("method")}",
                    )
                }
            }.onFailure { respondError(requestId, it) }
        }
    }

    private fun respondSuccess(requestId: String, result: Any?) {
        supervisor.respondToRpc(
            JSONObject().put("requestId", requestId).put("ok", true).put("result", result),
        )
    }

    private fun respondError(requestId: String, error: Throwable) {
        runCatching {
            supervisor.respondToRpc(
                JSONObject()
                    .put("requestId", requestId)
                    .put("ok", false)
                    .put(
                        "error",
                        JSONObject()
                            .put("code", errorCode(error))
                            .put("message", error.message ?: error.javaClass.simpleName),
                    ),
            )
        }.onFailure { Log.w(LOG_TAG, "Unable to send Node RPC error", it) }
    }

    private fun updateStatus(status: TaskExecutionStatus, detail: String? = null) {
        MidsceneAccessibilityService.current()?.updateTaskStatus(status, detail)
        runCatching { TaskRuntimeService.report(appContext, status) }
            .onFailure { Log.w(LOG_TAG, "Unable to update task notification", it) }
    }

    private fun requireService(service: MidsceneAccessibilityService?): MidsceneAccessibilityService =
        service ?: throw AccessibilityDisabledException()

    private fun errorCode(error: Throwable): String = when (error) {
        is AccessibilityDisabledException -> "ACCESSIBILITY_DISABLED"
        is MidsceneAccessibilityService.SessionCancelledException -> "SESSION_CANCELLED"
        is MidsceneAccessibilityService.StaleFrameException -> "STALE_FRAME"
        is UnsupportedOperationException -> "UNSUPPORTED_ACTION"
        is IllegalArgumentException -> "INVALID_REQUEST"
        else -> "INTERNAL_ERROR"
    }

    private fun pruneRuns(root: File, active: File) {
        root.listFiles { file -> file.isDirectory && file != active }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_RETAINED_RUN_DIRECTORIES - 1)
            ?.forEach { stale -> stale.deleteRecursively() }
    }

    override fun close() {
        stop()
        clearStopRegistration()
        supervisor.close()
        executor.shutdownNow()
    }

    class NodeRunStoppedException : IllegalStateException("用户停止了任务")

    private class AccessibilityDisabledException :
        IllegalStateException("请先在系统设置中开启 Tapilot 辅助功能")

    companion object {
        private const val LOG_TAG = "MidsceneNodeTest"
        private const val MAX_SOURCE_CHARACTERS = 1_000_000
        private const val MAX_LOG_LINES = 200
        private const val MAX_RETAINED_RUN_DIRECTORIES = 3
    }
}

internal fun nodeRunFailure(error: Throwable, stopRequested: Boolean): Throwable =
    if (stopRequested) NodeTestRunCoordinator.NodeRunStoppedException() else error

private fun JSONObject.putOptional(key: String, value: Any?): JSONObject {
    if (value != null && value.toString().isNotBlank()) put(key, value)
    return this
}
