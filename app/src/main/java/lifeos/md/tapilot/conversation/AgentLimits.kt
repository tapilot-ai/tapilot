package lifeos.md.tapilot.conversation

import org.json.JSONObject

/** Null disables a limit. Limits are snapshotted separately for each attempt. */
data class AgentLimits(val phoneOperations: Int? = 100, val modelTurns: Int? = 100) {
    init {
        require(phoneOperations == null || phoneOperations > 0)
        require(modelTurns == null || modelTurns > 0)
    }

    fun json() = JSONObject().put("phoneOperations", phoneOperations ?: 0).put("modelTurns", modelTurns ?: 0)

    companion object {
        fun from(data: JSONObject): AgentLimits {
            val settings = data.optJSONObject("agentLimits") ?: return AgentLimits()
            fun read(key: String, fallback: Int): Int? {
                val value = settings.opt(key)
                if (value !is Number) return fallback
                val number = value.toLong()
                if (value.toDouble() != number.toDouble() || number !in 0..Int.MAX_VALUE.toLong()) return fallback
                return if (number == 0L) null else number.toInt()
            }
            return AgentLimits(read("phoneOperations", 100), read("modelTurns", 100))
        }
    }
}
