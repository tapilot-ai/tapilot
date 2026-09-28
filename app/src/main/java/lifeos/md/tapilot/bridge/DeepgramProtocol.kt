package lifeos.md.tapilot.bridge

import java.io.IOException
import org.json.JSONObject

internal object DeepgramProtocol {
    const val CLOSE_STREAM = "{\"type\":\"CloseStream\"}"

    fun finalTranscript(rawMessage: String): String? {
        val message = JSONObject(rawMessage)
        if (message.optString("type") == "Error") {
            throw IOException("Deepgram rejected request: ${message.optString("code")}")
        }
        if (message.optString("type") != "Results" || !message.optBoolean("is_final")) return null
        return message.optJSONObject("channel")?.optJSONArray("alternatives")
            ?.optJSONObject(0)?.optString("transcript")?.trim()?.takeIf { it.isNotEmpty() }
    }
}
