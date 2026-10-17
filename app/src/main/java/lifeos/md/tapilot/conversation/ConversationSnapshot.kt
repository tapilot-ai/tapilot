package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject

/** Detach mutable UI JSON before storing it or using it as an encoding cache key. */
internal object ConversationSnapshot {
    fun capture(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { copy(value.get(it)) }
    private fun copy(value: Any?): Any? = when (value) {
        is JSONObject -> capture(value)
        is JSONArray -> (0 until value.length()).map { copy(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}
