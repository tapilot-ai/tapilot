package lifeos.md.tapilot.performance

import lifeos.md.tapilot.bridge.ModelTransport
import lifeos.md.tapilot.chatgpt.ChatGptResponses
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Debug app-UID diagnostic. Credentials arrive on stdin, never files or argv. */
object ModelTransportBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val connections = IdentityHashMap<Connection, Pair<Int, Int>>()
        var started = 0L
        var connectionTrace = JSONArray()
        var network = JSONArray()
        var timing = JSONObject()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000.0
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS).retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
            .eventListener(object : EventListener() {
                override fun connectionAcquired(call: Call, connection: Connection) {
                    val old = connections[connection] ?: (connections.size + 1 to 0)
                    connections[connection] = old.first to old.second + 1
                    connectionTrace.put(JSONObject().put("socketId", old.first).put("previousRequests", old.second)
                        .put("negotiatedProtocol", connection.protocol().toString()).put("acquiredMs", elapsed()))
                }
                override fun requestHeadersStart(call: Call) { timing.put("sendHeadersMs", elapsed()) }
                override fun requestBodyEnd(call: Call, byteCount: Long) { timing.put("bodySentMs", elapsed()) }
                override fun responseHeadersEnd(call: Call, response: okhttp3.Response) { timing.put("headersMs", elapsed()) }
            }).build()
        val transport = ModelTransport(client) { network.put(it) }
        println(JSONObject().put("ready", true).put("node", "native-okhttp").put("environmentProxyEnabled", false))
        try {
            generateSequence(::readLine).forEach { line ->
                val input = JSONObject(line)
                val id = input.getString("id")
                println(JSONObject().put("received", id))
                val body = input.getString("body").toByteArray(Charsets.UTF_8)
                val measurement = JSONObject().put("id", id).put("transport", "native-okhttp")
                    .put("requestBytes", body.size).put("requestSha256", MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) })
                connectionTrace = JSONArray(); network = JSONArray(); timing = JSONObject()
                started = System.nanoTime()
                try {
                    val response = CompletableFuture<JSONObject>()
                    transport.execute(id, "benchmark", JSONObject().put("url", "https://api.openai.com/v1/responses")
                        .put("method", "POST").put("headers", JSONObject().put("Authorization", "Bearer ${input.getString("token")}")
                            .put("Content-Type", "application/json").put("Accept", "text/event-stream"))
                        .put("bodyBase64", Base64.getEncoder().encodeToString(body))) {
                        it.fold(response::complete, response::completeExceptionally)
                    }
                    val result = response.get(50, TimeUnit.SECONDS)
                    measurement.put("totalMs", elapsed()).put("status", result.getInt("status"))
                    val decoded = ChatGptResponses.decode(result)
                    check(decoded.getJSONObject("modelMetrics").getString("model") == "gpt-6-luna")
                    val events = String(Base64.getDecoder().decode(result.getString("bodyBase64")), Charsets.UTF_8)
                    val completed = events.lineSequence().filter { it.startsWith("data: ") }
                        .mapNotNull { runCatching { JSONObject(it.removePrefix("data: ")) }.getOrNull() }
                        .last { it.optString("type") == "response.completed" }.getJSONObject("response")
                    val usage = completed.getJSONObject("usage")
                    measurement.put("model", completed.getString("model")).put("outcome", "complete")
                        .put("usage", JSONObject().put("inputTokens", usage.getInt("input_tokens")).put("outputTokens", usage.getInt("output_tokens"))
                            .put("cachedTokens", usage.optJSONObject("input_tokens_details")?.opt("cached_tokens"))
                            .put("reasoningTokens", usage.optJSONObject("output_tokens_details")?.opt("reasoning_tokens")))
                    val http = network.getJSONObject(network.length() - 1)
                    measurement.put("headersMs", http.getDouble("headersMs")).put("firstBodyMs", http.opt("firstBodyMs"))
                } catch (error: Exception) {
                    measurement.put("outcome", "failed").put("error", error.javaClass.simpleName).put("totalMs", elapsed())
                }
                measurement.put("connectionTrace", connectionTrace).put("network", network).put("nativeTiming", timing)
                println(measurement)
            }
        } finally { transport.close() }
    }
}
