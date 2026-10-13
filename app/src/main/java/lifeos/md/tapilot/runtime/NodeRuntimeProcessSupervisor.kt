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

    fun runDoctor(runtime: InstalledNodeRuntime, listener: NodeRuntimeListener) {
        start(runtime, listOf("doctor"), runtime.runtimeDirectory, emptyMap(), listener)
    }

    fun runTest(
        runtime: InstalledNodeRuntime,
        request: NodeTestRunRequest,
        listener: NodeRuntimeListener,
    ) {
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
        start(runtime, arguments, request.projectRoot, request.environment, listener)
    }

    private fun start(
        runtime: InstalledNodeRuntime,
        arguments: List<String>,
        workingDirectory: File,
        environment: Map<String, String>,
        listener: NodeRuntimeListener,
    ) {
        val command = listOf(
            runtime.nodeExecutable.absolutePath,
            runtime.entrypoint.absolutePath,
            *arguments.toTypedArray(),
        )
        val nativeLibraryDirectory = requireNotNull(runtime.nodeExecutable.parentFile).absolutePath
        val childEnvironment = sanitizeEnvironment(environment) +
            mapOf(
                "MIDSCENE_ANDROID_RUNTIME_VERSION" to runtime.manifest.runtimeVersion,
                "LD_LIBRARY_PATH" to nativeLibraryDirectory,
                "HOME" to runtime.homeDirectory.absolutePath,
                "TMPDIR" to runtime.temporaryDirectory.absolutePath,
                "PATH" to "$nativeLibraryDirectory:/system/bin:/system/xbin",
            )
        synchronized(lock) {
            check(activeProcess == null) { "A Node runtime task is already running" }
            val process = processFactory(command, workingDirectory, childEnvironment)
            activeProcess = process
            activeInput = process.outputStream.bufferedWriter()
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
        }
    }

    fun stop(): Boolean {
        val process = synchronized(lock) { activeProcess } ?: return false
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
                require(allowedEnvironment.matches(name)) { "Environment variable is not allowed: $name" }
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
