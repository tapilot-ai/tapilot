package lifeos.md.tapilot.bridge

import org.junit.Assert.*
import org.junit.Test

class SpeechCaptureBoundaryTest {
    @Test fun lastReadIsRetainedAfterReleaseAndBeforeEndFrame() {
        val boundary = SpeechCaptureBoundary()
        val audio = SpeechAudio(4)
        audio.append(byteArrayOf(1, 2))
        boundary.requestFinish()
        assertFalse(boundary.claimFinal())
        if (boundary.acceptsAudio()) audio.append(byteArrayOf(3, 4))
        boundary.captureDrained()
        assertTrue(boundary.claimFinal())
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), audio.snapshot())
        assertFalse(boundary.acceptsAudio())
        assertFalse(boundary.claimFinal())
    }
    @Test fun serviceReadinessCannotEndCaptureBeforeRelease() {
        val boundary = SpeechCaptureBoundary()
        boundary.captureDrained()
        assertFalse(boundary.claimFinal())
        boundary.requestFinish()
        assertTrue(boundary.claimFinal())
    }
    @Test fun retainedAudioRetryIsAlreadyDrainedAndFinalizesOnce() {
        val boundary = SpeechCaptureBoundary(true)
        boundary.requestFinish()
        assertTrue(boundary.claimFinal())
        assertFalse(boundary.claimFinal())
    }
}
