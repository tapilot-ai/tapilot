package lifeos.md.tapilot.conversation

import org.json.JSONObject

/** Queued input is typed in memory; JSON is used only for durable snapshots. */
internal data class PendingTurn(val text: String, val global: Boolean, val attachment: JSONObject? = null, val continueOf: String? = null) {
    fun json() = JSONObject().put("text", text).put("global", global).apply {
        attachment?.let { put("attachment", JSONObject(it.toString())) }
        continueOf?.let { put("continueOf", it) }
    }
    companion object {
        fun from(value: JSONObject) = PendingTurn(value.getString("text"), value.optBoolean("global"),
            value.optJSONObject("attachment")?.let { JSONObject(it.toString()) }, value.optString("continueOf").takeIf { it.isNotBlank() })
    }
}
