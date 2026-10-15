package lifeos.md.tapilot.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import lifeos.md.tapilot.services.ServiceGateway
import org.json.JSONObject

/** Legacy WebView transport only. Native callers depend on application services directly. */
class NativeJavascriptBridge(private val webView: WebView, private val services: ServiceGateway) {
    @JavascriptInterface
    fun postMessage(payload: String) {
        var requestId = ""
        runCatching {
            val request = JSONObject(payload)
            requestId = request.getString("requestId")
            require(request.getInt("protocolVersion") == BridgeProtocol.VERSION) { "Unsupported protocol version" }
            services.call(request.getString("method"), request.optJSONObject("params") ?: JSONObject(), request.getString("sessionId")) {
                respond(requestId, it)
            }
        }.onFailure { respond(requestId, Result.failure(it)) }
    }
    private fun respond(id: String, result: Result<Any?>) {
        val response = JSONObject().put("requestId", id).put("ok", result.isSuccess)
        result.fold({ response.put("result", it ?: JSONObject.NULL) }, { error ->
            response.put("error", JSONObject().put("code", BridgeErrors.code(error))
                .put("message", error.message ?: error.javaClass.simpleName).put("retryable", false))
        })
        webView.post { webView.evaluateJavascript("window.__midsceneNativeResolve?.(${JSONObject.quote(response.toString())})", null) }
    }
}
