package lifeos.md.tapilot.accessibility

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class FrameFingerprintTest {
    @Test fun blackWhiteAndColorSignalsHaveStableUnsignedLuma() {
        val colors = intArrayOf(0xff000000.toInt(), 0xffffffff.toInt(), 0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt())
        val pixels = IntArray(64 * 96) { colors[it % colors.size] }
        val bytes = Base64.getDecoder().decode(FrameFingerprint.encode(pixels))
        assertEquals(6144, bytes.size)
        assertArrayEquals(intArrayOf(0, 255, 54, 182, 18), bytes.take(5).map { it.toInt() and 255 }.toIntArray())
        assertEquals(8192, FrameFingerprint.encode(pixels).length)
    }
    @Test fun incompleteImageIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { FrameFingerprint.encode(IntArray(10)) }
    }
}
