package lifeos.md.tapilot.speech

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeepgramProtocolTest {
    @Test fun finalizedSegmentAloneIsNotEndOfSpeech() {
        org.junit.Assert.assertFalse(DeepgramProtocol.speechEnded("""{"type":"Results","is_final":true,"speech_final":false}"""))
        org.junit.Assert.assertTrue(DeepgramProtocol.speechEnded("""{"type":"Results","is_final":true,"speech_final":true}"""))
    }
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

    @Test fun interimWordsUpdateDisplayWithoutBecomingFinalSegments() {
        val interim = """{"type":"Results","is_final":false,"channel":{"alternatives":[{"transcript":"still speaking"}]}}"""
        assertEquals("still speaking", DeepgramProtocol.interimTranscript(interim))
        assertNull(DeepgramProtocol.finalTranscript(interim))
        val final = interim.replace("false", "true")
        assertNull(DeepgramProtocol.interimTranscript(final))
        assertEquals("still speaking", DeepgramProtocol.finalTranscript(final))
        assertNull(DeepgramProtocol.interimTranscript("""{"type":"Metadata"}"""))
    }

    @Test fun reportsServiceError() {
        val error = assertThrows(IOException::class.java) {
            DeepgramProtocol.finalTranscript("""{"type":"Error","code":"INVALID_AUTH"}""")
        }
        assertEquals("Deepgram rejected request: INVALID_AUTH", error.message)
    }
}
