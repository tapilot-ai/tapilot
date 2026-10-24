package lifeos.md.tapilot.runtime

import java.io.File
import java.io.IOException
import java.io.BufferedWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

data class NodeTestRunRequest(
    val projectRoot: File,
    val resultDirectory: File,
    val configPath: String? = null,
    val projects: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
)

interface NodeRuntimeListener {
    fun onLog(line: String)
    fun onEvent(event: NodeRuntimeEvent)
    fun onComplete(exitCode: Int)
    fun onFailure(error: Throwable)
}

class NodeRuntimeProcessSupervisor(
    private val processFactory: (List<String>, File, Map<String, String>) -> Process =
        ::startProcess,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val inputExecutor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var activeProcess: Process? = null
    private var activeInput: BufferedWriter? = null
    private data class Prepared(val process: Process, val arguments: List<String>, val directory: File,
        val environment: Map<String, String>, val runtime: InstalledNodeRuntime)
    private var prepared: Prepared? = null

    /** Imports modules only. No test or native operation runs before adoption. */
    fun prepareTest(runtime: InstalledNodeRuntime, request: NodeTestRunRequest) = synchronized(lock) {
        check(activeProcess == null) { "A Node runtime task is already running" }
        discardPrepared()
        val arguments = testArguments(request)
        val environment = childEnvironment(runtime, request.environment)
        val process = processFactory(runtime.command(runtime.entrypoint.absolutePath,
            "prepare") + arguments.drop(1), request.projectRoot, environment)
        prepared = Prepared(process, arguments, request.projectRoot.canonicalFile, environment, runtime)
    }

    fun discardPrepared() = synchronized(lock) {
        prepared?.let { terminate(it.process) }
        prepared = null
    }

    fun runDoctor(runtime: InstalledNodeRuntime, listener: NodeRuntimeListener) {
        start(runtime, listOf("doctor"), runtime.runtimeDirectory, emptyMap(), listener)
    }

    fun runTest(
        runtime: InstalledNodeRuntime,
        request: NodeTestRunRequest,
        listener: NodeRuntimeListener,
    ): Boolean = start(runtime, testArguments(request), request.projectRoot, request.environment, listener)

    private fun testArguments(request: NodeTestRunRequest): List<String> {
        require(request.projectRoot.isDirectory) {
            "Project root does not exist: ${request.projectRoot.absolutePath}"
        }
        require(request.resultDirectory.isAbsolute) { "Result directory must be absolute" }
        val arguments = mutableListOf(
            "test",
            "--project-root",
            request.projectRoot.canonicalPath,
            "--result-dir",
            request.resultDirectory.absolutePath,
        )
        request.configPath?.let {
            require(it.isNotBlank() && !it.startsWith("-")) { "Invalid config path" }
            arguments += listOf("--config", it)
        }
        request.projects.forEach {
            require(it.isNotBlank() && !it.startsWith("-")) { "Invalid project name" }
            arguments += listOf("--project", it)
        }
        return arguments
    }

    private fun childEnvironment(runtime: InstalledNodeRuntime, environment: Map<String, String>): Map<String, String> {
        val nativeLibraryDirectory = requireNotNull(runtime.nodeExecutable.parentFile).absolutePath
        return sanitizeEnvironment(environment) + mapOf(
            "MIDSCENE_ANDROID_RUNTIME_VERSION" to runtime.manifest.runtimeVersion,
            "LD_LIBRARY_PATH" to nativeLibraryDirectory,
            "HOME" to runtime.homeDirectory.absolutePath,
            "TMPDIR" to runtime.temporaryDirectory.absolutePath,
            "PATH" to "$nativeLibraryDirectory:/system/bin:/system/xbin",
        )
    }

    private fun start(
        runtime: InstalledNodeRuntime,
        arguments: List<String>,
        workingDirectory: File,
        environment: Map<String, String>,
        listener: NodeRuntimeListener,
    ): Boolean {
        val command = runtime.command(runtime.entrypoint.absolutePath) + arguments
        val childEnvironment = childEnvironment(runtime, environment)
        return synchronized(lock) {
            check(activeProcess == null) { "A Node runtime task is already running" }
            val candidate = prepared?.takeIf {
                it.runtime == runtime && it.arguments == arguments &&
                    it.directory == workingDirectory.canonicalFile && it.environment == childEnvironment && it.process.isAlive
            }
            if (candidate == null) discardPrepared()
            val process = candidate?.process ?: processFactory(command, workingDirectory, childEnvironment)
            prepared = null
            activeProcess = process
            activeInput = process.outputStream.bufferedWriter()
            if (candidate != null) respondToRpc(JSONObject().put("type", "activate"))
            executor.execute {
                val result = runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val event = runCatching { NodeRuntimeEvent.parse(line) }
                                .getOrElse { throw IOException("Invalid Node runtime event", it) }
                            if (event == null) listener.onLog(line) else listener.onEvent(event)
                        }
                    }
                    process.waitFor()
                }
                // Keep ownership until the failed worker is known to have exited. A malformed
                // event or listener exception must not leave an untracked process behind.
                val terminated = if (result.isFailure) runCatching { terminate(process) } else Result.success(Unit)
                if (terminated.isFailure) {
                    result.exceptionOrNull()?.addSuppressed(terminated.exceptionOrNull()!!)
                }
                if (terminated.isSuccess) release(process)
                result.fold(listener::onComplete, listener::onFailure)
            }
            candidate != null
        }
    }

    fun stop(): Boolean {
        val (process, hadPrepared) = synchronized(lock) {
            val existed = prepared != null
            discardPrepared()
            activeProcess to existed
        }
        if (process == null) return hadPrepared
        terminate(process)
        release(process)
        return true
    }

    private fun release(process: Process) {
        val writer = synchronized(lock) {
            if (activeProcess !== process) return
            activeProcess = null
            activeInput.also { activeInput = null }
        }
        writer?.let { runCatching { it.close() } }
    }

    private fun terminate(process: Process) {
        process.destroy()
        if (!process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            check(process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Node runtime did not terminate" }
        }
    }

    fun isRunning(): Boolean = synchronized(lock) { activeProcess != null }

    fun respondToRpc(response: JSONObject) {
        val (process, writer) = synchronized(lock) {
            check(activeProcess != null) { "No Node runtime task is running" }
            activeProcess!! to checkNotNull(activeInput) { "Node runtime input is unavailable" }
        }
        inputExecutor.execute {
            val payload = response.toString()
            val isCurrentInput = synchronized(lock) {
                activeProcess === process && activeInput === writer
            }
            if (!isCurrentInput) return@execute
            try {
                writer.write(payload)
                writer.newLine()
                writer.flush()
            } catch (error: Throwable) {
                if (error is InterruptedException) {
                    Thread.currentThread().interrupt()
                } else if (error !is IOException) {
                    throw error
                }
                // The reader loop reports process termination through the listener. Never let
                // pipe backpressure or a closed stdin block/fail the Android main thread.
                process.destroy()
            }
        }
    }

    override fun close() {
        stop()
        inputExecutor.shutdownNow()
        executor.shutdownNow()
    }

    companion object {
        private const val STOP_TIMEOUT_SECONDS = 2L
        private val allowedEnvironment = Regex("(?:MIDSCENE|OPENAI)_[A-Z0-9_]+")

        internal fun sanitizeEnvironment(environment: Map<String, String>): Map<String, String> =
            environment.mapValues { (name, value) ->
                val screenshotFactor = name == "TAPILOT_SCREENSHOT_SHRINK_FACTOR"
                require(allowedEnvironment.matches(name) || screenshotFactor) { "Environment variable is not allowed: $name" }
                if (screenshotFactor) require(value in setOf("1", "1.5")) { "Unsupported screenshot experiment factor" }
                require('\u0000' !in value) { "Environment variable contains a null byte: $name" }
                value
            }

        private fun startProcess(
            command: List<String>,
            workingDirectory: File,
            environment: Map<String, String>,
        ): Process = ProcessBuilder(command)
            .directory(workingDirectory)
            .redirectErrorStream(true)
            .apply { environment().putAll(environment) }
            .start()
    }
}
