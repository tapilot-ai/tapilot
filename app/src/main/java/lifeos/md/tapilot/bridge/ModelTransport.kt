package lifeos.md.tapilot.bridge

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ModelTransport : AutoCloseable {
    private data class ActiveRequest(
        val sessionId: String,
        val connection: HttpURLConnection,
    )

    private val executor = Executors.newCachedThreadPool()
    private val activeRequests = ConcurrentHashMap<String, ActiveRequest>()
    private val cancelledRequests = ConcurrentHashMap.newKeySet<String>()
    private val cancelledSessions = ConcurrentHashMap.newKeySet<String>()

    fun execute(
        requestId: String,
        sessionId: String,
        params: JSONObject,
        callback: (Result<JSONObject>) -> Unit,
    ) {
        executor.execute {
            if (isCancelled(requestId, sessionId)) {
                callback(Result.failure(RequestCancelledException()))
                return@execute
            }
            runCatching {
                val url = URL(params.getString("url"))
                require(url.protocol == "https") { "Model URL must use HTTPS" }
                val connection = url.openConnection() as HttpURLConnection
                activeRequests[requestId] = ActiveRequest(sessionId, connection)
                try {
                    if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                    connection.requestMethod = params.optString("method", "POST")
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.instanceFollowRedirects = false
                    val headers = params.optJSONObject("headers") ?: JSONObject()
                    for (name in headers.keys()) {
                        connection.setRequestProperty(name, headers.getString(name))
                    }
                    if (params.has("bodyBase64") && !params.isNull("bodyBase64")) {
                        connection.doOutput = true
                        connection.outputStream.use { output ->
                            output.write(Base64.getDecoder().decode(params.getString("bodyBase64")))
                        }
                    }
                    val status = connection.responseCode
                    val stream = if (status >= 400) connection.errorStream else connection.inputStream
                    val body = stream?.use { it.readBytes() } ?: byteArrayOf()
                    if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                    val responseHeaders = JSONObject()
                    connection.headerFields.forEach { (name, values) ->
                        if (name != null && !values.isNullOrEmpty()) {
                            responseHeaders.put(name, values.joinToString(", "))
                        }
                    }
                    JSONObject()
                        .put("status", status)
                        .put("headers", responseHeaders)
                        .put("bodyBase64", Base64.getEncoder().encodeToString(body))
                } finally {
                    activeRequests.remove(requestId)
                    connection.disconnect()
                }
            }.fold(
                onSuccess = { callback(Result.success(it)) },
                onFailure = {
                    val error = if (isCancelled(requestId, sessionId)) {
                        RequestCancelledException()
                    } else {
                        it
                    }
                    callback(Result.failure(error))
                },
            )
            cancelledRequests.remove(requestId)
        }
    }

    fun cancelRequest(requestId: String) {
        cancelledRequests.add(requestId)
        activeRequests[requestId]?.connection?.disconnect()
    }

    fun cancelSession(sessionId: String) {
        cancelledSessions.add(sessionId)
        activeRequests.forEach { (requestId, request) ->
            if (request.sessionId == sessionId) cancelRequest(requestId)
        }
    }

    fun completeSession(sessionId: String) {
        cancelledSessions.remove(sessionId)
    }

    override fun close() {
        activeRequests.keys.forEach(::cancelRequest)
        executor.shutdownNow()
        activeRequests.clear()
        cancelledRequests.clear()
        cancelledSessions.clear()
    }

    private fun isCancelled(requestId: String, sessionId: String): Boolean =
        cancelledRequests.contains(requestId) || cancelledSessions.contains(sessionId)

    class RequestCancelledException : IllegalStateException("Model request was cancelled")

    companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 120_000
    }
}
