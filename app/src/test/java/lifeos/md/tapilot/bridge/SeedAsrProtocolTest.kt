package lifeos.md.tapilot.bridge

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedAsrProtocolTest {
    @Test fun fullRequestDescribesSixteenKilohertzPcm() {
        val packet = SeedAsrProtocol.fullRequest()
        assertEquals(0x11, packet[0].toInt() and 0xff)
        assertEquals(0x11, packet[1].toInt() and 0xff)
        assertEquals(0x11, packet[2].toInt() and 0xff)
        val input = DataInputStream(ByteArrayInputStream(packet, 4, packet.size - 4))
        assertEquals(1, input.readInt())
        val size = input.readInt()
        val payload = ByteArray(size).also { input.readFully(it) }
        val request = JSONObject(GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
            .toString(Charsets.UTF_8))
        assertEquals(16000, request.getJSONObject("audio").getInt("rate"))
        assertEquals("bigmodel", request.getJSONObject("request").getString("model_name"))
        assertTrue(request.getJSONObject("request").getBoolean("show_utterances"))
        assertTrue(request.getJSONObject("request").getBoolean("enable_nonstream"))
        assertEquals(1500, request.getJSONObject("request").getInt("end_window_size"))
    }

    @Test fun onlyLatestDefiniteUtteranceSignalsPause() {
        fun ended(utterances: String) = SeedAsrProtocol.parse(serverResponse(0x91, 0,
            """{"result":{"text":"你好","utterances":$utterances}}""")).speechEnded
        assertTrue(ended("""[{"definite":true}]"""))
        assertEquals(false, ended("""[{"definite":true},{"definite":false}]"""))
        assertEquals(false, ended("[]"))
    }

    @Test fun finalAudioPacketUsesNegativeSequence() {
        val packet = SeedAsrProtocol.audioRequest(4, byteArrayOf(1, 2), last = true)
        assertEquals(0x23, packet[1].toInt() and 0xff)
        assertEquals(0x01, packet[2].toInt() and 0xff)
        assertEquals(-4, DataInputStream(ByteArrayInputStream(packet, 4, 4)).readInt())
    }

    @Test fun parsesFinalTextAndErrorCode() {
        val result = SeedAsrProtocol.parse(serverResponse(0x93, 0, "{\"result\":{\"text\":\"你好\"}}"))
        assertEquals("你好", result.text)
        assertTrue(result.last)
        assertEquals(45000000, SeedAsrProtocol.parse(serverResponse(0xf1, 45000000, "{}")).code)
    }

    @Test fun providerEndpointsMatchTheApiContracts() {
        assertEquals("volc.seedasr.sauc.duration", SpeechTranscriber.RESOURCE_ID)
        assertTrue(SpeechTranscriber.VOLCENGINE_URL.endsWith("/api/v3/sauc/bigmodel"))
        assertTrue(SpeechTranscriber.DEEPGRAM_URL.contains("model=nova-3"))
        assertTrue(SpeechTranscriber.DEEPGRAM_URL.contains("encoding=linear16"))
        assertTrue(SpeechTranscriber.DEEPGRAM_URL.contains("sample_rate=16000"))
    }

    private fun serverResponse(type: Int, code: Int, json: String): ByteArray {
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(json.toByteArray()) }
        }.toByteArray()
        return ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use {
                it.write(byteArrayOf(0x11, type.toByte(), 0x11, 0))
                it.writeInt(1)
                if (code != 0) it.writeInt(code)
                it.writeInt(compressed.size)
                it.write(compressed)
            }
        }.toByteArray()
    }
}
