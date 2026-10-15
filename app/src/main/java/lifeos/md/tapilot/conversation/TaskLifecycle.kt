package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject
import lifeos.md.tapilot.TaskExecutionStatus

/** Persist task state before execution, and every plan/tool/context checkpoint afterwards. */
object TaskLifecycle {
    fun executionStatus(stopped: Boolean, stopAcknowledged: Boolean, succeeded: Boolean, phase: String): TaskExecutionStatus = when {
        stopped && !stopAcknowledged -> TaskExecutionStatus.FAILED
        stopped -> TaskExecutionStatus.STOPPED
        !succeeded -> TaskExecutionStatus.FAILED
        phase == "waiting_user" -> TaskExecutionStatus.WAITING_USER
        else -> TaskExecutionStatus.SUCCEEDED
    }

    fun begin(entry: JSONObject, previous: JSONObject? = null) {
        entry.put("taskId", previous?.optString("taskId", previous.optString("id")) ?: entry.getString("id"))
            .put("goal", previous?.optString("goal", previous.optString("source")) ?: entry.getString("source"))
            .put("phase", "running").put("attempt", (previous?.optInt("attempt", 1) ?: 0) + 1)
            .put("modelTurns", 0).put("phoneOperations", 0)
        previous?.optJSONArray("plan")?.let { entry.put("plan", JSONArray(it.toString())).put("currentStep", previous.optInt("currentStep")) }
        previous?.optJSONArray("agentContext")?.let { entry.put("agentContext", JSONArray(it.toString())) }
        previous?.optJSONArray("completedOperations")?.let { entry.put("previousOperations", JSONArray(it.toString())) }
    }
    fun event(entry: JSONObject, value: JSONObject) {
        value.optJSONObject("performance")?.let { entry.put("performance", it) }
        when (value.getString("type")) {
            "plan" -> entry.put("plan", value.getJSONArray("steps")).put("currentStep", value.getInt("currentStep"))
            "model" -> entry.put("modelTurns", value.getInt("turns"))
            "operation" -> entry.put("phoneOperations", value.getInt("operations")).put("currentInstruction", value.getString("instruction"))
            "checkpoint" -> entry.put("agentContext", value.getJSONArray("messages"))
                .put("modelTurns", value.getInt("turns")).put("phoneOperations", value.getInt("operations"))
        }
        entry.put("updatedAt", System.currentTimeMillis())
    }
    fun finish(entry: JSONObject, status: String, phase: String = "") {
        entry.put("phase", phase.ifBlank { when (status) { "stopped" -> "paused"; "waiting_user" -> "waiting_user"; "succeeded" -> "completed"; else -> "failed" } })
            .put("completedAt", System.currentTimeMillis())
    }
    /** Never auto-run after process death. Preserve pending messages as visible, retryable input. */
    fun recover(state: JSONObject) {
        state.getJSONArray("history").objects().forEach { entry ->
            if (entry.optString("status") in listOf("running", "queued")) {
                entry.put("status", "stopped").put("detail", "应用中断，任务进度已保存，请重试或继续")
                finish(entry, "stopped")
            }
        }
    }
    /** Failed attempts must not automatically replay input or bypass the configured limits. */
    fun settleInputs(entry: JSONObject, history: List<JSONObject>, pendingMessage: String): List<JSONObject> {
        val consumed = entry.optJSONArray("agentContext")?.objects()?.map { it.optString("inputId") }.orEmpty().toSet()
        val status = entry.getString("status")
        val inputs = history.filter { it.optString("kind") == "instruction" && it.optString("taskId") == entry.getString("id") }
        inputs.forEach { input ->
            val inputStatus = when {
                input.getString("id") !in consumed -> "stopped"
                status == "succeeded" -> "succeeded"
                status == "waiting_user" -> "waiting_user"
                status == "failed" -> "failed"
                else -> "stopped"
            }
            input.put("status", inputStatus); finish(input, inputStatus)
            if (inputStatus !in listOf("succeeded", "waiting_user")) input.put("detail", if (inputStatus == "failed") entry.optString("detail") else pendingMessage)
        }
        return if (status in listOf("succeeded", "waiting_user")) inputs.filter { it.getString("id") !in consumed } else emptyList()
    }
    fun pendingInputs(previous: JSONObject?, history: List<JSONObject>): JSONArray {
        if (previous == null) return JSONArray()
        val consumed = previous.optJSONArray("agentContext")?.objects()?.map { it.optString("inputId") }.orEmpty().toSet()
        return JSONArray(history.filter { it.optString("kind") == "instruction" && it.optString("taskId") == previous.optString("id") && it.optString("id") !in consumed }
            .sortedBy { it.optLong("createdAt") }.map { JSONObject().put("id", it.getString("id")).put("text", it.getString("source")) })
    }
    fun isNewTask(text: String): Boolean = Regex("^((换个任务|换一个任务|切换任务)|(新任务|new task|switch task)([：:，,。.!\\s]|$))", RegexOption.IGNORE_CASE).containsMatchIn(text.trim())
    fun continuation(history: List<JSONObject>, text: String): JSONObject? {
        val last = history.filter { it.optString("kind", "task") == "task" }.maxByOrNull { it.optLong("createdAt") } ?: return null
        if (isNewTask(text)) return null
        return last.takeIf { it.optString("phase") == "waiting_user" ||
            (it.optString("phase") in listOf("paused", "failed") && text.trim().lowercase() in listOf("继续", "继续任务", "continue", "resume")) }
    }
}
