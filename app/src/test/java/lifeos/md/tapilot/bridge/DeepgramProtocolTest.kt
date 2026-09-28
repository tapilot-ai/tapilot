package lifeos.md.tapilot.bridge

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeepgramProtocolTest {
    @Test fun acceptsFinalResultWithoutFinalizeAcknowledgement() {
        val message = """{"type":"Results","is_final":true,"from_finalize":false,"channel":{"alternatives":[{"transcript":"  hello world  "}]}}"""
        assertEquals("hello world", DeepgramProtocol.finalTranscript(message))
        assertEquals("{\"type\":\"CloseStream\"}", DeepgramProtocol.CLOSE_STREAM)
    }

    @Test fun ignoresInterimAndEmptyResults() {
        assertNull(DeepgramProtocol.finalTranscript("""{"type":"Results","is_final":false,"channel":{"alternatives":[{"transcript":"partial"}]}}"""))
        assertNull(DeepgramProtocol.finalTranscript("""{"type":"Results","is_final":true,"channel":{"alternatives":[{"transcript":"  "}]}}"""))
        assertNull(DeepgramProtocol.finalTranscript("""{"type":"Metadata"}"""))
    }

    @Test fun reportsServiceError() {
        val error = assertThrows(IOException::class.java) {
            DeepgramProtocol.finalTranscript("""{"type":"Error","code":"INVALID_AUTH"}""")
        }
        assertEquals("Deepgram rejected request: INVALID_AUTH", error.message)
    }
}
