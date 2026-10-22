package lifeos.md.tapilot.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechAudioTest {
    @Test fun waveUsesActualSignedPcmEnergy() {
        assertEquals(0f, SpeechAudio.level(ByteArray(100)), 0f)
        assertTrue(SpeechAudio.level(byteArrayOf(0, 16, 0, -16)) > .7f)
        assertEquals(SpeechAudio.level(byteArrayOf(0, 16)), SpeechAudio.level(byteArrayOf(0, -16)), .01f)
    }
    @Test fun retryAudioIsBoundedAndSnapshotDoesNotAliasStoredData() {
        val audio = SpeechAudio(4)
        audio.append(byteArrayOf(1, 2))
        val copy = audio.snapshot()
        copy[0] = 99
        audio.append(byteArrayOf(3, 4))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), audio.snapshot())
        assertThrows(IllegalArgumentException::class.java) { audio.append(byteArrayOf(5)) }
    }
}
