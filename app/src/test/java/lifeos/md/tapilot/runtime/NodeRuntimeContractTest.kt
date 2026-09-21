package lifeos.md.tapilot.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeRuntimeContractTest {
    @Test
    fun parsesManifest() {
        val manifest = NodeRuntimeManifest.parse(
            """{"schemaVersion":1,"runtimeVersion":"0.1.0","entrypoint":"runner.mjs","minimumNodeMajor":20}""",
        )

        assertEquals("0.1.0", manifest.runtimeVersion)
        assertEquals("runner.mjs", manifest.entrypoint)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeEntrypoint() {
        NodeRuntimeManifest(1, "0.1.0", "../runner.mjs", 20)
    }

    @Test
    fun parsesStructuredEventAndIgnoresLogs() {
        assertNull(NodeRuntimeEvent.parse("ordinary output"))
        val event = NodeRuntimeEvent.parse(
            """MIDSCENE_EVENT {"type":"doctor.completed","timestamp":"2026-09-15T00:00:00.000Z","node":"v22.0.0"}""",
        )
        assertEquals("doctor.completed", event?.type)
        assertEquals("2026-09-15T00:00:00.000Z", event?.timestamp)
    }
}
