package lifeos.md.tapilot.voice

import org.junit.Assert.*
import org.junit.Test

class FloatingVoiceSessionTest {
    @Test fun slideOnlySelectsCancellationAndCanBeReversed() {
        val session = FloatingVoiceSession()
        session.begin(true)
        session.selectCancel(true)
        assertTrue(session.active); assertTrue(session.held); assertTrue(session.cancelSelected)
        session.selectCancel(false)
        assertFalse(session.cancelSelected)
        assertTrue(session.release()); assertFalse(session.held)
        assertTrue(session.consumeFinish()); assertFalse(session.consumeFinish())
    }
    @Test fun releaseBeforeMicrophoneReadyIsConsumedOnceAndCancelRejectsLateCallbacks() {
        val session = FloatingVoiceSession()
        val old = session.begin(true)
        session.release()
        assertTrue(session.contextCaptured(old))
        session.end()
        assertFalse(session.consumeFinish()); assertFalse(session.contextCaptured(old))
        val next = session.begin(true)
        assertFalse(session.isCurrent(old)); assertTrue(session.isCurrent(next))
        assertFalse(session.contextReady); assertFalse(session.consumeFinish())
    }
    @Test fun failedEntryKeepsVisibleErrorUntilRetryResetsIt() {
        val session = FloatingVoiceSession()
        val old = session.begin(true)
        session.fail("microphone unavailable")
        assertTrue(session.active); assertFalse(session.held)
        assertFalse(session.contextCaptured(old)); assertFalse(session.consumeFinish())
        assertEquals("microphone unavailable", session.error)
        session.begin(false)
        assertEquals("", session.error); assertFalse(session.cancelSelected)
        assertFalse(session.contextReady)
    }
}
