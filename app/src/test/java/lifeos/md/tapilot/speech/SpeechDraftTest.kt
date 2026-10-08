package lifeos.md.tapilot.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechDraftTest {
    @Test fun preservesParagraphAndListBoundariesWhenAppendingToDraft() {
        assertEquals("hello", appendSpeechTranscript("", " hello "))
        assertEquals("draft\nhello", appendSpeechTranscript("draft", "hello"))
        assertEquals("draft hello", appendSpeechTranscript("draft ", "hello"))
        assertEquals("draft\n1. 登录\n2. 录音", appendSpeechTranscript("draft", "1. 登录\n2. 录音"))
        assertEquals("draft\n", appendSpeechTranscript("draft\n", "  "))
    }
}
