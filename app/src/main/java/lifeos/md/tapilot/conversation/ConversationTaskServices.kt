package lifeos.md.tapilot.conversation

import lifeos.md.tapilot.TaskExecutionStatus
import org.json.JSONObject

internal enum class TaskNavigation { HOME, PREVIOUS_APP }

/** Operations a task may perform; transport field names stay in the adapter below. */
internal interface ConversationTaskServices {
    fun status(status: TaskExecutionStatus)
    fun archiveAttachment(attachment: JSONObject): JSONObject
    fun readAttachment(id: String): JSONObject
    fun capabilities(): JSONObject
    fun navigate(destination: TaskNavigation)
    fun prepare(environment: JSONObject)
    fun run(source: String, environment: JSONObject): JSONObject
    fun discardPrepared()
}

/** Compatibility boundary for the shared native/legacy gateway. */
internal class GatewayTaskServices(
    private val call: (String, JSONObject, Long) -> Any?,
    private val dispatch: (String, JSONObject) -> Unit,
) : ConversationTaskServices {
    private fun json(method: String, params: JSONObject = JSONObject(), seconds: Long = 120): JSONObject =
        call(method, params, seconds) as? JSONObject ?: error("Invalid $method response")

    override fun status(status: TaskExecutionStatus) {
        call("task.status", JSONObject().put("status", status.name.lowercase()), 120)
    }
    override fun archiveAttachment(attachment: JSONObject) = json("app.archiveAttachment", JSONObject().put("attachment", attachment))
    override fun readAttachment(id: String) = json("app.readAttachment", JSONObject().put("id", id))
    override fun capabilities() = json("device.getCapabilities")
    override fun navigate(destination: TaskNavigation) {
        call(if (destination == TaskNavigation.HOME) "app.goHome" else "app.returnToPreviousApp", JSONObject(), 120)
    }
    override fun prepare(environment: JSONObject) {
        dispatch("runtime.prepareTest", JSONObject().put("environment", environment))
    }
    override fun run(source: String, environment: JSONObject) = json("runtime.runTest", JSONObject()
        .put("source", source).put("kind", "yaml").put("environment", environment).put("reportTaskStatus", false), 0)
    override fun discardPrepared() { dispatch("runtime.discardPrepared", JSONObject()) }
}
