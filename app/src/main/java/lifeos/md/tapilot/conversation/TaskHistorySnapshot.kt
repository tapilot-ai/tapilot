package lifeos.md.tapilot.conversation

import org.json.JSONArray
import org.json.JSONObject

/** Only the active conversation crosses onto the task worker; unrelated UI history stays in place. */
internal data class TaskHistorySnapshot(val retry: JSONObject?, val prior: List<JSONObject>, val pendingInputs: JSONArray) {
    companion object {
        fun capture(history: List<JSONObject>, conversationId: String, text: String, kind: String,
                    retryOf: String?, continueOf: String?): TaskHistorySnapshot {
            fun conversation(entry: JSONObject) = entry.optString("conversationId", entry.optString("id"))
            val selected = history.find { it.optString("id") == (continueOf ?: retryOf) }
                ?: if (kind == "task") TaskLifecycle.continuation(history.filter { conversation(it) == conversationId }, text) else null
            val retry = selected?.let(::jsonSnapshot)
            val prior = history.filter { conversation(it) == conversationId &&
                (retry == null || (it.optString("id") != retryOf && it.optLong("createdAt") <= retry.optLong("createdAt"))) }
                .map(::jsonSnapshot)
            val pending = TaskLifecycle.pendingInputs(retry?.takeIf { continueOf != null || it.optString("phase") != "completed" }, history)
            return TaskHistorySnapshot(retry, prior, pending)
        }
    }
}
