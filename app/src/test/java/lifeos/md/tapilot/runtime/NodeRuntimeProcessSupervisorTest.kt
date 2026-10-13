package lifeos.md.tapilot.runtime

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class NodeRuntimeProcessSupervisorTest {
    @Test
    fun acceptsOnlyModelAndMidsceneEnvironment() {
        val result = NodeRuntimeProcessSupervisor.sanitizeEnvironment(
            mapOf(
                "MIDSCENE_MODEL_API_KEY" to "secret",
                "OPENAI_API_KEY" to "secret-2",
            ),
        )

        assertEquals(2, result.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnrelatedEnvironment() {
        NodeRuntimeProcessSupervisor.sanitizeEnvironment(mapOf("PATH" to "/tmp"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNullBytesInEnvironment() {
        NodeRuntimeProcessSupervisor.sanitizeEnvironment(
            mapOf("MIDSCENE_MODEL_API_KEY" to "secret\u0000suffix"),
        )
    }

    @Test
    fun rpcPipeBackpressureNeverBlocksTheCallingThread() {
        val process = BlockingInputProcess()
        val supervisor = NodeRuntimeProcessSupervisor { _, _, _ -> process }
        val runtime = InstalledNodeRuntime(
            nodeExecutable = File(".", "node"),
            runtimeDirectory = File("."),
            entrypoint = File("runner.mjs"),
            homeDirectory = File("."),
            temporaryDirectory = File("."),
            manifest = NodeRuntimeManifest(1, "test", "runner.mjs", 20),
        )
        supervisor.runDoctor(runtime, NoOpNodeRuntimeListener)

        val startedAt = System.nanoTime()
        supervisor.respondToRpc(JSONObject().put("requestId", "capture"))
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue(process.writeStarted.await(1, TimeUnit.SECONDS))
        assertTrue("RPC response blocked for ${elapsedMillis}ms", elapsedMillis < 100)
        process.destroy()
        supervisor.close()
    }

    @Test
    fun completionReleasesRuntimeBeforeStartingNextTask() {
        val supervisor = NodeRuntimeProcessSupervisor { _, _, _ -> object : Process() {
            override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
            override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
            override fun getOutputStream() = java.io.ByteArrayOutputStream()
            override fun waitFor() = 0
            override fun exitValue() = 0
            override fun destroy() = Unit
        } }
        val runtime = InstalledNodeRuntime(File(".", "node"), File("."), File("runner.mjs"), File("."), File("."),
            NodeRuntimeManifest(1, "test", "runner.mjs", 20))
        val completed = CountDownLatch(2)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val listener = object : NodeRuntimeListener {
            override fun onLog(line: String) = Unit
            override fun onEvent(event: NodeRuntimeEvent) = Unit
            override fun onComplete(exitCode: Int) {
                try {
                    assertTrue("Completed runtime must release its slot", !supervisor.isRunning())
                    if (completed.count == 2L) supervisor.runDoctor(runtime, this)
                } catch (error: Throwable) { failure.set(error) }
                completed.countDown()
            }
            override fun onFailure(error: Throwable) { failure.set(error); completed.countDown() }
        }
        try {
            supervisor.runDoctor(runtime, listener)
            assertTrue("Both tasks should finish", completed.await(2, TimeUnit.SECONDS))
            failure.get()?.let { throw it }
        } finally { supervisor.close() }
    }

    @Test fun malformedEventAndListenerFailureTerminateBeforeReleasingOwnership() {
        for (line in listOf("MIDSCENE_EVENT {broken", "diagnostic")) {
            var forced = false
            var destroyed = false
            val process = object : Process() {
                override fun getInputStream() = ByteArrayInputStream((line + "\n").toByteArray())
                override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
                override fun getOutputStream() = java.io.ByteArrayOutputStream()
                override fun waitFor() = 0
                override fun waitFor(timeout: Long, unit: TimeUnit) = forced
                override fun exitValue(): Int { if (!forced) throw IllegalThreadStateException(); return 1 }
                override fun destroy() { destroyed = true }
                override fun destroyForcibly(): Process { forced = true; return this }
            }
            val supervisor = NodeRuntimeProcessSupervisor { _, _, _ -> process }
            val complete = CountDownLatch(1)
            var releasedAfterTermination = false
            val runtime = InstalledNodeRuntime(File(".", "node"), File("."), File("runner.mjs"), File("."), File("."), NodeRuntimeManifest(1, "test", "runner.mjs", 20))
            try {
                supervisor.runDoctor(runtime, object : NodeRuntimeListener {
                    override fun onLog(line: String) { error("listener failed") }
                    override fun onEvent(event: NodeRuntimeEvent) = Unit
                    override fun onComplete(exitCode: Int) { complete.countDown() }
                    override fun onFailure(error: Throwable) {
                        releasedAfterTermination = destroyed && forced && !supervisor.isRunning()
                        complete.countDown()
                    }
                })
                assertTrue(complete.await(2, TimeUnit.SECONDS))
                assertTrue(releasedAfterTermination)
            } finally { supervisor.close() }
        }
    }

    private class BlockingInputProcess : Process() {
        val writeStarted = CountDownLatch(1)
        private val destroyed = CountDownLatch(1)
        private val input = object : InputStream() {
            override fun read(): Int {
                destroyed.await()
                return -1
            }
        }
        private val output = object : OutputStream() {
            override fun write(value: Int) {
                writeStarted.countDown()
                destroyed.await()
            }
        }

        override fun getOutputStream(): OutputStream = output
        override fun getInputStream(): InputStream = input
        override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int {
            destroyed.await()
            return 0
        }
        override fun exitValue(): Int = if (destroyed.count == 0L) 0 else error("Still running")
        override fun destroy() {
            destroyed.countDown()
        }
    }

    private object NoOpNodeRuntimeListener : NodeRuntimeListener {
        override fun onLog(line: String) = Unit
        override fun onEvent(event: NodeRuntimeEvent) = Unit
        override fun onComplete(exitCode: Int) = Unit
        override fun onFailure(error: Throwable) = Unit
    }
}
