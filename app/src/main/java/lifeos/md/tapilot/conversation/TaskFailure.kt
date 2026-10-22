package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.AppText.text as appText
import org.json.JSONObject

/** Stable failure codes cross runtime/storage boundaries; presentation follows the current app language. */
internal class TaskFailure(val code: String, cause: Throwable? = null) : IllegalStateException(null, cause) {
    override val message: String get() = localized(code) ?: appText("任务失败", "Task failed")

    companion object {
        fun localized(code: String, chinese: Boolean? = null): String? {
            val pair = when (code) {
                "request_timeout" -> "请求超时，请稍后重试" to "Request timed out. Try again later."
                "task_stopped" -> "任务已停止" to "Task stopped"
                "model_turn_limit" -> "本次任务已达到对话轮次上限，可继续或在设置中调整上限。" to "The task reached its model turn limit. Continue or adjust the limit in settings."
                "phone_operation_limit" -> "本次任务已达到手机操作上限，可继续或在设置中调整上限。" to "The task reached its phone operation limit. Continue or adjust the limit in settings."
                "model_message_missing" -> "模型未返回消息。" to "The model returned no message."
                "model_response_empty" -> "模型返回了空回复。" to "The model returned an empty response."
                "task_terminal_missing" -> "任务缺少明确的结束状态，不能推断为完成。" to "The task has no explicit final state; completion is unknown."
                "task_result_missing" -> "模型未返回有效结果。" to "The model returned no usable result."
                "task_no_progress" -> "模型连续未推进任务，已停止。尚未确认整个任务完成。" to "The model repeatedly made no progress. The task stopped before completion was confirmed."
                "task_verification_missing" -> "当前要求尚未通过画面核验，不能确认任务完成。" to "The current requirements have no successful screen verification. Task completion is not confirmed."
                "request_failed" -> "请求失败" to "Request failed"
                "context_payload_limit" -> "附件与上下文超过传输上限，请减少附件或开始新对话。" to "Attachments and context exceed the transfer limit. Reduce attachments or start a new conversation."
                "context_budget_limit" -> "对话上下文超过安全预算。已保留任务记录，请缩短输入、减少附件或开始新对话；未继续操作手机。" to "Conversation context exceeds the safe budget. Task records were retained. Shorten your input, reduce attachments or start a new conversation; no further phone action was taken."
                else -> return null
            }
            return when (chinese) { true -> pair.first; false -> pair.second; null -> appText(pair.first, pair.second) }
        }
        fun from(value: JSONObject): Throwable {
            val code = value.optString("errorCode")
            return if (localized(code) != null) TaskFailure(code)
            else IllegalStateException(value.optString("error", value.optString("reply")).ifBlank { appText("任务失败", "Task failed") })
        }
        fun find(error: Throwable?): TaskFailure? {
            var current = error
            val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
            while (current != null && visited.add(current)) {
                if (current is TaskFailure) return current
                current = current.cause
            }
            return null
        }
    }
}
