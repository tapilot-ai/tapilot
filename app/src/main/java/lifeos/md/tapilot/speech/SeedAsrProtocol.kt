package lifeos.md.tapilot.speech

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONObject

/** Seed ASR 2.0 binary framing, matching the official Volcengine WebSocket API. */
internal object SeedAsrProtocol {
    data class Response(val code: Int, val text: String, val last: Boolean, val speechEnded: Boolean = false)

    fun fullRequest(): ByteArray {
        val body = JSONObject()
            .put("user", JSONObject().put("uid", "tapilot"))
            .put("audio", JSONObject()
                .put("format", "pcm").put("codec", "raw")
                .put("rate", 16000).put("bits", 16).put("channel", 1))
            .put("request", JSONObject()
                .put("model_name", "bigmodel")
                .put("enable_nonstream", true)
                .put("enable_itn", true).put("enable_punc", true)
                .put("enable_ddc", false).put("show_utterances", true).put("end_window_size", 1500))
        return frame(0x11, 1, gzip(body.toString().toByteArray(Charsets.UTF_8)))
    }

    fun audioRequest(sequence: Int, pcm: ByteArray, last: Boolean = false): ByteArray {
        require(sequence > 0)
        return frame(if (last) 0x23 else 0x21, if (last) -sequence else sequence, gzip(pcm), 0x01)
    }

    fun parse(bytes: ByteArray): Response {
        require(bytes.size >= 4 && (bytes[0].toInt() and 0xff) shr 4 == 1) { "Invalid Seed ASR header" }
        val headerWords = bytes[0].toInt() and 0x0f
        require(headerWords >= 1)
        var offset = headerWords * 4
        require(offset <= bytes.size)
        val kind = (bytes[1].toInt() and 0xff) shr 4
        val flags = bytes[1].toInt() and 0x0f
        val compression = bytes[2].toInt() and 0x0f
        val serialization = (bytes[2].toInt() and 0xff) shr 4
        if (flags and 1 != 0) offset += 4 // sequence
        if (flags and 4 != 0) offset += 4 // event
        val code = if (kind == 0x0f) {
            require(offset + 8 <= bytes.size)
            val value = intAt(bytes, offset)
            offset += 4
            value
        } else {
            require(kind == 0x09) { "Unsupported Seed ASR response" }
            0
        }
        require(offset + 4 <= bytes.size)
        val size = intAt(bytes, offset)
        offset += 4
        require(size in 0..MAX_RESPONSE && size == bytes.size - offset) { "Invalid Seed ASR response size" }
        var payload = bytes.copyOfRange(offset, bytes.size)
        if (compression == 1 && payload.isNotEmpty()) {
            payload = GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
            require(payload.size <= MAX_RESPONSE)
        }
        val message = if (payload.isNotEmpty()) {
            require(serialization == 1) { "Unsupported Seed ASR serialization" }
            JSONObject(payload.toString(Charsets.UTF_8))
        } else null
        val result = message?.opt("result")
        val text = when (result) {
            is JSONObject -> result.optString("text")
            is org.json.JSONArray -> (0 until result.length()).reversed().firstNotNullOfOrNull { index ->
                (result.opt(index) as? JSONObject)?.optString("text")?.takeIf { it.isNotBlank() }
            }.orEmpty()
            else -> ""
        }
        val latestResult = when (result) {
            is JSONObject -> result
            is org.json.JSONArray -> result.optJSONObject(result.length() - 1)
            else -> null
        }
        val utterances = latestResult?.optJSONArray("utterances")
        val lastUtterance = utterances?.optJSONObject(utterances.length() - 1)
        val speechEnded = text.isNotBlank() && lastUtterance?.optBoolean("definite") == true
        return Response(code, text.trim(), flags and 2 != 0, speechEnded)
    }

    private fun frame(typeAndFlags: Int, sequence: Int, payload: ByteArray, serializerAndCompression: Int = 0x11): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use {
            it.write(byteArrayOf(0x11, typeAndFlags.toByte(), serializerAndCompression.toByte(), 0))
            it.writeInt(sequence)
            it.writeInt(payload.size)
            it.write(payload)
        }
        return output.toByteArray()
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()

    private fun intAt(bytes: ByteArray, index: Int): Int =
        ((bytes[index].toInt() and 0xff) shl 24) or
            ((bytes[index + 1].toInt() and 0xff) shl 16) or
            ((bytes[index + 2].toInt() and 0xff) shl 8) or
            (bytes[index + 3].toInt() and 0xff)

    private const val MAX_RESPONSE = 1024 * 1024
}
