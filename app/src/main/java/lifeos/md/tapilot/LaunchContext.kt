package lifeos.md.tapilot

import org.json.JSONObject

/** One-shot context captured before an external app loses the foreground. */
object LaunchContext {
    private var pending: JSONObject? = null
    private var pendingPanel: JSONObject? = null

    @Synchronized
    fun put(value: JSONObject) {
        pending = value
    }

    @Synchronized
    fun putPanel(value: JSONObject) { pendingPanel = value }

    @Synchronized
    fun consumePanel(): JSONObject? = pendingPanel.also { pendingPanel = null }

    @Synchronized
    fun consume(): JSONObject? = pending.also { pending = null }
}
