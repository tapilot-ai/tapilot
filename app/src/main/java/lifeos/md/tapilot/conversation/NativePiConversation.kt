package lifeos.md.tapilot.conversation

import android.content.Context
import lifeos.md.tapilot.runtime.NodeRuntimeProvisioner
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

/** A separate Pi process owns the transcript. It never receives API credentials. */
class NativePiConversation(context: Context) : AutoCloseable {
    private val provisioner = NodeRuntimeProvisioner(context.applicationContext)
    private val lock = Any()
    private val inputExecutor = Executors.newSingleThreadExecutor()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var accepting = false
    private val deferred = ArrayDeque<JSONObject>()

    fun prepare() = synchronized(lock) { check(process == null); deferred.clear(); accepting = true }

    fun steer(id: String, text: String, followUp: Boolean = false): Boolean = synchronized(lock) {
        if (!accepting) return false
        val command = JSONObject().put("type", if (followUp) "followUp" else "steer").put("id", id).put("text", text)
        runCatching { if (process == null) deferred.add(command) else write(command) }.isSuccess
    }
    fun run(messages: JSONArray, resume: JSONArray?, limits: AgentLimits, stopped: () -> Boolean,
            complete: (JSONArray, JSONArray) -> JSONObject,
            phone: (String, String) -> PhoneResult, event: (JSONObject) -> Unit, resumeInputs: JSONArray = JSONArray(), goal: String = ""): JSONObject {
        check(!stopped()) { "任务已停止" }
        val runtime = provisioner.install()
        check(!stopped()) { "任务已停止" }
        val child = synchronized(lock) {
            check(process == null) { "Pi Agent 已在运行" }
            check(!stopped()) { "任务已停止" }
            ProcessBuilder(runtime.nodeExecutable.absolutePath,
                java.io.File(runtime.runtimeDirectory, "pi-runner.mjs").absolutePath)
                .directory(runtime.runtimeDirectory).redirectErrorStream(true).apply {
                    environment()["LD_LIBRARY_PATH"] = runtime.nodeExecutable.parentFile!!.absolutePath
                    environment()["HOME"] = runtime.homeDirectory.absolutePath
                    environment()["TMPDIR"] = runtime.temporaryDirectory.absolutePath
                }.start().also {
                    process = it; writer = it.outputStream.bufferedWriter()
                    write(JSONObject().put("type", "init").put("messages", messages)
                        .put("goal", goal).put("resume", resume ?: JSONArray()).put("pendingInputs", resumeInputs).put("limits", limits.json()))
                    while (deferred.isNotEmpty()) write(deferred.removeFirst())
                    accepting = true
                }
        }
        try {
            child.inputStream.bufferedReader().use { reader ->
                while (true) {
                    check(!stopped()) { "任务已停止" }
                    val line = reader.readLine() ?: error("Pi Agent 意外退出")
                    // Library diagnostics are intentionally not copied to app logs or task context.
                    if (!line.startsWith("TAPILOT_AGENT ")) continue
                    val value = JSONObject(line.removePrefix("TAPILOT_AGENT "))
                    when (value.getString("type")) {
                        "rpc" -> {
                            val result = runCatching {
                                check(!stopped()) { "任务已停止" }
                                val args = value.getJSONObject("params")
                                when (value.getString("method")) {
                                    "model.complete" -> complete(args.getJSONArray("messages"), args.getJSONArray("tools"))
                                    "phone.execute" -> phone(args.getString("instruction"), args.getString("success_condition")).json()
                                    else -> error("未知 Agent 请求")
                                }
                            }
                            check(!stopped()) { "任务已停止" }
                            val response = JSONObject().put("requestId", value.getString("requestId")).put("ok", result.isSuccess)
                            result.fold({ response.put("result", it) }, { response.put("error", it.cause?.message ?: it.message ?: "请求失败") })
                            synchronized(lock) { if (process === child) write(response) }
                        }
                        "result" -> { synchronized(lock) { accepting = false }; return value }
                        "failure" -> error(value.optString("error", "Pi Agent 执行失败"))
                        else -> event(value)
                    }
                }
            }
        } finally {
            synchronized(lock) {
                if (process === child) { accepting = false; writer = null; process = null }
            }
            child.destroy()
            if (!child.waitFor(2, TimeUnit.SECONDS)) { child.destroyForcibly(); child.waitFor(2, TimeUnit.SECONDS) }
        }
    }
    private fun write(value: JSONObject) {
        val output = checkNotNull(writer)
        val child = checkNotNull(process)
        val payload = value.toString()
        inputExecutor.execute {
            if (synchronized(lock) { process !== child || writer !== output }) return@execute
            runCatching { output.write(payload); output.newLine(); output.flush() }.onFailure { child.destroy() }
        }
    }
    fun stop() {
        val child = synchronized(lock) {
            accepting = false; deferred.clear()
            runCatching { writer?.let { write(JSONObject().put("type", "abort")) } }
            process
        }
        child?.destroy()
    }
    override fun close() { stop(); inputExecutor.shutdownNow() }
}
