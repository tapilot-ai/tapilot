package lifeos.md.tapilot.voice

import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SeedSpeechStreamTest {
    @Test fun completionDoesNotWaitForHttpEofAndKeepsFinalAudio() {
        val input = Buffer().writeUtf8("{\"code\":0,\"data\":\"AA==\"}\n{\"code\":20000000,\"data\":\"AQ==\"}\n")
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (input.exhausted()) throw AssertionError("Waited for HTTP after terminal event")
                return input.read(sink, byteCount)
            }
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        }.buffer()
        val audio = mutableListOf<String>()
        readSeedSpeechStream(source, { true }, audio::add)
        assertEquals(listOf("AA==", "AQ=="), audio)
    }

    @Test fun truncatedStreamAndProviderFailureCannotReportCompletion() {
        for (body in listOf("{\"code\":0,\"data\":\"AA==\"}\n", "{\"code\":55000000}\n")) {
            assertThrows(IOException::class.java) {
                readSeedSpeechStream(Buffer().writeUtf8(body), { true }) {}
            }
        }
    }

    @Test fun cancellationStopsBeforeReadingAndNullTerminalDataIsNotAudio() {
        val input = Buffer().writeUtf8("{\"code\":20000000,\"data\":null}\n")
        val size = input.size
        readSeedSpeechStream(input, { false }) { fail("Unexpected audio") }
        assertEquals(size, input.size)
        readSeedSpeechStream(input, { true }) { fail("Null is not audio") }
    }
}
