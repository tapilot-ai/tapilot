package lifeos.md.tapilot.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingGestureTest {
    @Test fun slidingArmsCancelAndReturningRestoresRecording() {
        assertEquals(RecordingGesture.Cancel, recordingGesture(900f, 200f, 700f, 400f, 2f))
        assertEquals(RecordingGesture.Recording, recordingGesture(900f, 200f, 890f, 400f, 2f))
    }
    @Test fun rightSideSelectsTranscription() {
        assertEquals(RecordingGesture.Transcribe, recordingGesture(900f, 360f, 700f, 400f, 2f))
    }
}
