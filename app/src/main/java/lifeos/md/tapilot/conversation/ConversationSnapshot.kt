package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject

/** Detach mutable UI JSON before storing it or using it as an encoding cache key. */
internal object ConversationSnapshot {
    fun capture(value: JSONObject, previous: Map<String, Any?>? = null): Map<String, Any?> {
        var changed: MutableMap<String, Any?>? = null
        for (key in value.keys()) {
            val old = previous?.get(key)
            val next = copy(value.get(key), old)
            if (previous?.containsKey(key) != true || next !== old) {
                if (changed == null) changed = LinkedHashMap(previous.orEmpty())
                changed[key] = next
            }
        }
        // Equal object size does not imply equal keys (one key may replace another).
        if (previous != null && (changed != null || previous.size != value.length())) {
            for (key in previous.keys) if (!value.has(key)) {
                if (changed == null) changed = LinkedHashMap(previous)
                changed.remove(key)
            }
        }
        return changed ?: previous ?: emptyMap()
    }

    private fun array(value: JSONArray, previous: List<*>?): List<Any?> {
        var changed: MutableList<Any?>? = null
        for (index in 0 until value.length()) {
            val old = previous?.getOrNull(index)
            val next = copy(value.get(index), old)
            if (previous == null || index >= previous.size || next !== old) {
                if (changed == null) changed = previous?.toMutableList() ?: mutableListOf()
                if (index < changed.size) changed[index] = next else changed.add(next)
            }
        }
        if (previous != null && previous.size > value.length()) {
            if (changed == null) changed = previous.toMutableList()
            changed.subList(value.length(), changed.size).clear()
        }
        return changed ?: previous ?: emptyList()
    }

    // Previous containers are detached snapshots produced here, never mutable UI JSON.
    @Suppress("UNCHECKED_CAST")
    private fun copy(value: Any?, previous: Any?): Any? = when (value) {
        is JSONObject -> capture(value, previous as? Map<String, Any?>)
        is JSONArray -> array(value, previous as? List<*>)
        JSONObject.NULL -> null
        else -> if (value == previous) previous else value
    }
}
