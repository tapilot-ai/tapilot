package lifeos.md.tapilot.conversation

import org.json.JSONObject

enum class ExecutionMode(val wireValue: String) {
    YOLO("yolo"), CRITICAL("critical"), ALWAYS("always");

    fun title(zh: Boolean): String = when (this) {
        YOLO -> if (zh) "自动执行（YOLO）" else "Automatic (YOLO)"
        CRITICAL -> if (zh) "关键操作确认" else "Confirm critical operations"
        ALWAYS -> if (zh) "每次操作确认" else "Confirm each operation"
    }

    fun requiresConfirmation(instruction: String): Boolean = when (this) {
        YOLO -> false
        ALWAYS -> true
        CRITICAL -> criticalOperation.containsMatchIn(instruction)
    }

    companion object {
        private val criticalOperation = Regex("发送|转账|付款|支付|购买|下单|删除|清空|卸载|发布|提交|授权|分享给|\\b(send|transfer|pay|purchase|delete|uninstall|publish|submit)\\b", RegexOption.IGNORE_CASE)

        fun from(data: JSONObject): ExecutionMode = entries.firstOrNull { it.wireValue == data.optString("executionMode") }
            ?: if (data.has("confirmEveryStep")) {
                if (data.optBoolean("confirmEveryStep")) ALWAYS else CRITICAL
            } else YOLO
    }
}
