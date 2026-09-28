package lifeos.md.tapilot.bridge

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.ContentValues
import android.provider.MediaStore
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import lifeos.md.tapilot.ReportActivity
import lifeos.md.tapilot.TaskRuntimeService
import lifeos.md.tapilot.accessibility.MidsceneAccessibilityService
import lifeos.md.tapilot.accessibility.FloatingBallPreferences
import lifeos.md.tapilot.accessibility.EdgeEffectGeometryMode
import lifeos.md.tapilot.accessibility.EdgeEffectGeometrySettings
import lifeos.md.tapilot.accessibility.EdgeEffectPreferences
import lifeos.md.tapilot.TaskExecutionStatus
import lifeos.md.tapilot.report.ReportRepository
import lifeos.md.tapilot.runtime.InstalledNodeRuntime
import lifeos.md.tapilot.runtime.NodeRuntimeEvent
import lifeos.md.tapilot.runtime.NodeRuntimeListener
import lifeos.md.tapilot.runtime.NodeRuntimeProcessSupervisor
import lifeos.md.tapilot.runtime.NodeRuntimeProvisioner
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import androidx.core.content.FileProvider

class NativeJavascriptBridge(
    private val webView: WebView,
    private val onLanguageChanged: (String) -> Unit = {},
    private val onThemeChanged: (String) -> Unit = {},
) : AutoCloseable {
    private val modelTransport = ModelTransport()
    private val modelCredentialStore = ModelCredentialStore(webView.context)
    private val speechTranscriber = SpeechTranscriber(webView.context as Activity)
    private val reportRepository = ReportRepository(File(webView.context.filesDir, "reports")).apply {
        cleanupStaleDrafts()
    }
    private val nodeRuntimeSupervisor = NodeRuntimeProcessSupervisor()
    private val nodeTestRunCoordinator = NodeTestRunCoordinator(
        webView.context,
        reportRepository,
        nodeRuntimeSupervisor,
    )

    @JavascriptInterface
    fun postMessage(payload: String) {
        webView.post {
            runCatching { handle(JSONObject(payload)) }
                .onFailure { error ->
                    val requestId = runCatching { JSONObject(payload).optString("requestId") }
                        .getOrDefault("")
                    respondError(requestId, error)
                }
        }
    }

    private fun handle(request: JSONObject) {
        require(request.getInt("protocolVersion") == BridgeProtocol.VERSION) {
            "Unsupported protocol version"
        }
        val requestId = request.getString("requestId")
        val sessionId = request.getString("sessionId")
        val method = request.getString("method")
        val service = MidsceneAccessibilityService.current()

        when (method) {
            "device.getCapabilities" -> respondSuccess(
                requestId,
                (service?.capabilityDetails() ?: JSONObject())
                    .put("protocolVersion", BridgeProtocol.VERSION)
                    .put("canCapture", service != null)
                    .put("canPerformGestures", service != null)
                    .put("canInputText", service != null),
            )
            "device.getSize" -> {
                val bounds = webView.context.resources.displayMetrics
                respondSuccess(
                    requestId,
                    JSONObject().put("width", bounds.widthPixels).put("height", bounds.heightPixels),
                )
            }
            "device.capture" -> requireService(service).capture(sessionId) { result ->
                result.fold(
                    onSuccess = { capture ->
                        val geometry = JSONObject()
                            .put("frameId", capture.frameId)
                            .put("capturedAt", capture.capturedAt)
                            .put("displayId", 0)
                            .put("rotation", capture.rotation)
                            .put("screenWidth", capture.screenWidth)
                            .put("screenHeight", capture.screenHeight)
                            .put("imageWidth", capture.imageWidth)
                            .put("imageHeight", capture.imageHeight)
                        respondSuccess(
                            requestId,
                            JSONObject().put("dataUrl", capture.dataUrl).put("geometry", geometry),
                        )
                    },
                    onFailure = { respondError(requestId, it) },
                )
            }
            "device.execute" -> {
                val params = request.getJSONObject("params")
                val action = NativeActionParser.parse(params.getJSONObject("action"))
                params.optString("frameId").takeIf { it.isNotBlank() }?.let {
                    requireService(service).validateFrame(it)
                }
                requireService(service).execute(sessionId, action) { result ->
                    result.fold(
                        onSuccess = { respondSuccess(requestId, JSONObject.NULL) },
                        onFailure = { respondError(requestId, it) },
                    )
                }
            }
            "task.cancel" -> {
                service?.cancelSession(sessionId)
                modelTransport.cancelSession(sessionId)
                respondSuccess(requestId, JSONObject.NULL)
            }
            "task.complete" -> {
                service?.completeSession(sessionId)
                modelTransport.completeSession(sessionId)
                respondSuccess(requestId, JSONObject.NULL)
            }
            "task.status" -> {
                val params = request.getJSONObject("params")
                val status = runCatching {
                    TaskExecutionStatus.valueOf(params.getString("status").uppercase())
                }.getOrElse { throw IllegalArgumentException("Unsupported task status") }
                service?.updateTaskStatus(
                    status,
                    params.optString("detail").takeIf { it.isNotBlank() },
                )
                runCatching { TaskRuntimeService.report(webView.context, status) }
                    .onFailure { Log.w(LOG_TAG, "Unable to update task notification", it) }
                respondSuccess(requestId, JSONObject.NULL)
            }
            "model.request" -> modelTransport.execute(
                requestId,
                sessionId,
                request.getJSONObject("params"),
            ) { result ->
                result.fold(
                    onSuccess = { respondSuccess(requestId, it) },
                    onFailure = { respondError(requestId, it) },
                )
            }
            "model.cancel" -> {
                modelTransport.cancelRequest(
                    request.getJSONObject("params").getString("targetRequestId"),
                )
                respondSuccess(requestId, JSONObject.NULL)
            }
            "app.getModelCredentials" -> respondSuccess(
                requestId,
                JSONObject(modelCredentialStore.getAll().filterKeys { !it.startsWith("speech-") }),
            )
            "app.setModelCredential" -> {
                val params = request.getJSONObject("params")
                modelCredentialStore.set(params.getString("preset"), params.getString("apiKey"))
                respondSuccess(requestId, JSONObject.NULL)
            }
            "app.deleteModelCredential" -> {
                modelCredentialStore.delete(request.getJSONObject("params").getString("preset"))
                respondSuccess(requestId, JSONObject.NULL)
            }
            "speech.hasCredential" -> respondSuccess(
                requestId,
                JSONObject().put("configured", modelCredentialStore.getAll()[speechPreset(request)]?.isNotBlank() == true),
            )
            "speech.setCredential" -> {
                modelCredentialStore.set(speechPreset(request), request.getJSONObject("params").getString("apiKey"))
                respondSuccess(requestId, JSONObject.NULL)
            }
            "speech.start" -> {
                val params = request.getJSONObject("params")
                val provider = params.getString("provider")
                val language = params.optString("language", "en-US")
                val key = modelCredentialStore.getAll()[speechPreset(request)] ?: error("Speech API Key is missing")
                speechTranscriber.start(provider, key, language) { outcome ->
                    outcome.fold(
                        onSuccess = { respondSuccess(requestId, JSONObject.NULL) },
                        onFailure = { respondError(requestId, it) },
                    )
                }
            }
            "speech.cancel" -> {
                speechTranscriber.cancel()
                respondSuccess(requestId, JSONObject.NULL)
            }
            "speech.finish" -> {
                try {
                    speechTranscriber.finish { outcome ->
                        outcome.fold(
                            onSuccess = { respondSuccess(requestId, JSONObject().put("text", it)) },
                            onFailure = { respondError(requestId, it) },
                        )
                    }
                } catch (error: Throwable) {
                    speechTranscriber.cancel()
                    throw error
                }
            }
            "report.begin" -> executeReportOperation(requestId) {
                JSONObject().put("reportId", reportRepository.begin())
            }
            "report.append" -> executeReportOperation(requestId) {
                val params = request.getJSONObject("params")
                reportRepository.append(
                    params.getString("reportId"),
                    params.getString("chunk"),
                )
                JSONObject.NULL
            }
            "report.finish" -> executeReportOperation(requestId) {
                val reportId = request.getJSONObject("params").getString("reportId")
                reportRepository.finish(reportId)
                JSONObject.NULL
            }
            "report.abort" -> executeReportOperation(requestId) {
                val reportId = request.getJSONObject("params").getString("reportId")
                reportRepository.abort(reportId)
                JSONObject.NULL
            }
            "report.open" -> {
                val reportId = request.getJSONObject("params").getString("reportId")
                check(reportRepository.reportFile(reportId).isFile) {
                    "Report does not exist: $reportId"
                }
                webView.context.startActivity(
                    Intent(webView.context, ReportActivity::class.java)
                        .putExtra(ReportActivity.EXTRA_REPORT_ID, reportId),
                )
                respondSuccess(requestId, JSONObject.NULL)
            }
            "report.openExternal" -> {
                val report = requireReport(request)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(reportUri(report), "text/html")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                webView.context.startActivity(Intent.createChooser(intent, report.name))
                respondSuccess(requestId, JSONObject.NULL)
            }
            "report.share" -> {
                val report = requireReport(request)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_STREAM, reportUri(report))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                webView.context.startActivity(Intent.createChooser(intent, report.name))
                respondSuccess(requestId, JSONObject.NULL)
            }
            "report.export" -> executeReportOperation(requestId) {
                val report = requireReport(request)
                val fileName = "midscene-report-${System.currentTimeMillis()}.html"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/html")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = webView.context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create Downloads report")
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        report.inputStream().use { it.copyTo(output) }
                    } ?: error("Unable to open Downloads report")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (error: Throwable) {
                    resolver.delete(uri, null, null)
                    throw error
                }
                JSONObject().put("fileName", fileName)
            }
            "report.delete" -> executeReportOperation(requestId) {
                reportRepository.delete(request.getJSONObject("params").getString("reportId"))
                JSONObject.NULL
            }
            "runtime.doctor" -> executeNodeRuntimeDoctor(requestId)
            "runtime.runTest" -> executeNodeTestRun(requestId, request.getJSONObject("params"))
            "runtime.stop" -> respondSuccess(
                requestId,
                JSONObject().put("stopped", nodeTestRunCoordinator.stop()),
            )
            "app.getVersion" -> {
                val context = webView.context
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                respondSuccess(
                    requestId,
                    JSONObject()
                        .put("versionName", packageInfo.versionName ?: "")
                        .put("buildNumber", androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)),
                )
            }
            "app.setLanguage" -> {
                respondSuccess(requestId, JSONObject.NULL)
                onLanguageChanged(request.getJSONObject("params").getString("language"))
            }
            "app.setTheme" -> {
                respondSuccess(requestId, JSONObject.NULL)
                onThemeChanged(request.getJSONObject("params").getString("theme"))
            }
            "app.getFloatingBallState" -> respondSuccess(
                requestId,
                JSONObject()
                    .put("enabled", FloatingBallPreferences.isEnabled(webView.context))
                    .put("available", service != null),
            )
            "app.setFloatingBallEnabled" -> {
                val enabled = request.getJSONObject("params").getBoolean("enabled")
                FloatingBallPreferences.setEnabled(webView.context, enabled)
                service?.setFloatingBallEnabled(enabled)
                respondSuccess(
                    requestId,
                    JSONObject().put("enabled", enabled).put("available", service != null),
                )
            }
            "app.getEdgeEffectSettings" -> respondSuccess(
                requestId,
                EdgeEffectPreferences.describe(webView.context),
            )
            "app.setEdgeEffectSettings" -> {
                val params = request.getJSONObject("params")
                val settings = EdgeEffectGeometrySettings(
                    mode = EdgeEffectGeometryMode.fromWireValue(params.getString("mode")),
                    cornerRadiusDp = params.getDouble("cornerRadiusDp").toFloat(),
                    insetDp = params.getDouble("insetDp").toFloat(),
                )
                EdgeEffectPreferences.set(webView.context, settings)
                service?.previewEdgeEffect(settings)
                respondSuccess(requestId, EdgeEffectPreferences.describe(webView.context))
            }
            "app.goHome" -> {
                respondSuccess(requestId, JSONObject.NULL)
                webView.postDelayed(
                    {
                        webView.context.startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    GO_HOME_RESPONSE_DELAY_MILLIS,
                )
            }
            "app.bringToFront" -> {
                respondSuccess(requestId, JSONObject.NULL)
                webView.postDelayed(
                    {
                        webView.context.startActivity(
                            Intent(webView.context, lifeos.md.tapilot.MainActivity::class.java)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                ),
                        )
                    },
                    BRING_TO_FRONT_RESPONSE_DELAY_MILLIS,
                )
            }
            "app.openAccessibilitySettings" -> {
                webView.context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                respondSuccess(requestId, JSONObject.NULL)
            }
            "app.openBatterySettings" -> {
                webView.context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${webView.context.packageName}")
                    },
                )
                respondSuccess(requestId, JSONObject.NULL)
            }
            else -> throw UnsupportedOperationException("Unsupported bridge method: $method")
        }
    }

    private fun speechPreset(request: JSONObject): String = when (
        request.getJSONObject("params").getString("provider")
    ) {
        SpeechTranscriber.VOLCENGINE -> "speech-volcengine"
        SpeechTranscriber.DEEPGRAM -> "speech-deepgram"
        else -> throw IllegalArgumentException("Unsupported speech provider")
    }

    private fun executeNodeTestRun(requestId: String, params: JSONObject) {
        val environmentJson = params.getJSONObject("environment")
        val environment = environmentJson.keys().asSequence().associateWith { name ->
            environmentJson.getString(name)
        }
        runCatching {
            nodeTestRunCoordinator.run(
                source = params.getString("source"),
                kind = params.getString("kind"),
                environment = environment,
            ) { result ->
                result.fold(
                    onSuccess = { respondSuccess(requestId, it) },
                    onFailure = { respondError(requestId, it) },
                )
            }
        }.onFailure { respondError(requestId, it) }
    }

    private fun requireReport(request: JSONObject): File {
        val reportId = request.getJSONObject("params").getString("reportId")
        return reportRepository.reportFile(reportId).also {
            check(it.isFile) { "Report does not exist: $reportId" }
        }
    }

    private fun reportUri(report: File) = FileProvider.getUriForFile(
        webView.context,
        "${webView.context.packageName}.reports",
        report,
    )

    private fun executeNodeRuntimeDoctor(requestId: String) {
        nodeRuntimeExecutor.execute {
            runCatching { NodeRuntimeProvisioner(webView.context).install() }.fold(
                onSuccess = { runtime -> runNodeRuntimeDoctor(requestId, runtime) },
                onFailure = { respondError(requestId, it) },
            )
        }
    }

    private fun runNodeRuntimeDoctor(requestId: String, runtime: InstalledNodeRuntime) {
        val responded = AtomicBoolean(false)
        var result: JSONObject? = null
        fun failOnce(error: Throwable) {
            if (responded.compareAndSet(false, true)) respondError(requestId, error)
        }
        runCatching {
            nodeRuntimeSupervisor.runDoctor(
                runtime,
                object : NodeRuntimeListener {
                    override fun onLog(line: String) {
                        Log.d(LOG_TAG, "Node runtime: $line")
                    }

                    override fun onEvent(event: NodeRuntimeEvent) {
                        Log.d(LOG_TAG, "Node runtime event: ${event.type}")
                        if (event.type == "doctor.completed") result = JSONObject(event.payload)
                        if (event.type == "run.failed") {
                            failOnce(IllegalStateException(JSONObject(event.payload).optString("message")))
                        }
                    }

                    override fun onComplete(exitCode: Int) {
                        val doctorResult = result
                        if (exitCode == 0 && doctorResult != null) {
                            if (responded.compareAndSet(false, true)) {
                                respondSuccess(requestId, doctorResult)
                            }
                        } else {
                            failOnce(IllegalStateException("Node runtime doctor exited with code $exitCode"))
                        }
                    }

                    override fun onFailure(error: Throwable) = failOnce(error)
                },
            )
        }.onFailure(::failOnce)
    }

    private fun executeReportOperation(requestId: String, operation: () -> Any?) {
        reportExecutor.execute {
            runCatching(operation).fold(
                onSuccess = { respondSuccess(requestId, it) },
                onFailure = { respondError(requestId, it) },
            )
        }
    }

    private fun requireService(
        service: MidsceneAccessibilityService?,
    ): MidsceneAccessibilityService = service
        ?: throw AccessibilityDisabledException()

    private fun respondSuccess(requestId: String, result: Any?) {
        respond(JSONObject().put("requestId", requestId).put("ok", true).put("result", result))
    }

    private fun respondError(requestId: String, error: Throwable) {
        val code = when (error) {
            is AccessibilityDisabledException -> "ACCESSIBILITY_DISABLED"
            is MidsceneAccessibilityService.SessionCancelledException -> "SESSION_CANCELLED"
            is MidsceneAccessibilityService.StaleFrameException -> "STALE_FRAME"
            is ModelTransport.RequestCancelledException -> "SESSION_CANCELLED"
            is NodeTestRunCoordinator.NodeRunStoppedException -> "SESSION_CANCELLED"
            is UnsupportedOperationException -> "UNSUPPORTED_ACTION"
            is IllegalArgumentException -> "INVALID_REQUEST"
            else -> "INTERNAL_ERROR"
        }
        respond(
            JSONObject()
                .put("requestId", requestId)
                .put("ok", false)
                .put(
                    "error",
                    JSONObject()
                        .put("code", code)
                        .put("message", error.message ?: error.javaClass.simpleName)
                        .put("retryable", false),
                ),
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun respond(response: JSONObject) {
        val quoted = JSONObject.quote(response.toString())
        webView.post {
            webView.evaluateJavascript("window.__midsceneNativeResolve?.($quoted)", null)
        }
    }

    private class AccessibilityDisabledException :
        IllegalStateException("Enable Tapilot device control in Accessibility settings")

    override fun close() {
        speechTranscriber.close()
        modelTransport.close()
        nodeTestRunCoordinator.close()
        reportRepository.close()
    }

    companion object {
        private const val LOG_TAG = "MidsceneBridge"
        private const val GO_HOME_RESPONSE_DELAY_MILLIS = 50L
        private const val BRING_TO_FRONT_RESPONSE_DELAY_MILLIS = 50L
        private val reportExecutor = Executors.newSingleThreadExecutor()
        private val nodeRuntimeExecutor = Executors.newSingleThreadExecutor()
    }
}
