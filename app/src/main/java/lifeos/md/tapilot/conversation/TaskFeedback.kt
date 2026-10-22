package lifeos.md.tapilot.conversation

import org.json.JSONObject

/** Render persisted evidence consistently in chat, speech, and the global result panel. */
object TaskFeedback {
    fun response(entry: JSONObject, zh: Boolean): String {
        fun local(chinese: String, english: String) = if (zh) chinese else english
        val status = entry.optString("status")
        if (status !in listOf("stopped", "failed")) return entry.optString("reply")
        val detail = TaskFailure.localized(entry.optString("errorCode"), zh) ?: entry.optString("detail")
        val parts = mutableListOf<String>()
        if (status == "failed") {
            parts += detail.ifBlank { local("任务失败，未记录具体原因。", "Task failed without a recorded reason.") }
        } else {
            parts += when (entry.optString("stopReason")) {
                "app" -> local("应用关闭，任务已停止，进度已保存。", "The app closed; the task stopped and progress was saved.")
                "user" -> local("已按你的要求停止任务。", "Stopped at your request.")
                "panel" -> local("打开对话面板改变了当前屏幕，任务已暂停，避免继续误操作。", "Opening the conversation panel changed the screen, so the task was paused to prevent unintended actions.")
                "new_task" -> local("已停止当前任务，准备执行新任务。", "Stopped this task to start the new task.")
                "new_input" -> local("收到新消息，已停止当前执行并保留上下文。", "New input stopped the current execution; context was saved.")
                "declined" -> local("操作未获确认，任务已停止。", "The operation was not confirmed, so the task stopped.")
                else -> detail.takeUnless { it.isBlank() || it in listOf("任务已停止", "Task stopped") }
                    ?: local("任务已停止；这条记录未保存停止原因。", "Task stopped; this record has no stop reason.")
            }
            val current = entry.optString("currentInstruction")
            if (current.isNotBlank()) parts += local("停止时正在处理：", "At the time of stopping: ") + current
        }
        val operations = entry.optJSONArray("completedOperations")?.objects().orEmpty()
        val last = operations.lastOrNull()
        if (status == "failed" && last?.optBoolean("success") == true && last.optString("result").isNotBlank()) {
            parts += local("最近一步已通过画面核验：", "The last operation passed screen verification: ") + last.optString("result")
            parts += local("整个任务尚未确认完成。进度已保存，可继续处理。", "The whole task is not yet confirmed complete. Progress was saved; you can continue.")
        }
        if (last != null && !last.optBoolean("success")) {
            val result = last.optString("result")
            val reason = result.substringAfter("Reason:", result).trim()
            if (reason.isNotBlank() && !parts.any { it.contains(reason) }) {
                parts += local("最近一步未通过验证：", "The last completed operation failed verification: ") + reason
            }
        }
        return parts.joinToString("\n\n")
    }
}
