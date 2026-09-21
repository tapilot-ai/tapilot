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
