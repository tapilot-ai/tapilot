package lifeos.md.tapilot.bridge

import org.json.JSONObject
import java.io.EOFException
import java.net.ProtocolException
import java.net.SocketException
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class ModelTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build(),
) : AutoCloseable {
    private data class ActiveRequest(
        val sessionId: String,
        val call: Call,
    )

    @Volatile private var closed = false
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
                cancelledRequests.remove(requestId)
                return@execute
            }
            runCatching {
                val builder = Request.Builder().url(params.getString("url"))
                val headers = params.optJSONObject("headers") ?: JSONObject()
                for (name in headers.keys()) builder.header(name, headers.getString(name))
                val body = if (params.has("bodyBase64") && !params.isNull("bodyBase64")) {
                    Base64.getDecoder().decode(params.getString("bodyBase64")).toRequestBody()
                } else null
                val request = builder.header("Connection", "close")
                    .method(params.optString("method", "POST"), body).build()
                require(request.url.isHttps) { "Model URL must use HTTPS" }
                // Share one deadline across reconnection attempts. Only repeat this model
                // completion, never the agent turn or already executed phone tools.
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MS.toLong())
                var result: JSONObject? = null
                for (attempt in 0..1) {
                    if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                    val call = client.newCall(request)
                    call.timeout().deadlineNanoTime(deadline)
                    activeRequests[requestId] = ActiveRequest(sessionId, call)
                    var responseStatus: Int? = null
                    try {
                        if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                        call.execute().use { response ->
                            responseStatus = response.code
                            val bytes = response.body?.bytes() ?: byteArrayOf()
                            if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                            val responseHeaders = JSONObject()
                            response.headers.names().forEach { name ->
                                responseHeaders.put(name, response.headers.values(name).joinToString(", "))
                            }
                            result = JSONObject().put("status", response.code)
                                .put("headers", responseHeaders)
                                .put("bodyBase64", Base64.getEncoder().encodeToString(bytes))
                        }
                        break
                    } catch (error: IOException) {
                        if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                        val disconnected = error is EOFException || error is SocketException ||
                            (error is ProtocolException && error.message?.contains("unexpected end of stream") == true) ||
                            error.cause is EOFException
                        if (!disconnected || (responseStatus != null && responseStatus !in 200..299)) throw ModelNetworkException(error)
                        if (attempt == 1 || System.nanoTime() >= deadline) throw ModelNetworkException(error)
                    } finally {
                        activeRequests.remove(requestId)
                    }
                }
                checkNotNull(result)

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
        activeRequests[requestId]?.call?.cancel()
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
        closed = true
        activeRequests.keys.forEach(::cancelRequest)
        executor.shutdownNow()
        client.connectionPool.evictAll()
        activeRequests.clear()
        cancelledRequests.clear()
        cancelledSessions.clear()
    }

    private fun isCancelled(requestId: String, sessionId: String): Boolean =
        closed || cancelledRequests.contains(requestId) || cancelledSessions.contains(sessionId)

    class ModelNetworkException(cause: IOException) : IOException("模型连接中断或请求超时，请检查网络后重试", cause)

    class RequestCancelledException : IllegalStateException("Model request was cancelled")

    companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 120_000
    }
}
