package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject

/** Only the active conversation crosses onto the task worker; unrelated UI history stays in place. */
internal data class TaskHistorySnapshot(val retry: JSONObject?, val prior: List<JSONObject>, val pendingInputs: JSONArray) {
    companion object {
        private fun conversation(entry: JSONObject) = entry.optString("conversationId", entry.optString("id"))

        /** Amendments belong to a task attempt; they have no standalone goal or checkpoint. */
        fun retryTarget(entry: JSONObject, history: List<JSONObject>): JSONObject {
            if (entry.optString("kind") != "instruction") return entry
            return history.filter { conversation(it) == conversation(entry) && it.optString("kind", "task") == "task" &&
                TaskLifecycle.amendments(it, listOf(entry)).isNotEmpty() }.maxByOrNull { it.optLong("createdAt") }
                ?: throw TaskFailure("task_context_missing")
        }

        fun capture(history: List<JSONObject>, conversationId: String, text: String, kind: String,
                    retryOf: String?, continueOf: String?): TaskHistorySnapshot {
            val requestedId = continueOf ?: retryOf
            val selected = if (requestedId != null) history.find { conversation(it) == conversationId && it.optString("id") == requestedId }
                else if (kind == "task") TaskLifecycle.continuation(history.filter { conversation(it) == conversationId }, text) else null
            val retry = selected?.let { jsonSnapshot(retryTarget(it, history)) }?.also { task ->
                // Persist inherited ownership before execution, even if no new checkpoint is reached.
                task.put("amendmentIds", JSONArray(TaskLifecycle.amendments(task, history).map { it.getString("id") }))
            }
            val prior = history.filter { conversation(it) == conversationId &&
                (retry == null || ((retryOf == null || it.optString("id") != retry.optString("id")) && it.optLong("createdAt") <= retry.optLong("createdAt"))) }
                .map(::jsonSnapshot)
            val pending = TaskLifecycle.pendingInputs(retry?.takeIf {
                continueOf != null || selected?.optString("kind") == "instruction" || it.optString("phase") != "completed"
            }, history)
            return TaskHistorySnapshot(retry, prior, pending)
        }
    }
}
