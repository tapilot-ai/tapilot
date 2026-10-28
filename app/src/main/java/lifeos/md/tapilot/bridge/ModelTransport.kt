package lifeos.md.tapilot.bridge

import lifeos.md.tapilot.AppText.text as appText
import org.json.JSONObject
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import okhttp3.ConnectionPool
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
    private val onTiming: (JSONObject) -> Unit = {},
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
                val request = builder.method(params.optString("method", "POST"), body).build()
                require(request.url.isHttps) { "Model URL must use HTTPS" }
                // Share one deadline across reconnection attempts. Only repeat this model
                // completion, never the agent turn or already executed phone tools.
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MS.toLong())
                var result: JSONObject? = null
                for (attempt in 0..1) {
                    if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                    // Reserve time for one reconnect when no response headers arrive.
                    // Streaming bodies retain the overall deadline and are never replayed on timeout.
                    val retryPool = if (attempt > 0) ConnectionPool() else null
                    val started = System.nanoTime()
                    fun elapsed() = (System.nanoTime() - started) / 1_000_000.0
                    val measurement = JSONObject().put("phase", "model.http")
                        .put("requestId", requestId).put("attempt", attempt)
                        .put("requestBytes", body?.contentLength() ?: 0)
                    val attemptClient = client.newBuilder()
                        .eventListener(ModelConnectionTiming(measurement, ::elapsed))
                        .readTimeout(minOf(client.readTimeoutMillis, HEADER_IDLE_TIMEOUT_MS).toLong(), TimeUnit.MILLISECONDS)
                        .apply { if (retryPool != null) connectionPool(retryPool) }
                        .build()
                    val call = attemptClient.newCall(request)
                    call.timeout().deadlineNanoTime(deadline)
                    activeRequests[requestId] = ActiveRequest(sessionId, call)
                    var responseStatus: Int? = null
                    try {
                        if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                        call.execute().use { response ->
                            responseStatus = response.code
                            measurement.put("headersMs", elapsed()).put("status", response.code)
                            val source = response.body?.source()
                            // The shorter idle limit applies only while waiting for headers.
                            // Restore the configured body timeout; the call-wide deadline
                            // still bounds the entire request, including reconnection.
                            source?.timeout()?.timeout(client.readTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
                            if (source?.request(1) == true) measurement.put("firstBodyMs", elapsed())
                            val bytes = response.body?.bytes() ?: byteArrayOf()
                            measurement.put("responseBytes", bytes.size).put("bodyComplete", true)
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
                        measurement.put("errorType", error.javaClass.simpleName)
                        if (isCancelled(requestId, sessionId)) throw RequestCancelledException()
                        val disconnected = error is EOFException || error is SocketException ||
                            (error is ProtocolException && error.message?.contains("unexpected end of stream") == true) ||
                            error.cause is EOFException
                        val headerTimeout = responseStatus == null &&
                            (error is SocketTimeoutException ||
                                (error is InterruptedIOException && error.message == "timeout"))
                        val proxyConnectFailed = measurement.optString("proxyType") in setOf("HTTP", "SOCKS") &&
                            measurement.has("connectStartMs") && !measurement.has("tlsStartMs") &&
                            !measurement.has("connectEndMs") && !measurement.has("requestHeadersStartMs") &&
                            (error is java.net.ConnectException || error is java.net.NoRouteToHostException || error is SocketTimeoutException)
                        fun failure(): IOException = if (proxyConnectFailed) ModelProxyConnectionException(error) else ModelNetworkException(error)
                        if ((!disconnected && !headerTimeout) || (responseStatus != null && responseStatus !in 200..299)) throw failure()
                        if (attempt == 1 || System.nanoTime() >= deadline) throw failure()
                    } finally {
                        measurement.put("totalMs", elapsed())
                        runCatching { onTiming(measurement) }
                        activeRequests.remove(requestId)
                        retryPool?.evictAll()
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

    class ModelNetworkException(cause: IOException) : IOException(appText("模型连接中断或请求超时，请检查网络后重试", "Model connection interrupted or timed out. Check your network and try again"), cause)

    class ModelProxyConnectionException(cause: IOException) : IOException(appText(
        "无法连接系统代理。请检查或重新连接手机的 VPN/代理服务，再重试；任务尚未完成",
        "Cannot connect to the system proxy. Check or reconnect the phone's VPN/proxy service, then retry. Task incomplete",
    ), cause)

    class RequestCancelledException : IllegalStateException("Model request was cancelled")

    companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 120_000
        const val HEADER_IDLE_TIMEOUT_MS = 60_000
    }
}
