package lifeos.md.tapilot.conversation

import org.json.JSONObject

internal enum class PhoneMode(val wireValue: String) {
    ACT("act"), OBSERVE("observe");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.wireValue == value }
            ?: throw IllegalArgumentException("Unsupported phone operation mode")
    }
}

internal enum class PlanningEffort(val wireValue: String) {
    FAST("fast"), BALANCE("balance"), DEEP_THINK("deepThink");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.wireValue == value }
            ?: throw IllegalArgumentException("Unsupported planning effort")
    }
}

/** Validated at the Pi boundary; task callbacks receive named, typed operation fields. */
internal data class PhoneOperation(
    val instruction: String,
    val successCondition: String,
    val mode: PhoneMode = PhoneMode.ACT,
    val app: String = "",
    val effort: PlanningEffort = PlanningEffort.FAST,
) {
    init {
        require(instruction.isNotBlank()) { "Phone instruction must not be blank" }
        require(successCondition.isNotBlank()) { "Phone success condition must not be blank" }
    }

    companion object {
        fun from(value: JSONObject): PhoneOperation {
            fun text(key: String, default: String? = null): String {
                if (!value.has(key) && default != null) return default
                return value.opt(key) as? String ?: throw IllegalArgumentException("Expected string: $key")
            }
            return PhoneOperation(text("instruction"), text("success_condition"),
                PhoneMode.from(text("mode", "act")), text("app", ""),
                PlanningEffort.from(text("effort", "fast")))
        }
    }
}
