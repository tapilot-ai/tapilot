package lifeos.md.tapilot.bridge

import okio.buffer
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.EOFException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ModelTransportTest {
    private fun params() = JSONObject().put("url", "https://model.example/completions")
        .put("method", "POST").put("bodyBase64", Base64.getEncoder().encodeToString("{}".toByteArray()))
    private fun execute(transport: ModelTransport, params: JSONObject = params()): Result<JSONObject> {
        val completed = CountDownLatch(1)
        var result: Result<JSONObject>? = null
        transport.execute("request-1", "session-1", params) { result = it; completed.countDown() }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        return result!!
    }
    private fun response(request: okhttp3.Request, code: Int = 200) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .body("{}".toResponseBody()).build()

    @Test fun `unreachable configured proxy produces recovery guidance without leaking its address`() {
        val port = ServerSocket(0).use { it.localPort }
        val client = OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", port)))
            .connectTimeout(200, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
        val records = mutableListOf<JSONObject>()
        ModelTransport(client, onTiming = { records.add(it) }).use { transport ->
            val error = execute(transport).exceptionOrNull()
            assertTrue(error is ModelTransport.ModelProxyConnectionException)
            assertTrue(error!!.message!!.contains("system proxy"))
            assertFalse(error.message!!.contains("127.0.0.1"))
            assertEquals(2, records.size)
            records.forEach { assertFalse(it.has("requestHeadersStartMs")); assertFalse(it.has("tlsStartMs")) }
        }
    }

    @Test fun `a cancelled request never starts network work`() {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet(); response(chain.request())
        }.build()
        ModelTransport(client).use { transport ->
            transport.cancelRequest("request-1")
            assertTrue(execute(transport).exceptionOrNull() is ModelTransport.RequestCancelledException)
            assertEquals(0, calls.get())
        }
    }

    @Test fun `real truncated response reconnects with the same model payload`() {
        ServerSocket(0).use { server ->
            server.soTimeout = 4000
            val bodies = mutableListOf<String>()
            var serverError: Throwable? = null
            val worker = thread {
                try {
                    repeat(2) { attempt ->
                        server.accept().use { socket ->
                            socket.soTimeout = 4000
                            val input = socket.getInputStream().bufferedReader()
                            var length = 0
                            while (true) {
                                val line = input.readLine() ?: error("missing headers")
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                            }
                            val body = CharArray(length)
                            var read = 0
                            while (read < length) read += input.read(body, read, length - read)
                            bodies.add(String(body))
                            // First completion is cut off mid-body, after request upload.
                            val bytes = if (attempt == 0) "{" else "{}"
                            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n" + bytes).toByteArray())
                        }
                    }
                } catch (error: Throwable) { serverError = error }
            }
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url("http://127.0.0.1:${server.localPort}/completions").build())
            }.build()
            ModelTransport(client).use { transport ->
                val result = execute(transport).getOrThrow()
                assertEquals(200, result.getInt("status"))
                assertEquals("{}", String(Base64.getDecoder().decode(result.getString("bodyBase64"))))
            }
            worker.join(4500)
            assertFalse(worker.isAlive)
            assertNull(serverError)
            assertEquals(listOf("{}", "{}"), bodies)
        }
    }

    @Test fun `consecutive requests reuse a connection and emit redacted transport metrics`() {
        ServerSocket(0).use { server ->
            server.soTimeout = 4000
            var serverError: Throwable? = null
            val worker = thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 4000
                        val input = socket.getInputStream().bufferedReader()
                        repeat(2) {
                            var length = 0
                            while (true) {
                                val line = input.readLine() ?: error("connection was not reused")
                                if (line.isEmpty()) break
                                assertFalse(line.equals("Connection: close", ignoreCase = true))
                                if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                            }
                            repeat(length) { check(input.read() != -1) }
                            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".toByteArray())
                            socket.getOutputStream().flush()
                        }
                    }
                } catch (error: Throwable) { serverError = error }
            }
            val records = mutableListOf<JSONObject>()
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url("http://127.0.0.1:${server.localPort}/completions").build())
            }.build()
            ModelTransport(client, onTiming = { records.add(it) }).use { transport ->
                repeat(2) { assertEquals(200, execute(transport).getOrThrow().getInt("status")) }
            }
            worker.join(4500)
            assertFalse(worker.isAlive); assertNull(serverError)
            assertEquals(2, records.size)
            assertEquals("DIRECT", records.first().getString("proxyType"))
            assertEquals("IPv4", records.first().getString("addressFamily"))
            assertTrue(records.first().has("connectEndMs"))
            assertFalse(records.last().has("connectStartMs")) // pooled connection
            records.forEach {
                assertEquals(2, it.getInt("responseBytes"))
                assertTrue(it.getDouble("totalMs") >= it.getDouble("firstBodyMs"))
                assertTrue(it.getDouble("firstBodyMs") >= it.getDouble("headersMs"))
                assertFalse(it.has("url")); assertFalse(it.has("headers")); assertFalse(it.has("body"))
                assertFalse(it.toString().contains("127.0.0.1"))
                assertFalse(it.toString().contains("model.example"))
                assertTrue(it.has("requestHeadersStartMs"))
            }
        }
    }

    @Test fun `repeated disconnect stops after one retry with actionable error`() {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            calls.incrementAndGet(); throw EOFException("unexpected end of stream")
        }.build()
        ModelTransport(client).use { transport ->
            val error = execute(transport).exceptionOrNull()
            assertTrue(error is ModelTransport.ModelNetworkException)
            assertTrue(error!!.cause is EOFException)
            assertTrue(error.message!!.contains("try again"))
            assertEquals(2, calls.get())
        }
    }

    @Test fun `header timeouts have one retry while HTTP authentication errors do not`() {
        for (timeout in listOf(false, true)) {
            val calls = AtomicInteger()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                calls.incrementAndGet()
                if (timeout) throw SocketTimeoutException("read timed out")
                response(chain.request(), 403)
            }.build()
            ModelTransport(client).use { transport ->
                val result = execute(transport)
                if (timeout) assertTrue(result.exceptionOrNull() is ModelTransport.ModelNetworkException)
                else assertEquals(403, result.getOrThrow().getInt("status"))
                assertEquals(if (timeout) 2 else 1, calls.get())
            }
        }
    }

    @Test fun `real silent connection times out and recovers on a new socket`() {
        ServerSocket(0).use { server ->
            server.soTimeout = 3000
            val bodies = mutableListOf<String>()
            var serverError: Throwable? = null
            val sockets = mutableListOf<java.net.Socket>()
            val worker = thread {
                try {
                    repeat(2) { attempt ->
                        val socket = server.accept()
                        sockets.add(socket)
                        socket.soTimeout = 3000
                        val input = socket.getInputStream().bufferedReader()
                        var length = 0
                        while (true) {
                            val line = input.readLine() ?: error("missing headers")
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        }
                        val body = CharArray(length)
                        var count = 0
                        while (count < length) count += input.read(body, count, length - count)
                        bodies.add(String(body))
                        // Keep the first connection open and silent; the retry must use a new one.
                        if (attempt == 1) {
                            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray())
                            socket.getOutputStream().flush()
                        }
                    }
                } catch (error: Throwable) { serverError = error }
                finally { sockets.forEach { it.close() } }
            }
            val client = OkHttpClient.Builder().readTimeout(150, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false).addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().url("http://127.0.0.1:${server.localPort}/completions").build())
                }.build()
            ModelTransport(client).use { transport ->
                assertEquals(200, execute(transport).getOrThrow().getInt("status"))
            }
            worker.join(3500)
            assertFalse(worker.isAlive)
            assertNull(serverError)
            assertEquals(listOf("{}", "{}"), bodies)
        }
    }

    @Test fun `header timeout reconnects once with unchanged payload and records both attempts`() {
        val calls = AtomicInteger()
        val payloads = mutableListOf<String>()
        val records = mutableListOf<JSONObject>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            payloads.add(buffer.readUtf8())
            if (calls.incrementAndGet() == 1) throw SocketTimeoutException("read timed out")
            response(chain.request())
        }.build()
        ModelTransport(client, onTiming = { records.add(it) }).use { transport ->
            assertEquals(200, execute(transport).getOrThrow().getInt("status"))
        }
        assertEquals(listOf("{}", "{}"), payloads)
        assertEquals(listOf(0, 1), records.map { it.getInt("attempt") })
    }

    @Test fun `body reads restore configured idle timeout without removing overall deadline`() {
        val bodyTimeout = okio.Timeout().timeout(60, TimeUnit.SECONDS)
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).addInterceptor { chain ->
            calls.incrementAndGet()
            val source = object : okio.Source {
                var sent = false
                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    assertEquals(TimeUnit.SECONDS.toNanos(120), bodyTimeout.timeoutNanos())
                    assertTrue(chain.call().timeout().hasDeadline())
                    if (sent) return -1
                    sent = true
                    sink.writeUtf8("{}")
                    return 2
                }
                override fun timeout() = bodyTimeout
                override fun close() = Unit
            }.buffer()
            val body = object : okhttp3.ResponseBody() {
                override fun contentType(): okhttp3.MediaType? = null
                override fun contentLength() = -1L
                override fun source() = source
            }
            response(chain.request()).newBuilder().body(body).build()
        }.build()
        ModelTransport(client).use { assertEquals(200, execute(it).getOrThrow().getInt("status")) }
        assertEquals(1, calls.get())
    }

    @Test fun `body timeout after response headers is not retried`() {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            val body = object : okhttp3.ResponseBody() {
                override fun contentType(): okhttp3.MediaType? = null
                override fun contentLength() = -1L
                override fun source(): okio.BufferedSource = object : okio.Source {
                    override fun read(sink: okio.Buffer, byteCount: Long): Long = throw SocketTimeoutException("body stalled")
                    override fun timeout() = okio.Timeout.NONE
                    override fun close() = Unit
                }.buffer()
            }
            response(chain.request()).newBuilder().body(body).build()
        }.build()
        ModelTransport(client).use { transport ->
            assertTrue(execute(transport).exceptionOrNull() is ModelTransport.ModelNetworkException)
        }
        assertEquals(1, calls.get())
    }

    @Test fun `cancelling during a failed attempt prevents reconnect`() {
        val calls = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor {
            calls.incrementAndGet(); entered.countDown()
            assertTrue(release.await(3, TimeUnit.SECONDS))
            throw EOFException("connection closed")
        }.build()
        ModelTransport(client).use { transport ->
            var error: Throwable? = null
            transport.execute("request-1", "session-1", params()) { error = it.exceptionOrNull(); done.countDown() }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            transport.cancelSession("session-1")
            release.countDown()
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertTrue(error is ModelTransport.RequestCancelledException)
            assertEquals(1, calls.get())
        }
    }
}
