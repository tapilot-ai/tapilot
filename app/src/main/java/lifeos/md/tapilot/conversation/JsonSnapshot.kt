package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject

/** Detach JSON containers without serializing text or rebuilding an intermediate Map tree. */
internal fun jsonSnapshot(source: JSONObject): JSONObject = JSONObject().apply {
    source.keys().forEach { key -> put(key, snapshotValue(source.get(key))) }
}

private fun snapshotValue(value: Any): Any = when (value) {
    is JSONObject -> jsonSnapshot(value)
    is JSONArray -> JSONArray().apply {
        for (index in 0 until value.length()) put(snapshotValue(value.get(index)))
    }
    else -> value // JSON primitives (including JSONObject.NULL) are immutable.
}
