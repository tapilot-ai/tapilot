package lifeos.md.tapilot.voice

import org.junit.Assert.*
import org.junit.Test

class PcmStreamTest {
    @Test fun reconstructsSamplesAcrossOddNetworkChunks() {
        val stream = PcmStream()
        val chunks = listOf(byteArrayOf(1), byteArrayOf(2, 3, 4), byteArrayOf(5, 6, 7), byteArrayOf(8))
        val output = chunks.flatMap { stream.append(it).toList() }.toByteArray()
        stream.finish()
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), output)
    }
    @Test(expected = IllegalStateException::class) fun rejectsIncompleteFinalSample() {
        val stream = PcmStream(); stream.append(byteArrayOf(1)); stream.finish()
    }
}
