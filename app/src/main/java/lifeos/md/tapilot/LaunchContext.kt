package lifeos.md.tapilot

import org.json.JSONObject

/** One-shot context captured before an external app loses the foreground. */
object LaunchContext {
    private var pending: JSONObject? = null

    @Synchronized
    fun put(value: JSONObject) {
        pending = value
    }

    @Synchronized
    fun consume(): JSONObject? = pending.also { pending = null }
}
