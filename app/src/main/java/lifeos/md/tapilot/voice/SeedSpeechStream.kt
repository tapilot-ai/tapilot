package lifeos.md.tapilot.voice

import okio.BufferedSource
import org.json.JSONObject
import java.io.IOException

/** The service's terminal event, not HTTP EOF, ends one synthesis request. */
internal fun readSeedSpeechStream(source: BufferedSource, active: () -> Boolean, audio: (String) -> Unit) {
    while (active() && !source.exhausted()) {
        val newline = source.indexOf('\n'.code.toByte(), 0, 2L * 1024 * 1024)
        val line = if (newline >= 0) source.readUtf8LineStrict(2L * 1024 * 1024).trim()
            else {
                check(source.buffer.size <= 2L * 1024 * 1024) { "Speech response line is too large" }
                source.readUtf8().trim()
            }
        if (line.isEmpty()) continue
        val event = JSONObject(line)
        val code = event.optInt("code", -1)
        if (code != 0 && code != 20000000) throw IOException("Speech synthesis failed ($code)")
        event.optString("data").takeIf { it.isNotBlank() && it != "null" }?.let(audio)
        if (code == 20000000) return
    }
    if (active()) throw IOException("Speech stream ended before synthesis completed")
}
