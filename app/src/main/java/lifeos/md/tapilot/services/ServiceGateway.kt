package lifeos.md.tapilot.services

import org.json.JSONObject

/** Application operations used by UI controllers; implementations own Android resources. */
interface ServiceGateway {
    fun call(method: String, params: JSONObject = JSONObject(), sessionId: String = "native", callback: (Result<Any?>) -> Unit)
}
