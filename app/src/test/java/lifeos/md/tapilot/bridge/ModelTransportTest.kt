package lifeos.md.tapilot.bridge

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

    @Test fun `repeated disconnect stops after one retry with actionable error`() {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor {
            calls.incrementAndGet(); throw EOFException("unexpected end of stream")
        }.build()
        ModelTransport(client).use { transport ->
            val error = execute(transport).exceptionOrNull()
            assertTrue(error is ModelTransport.ModelNetworkException)
            assertTrue(error!!.cause is EOFException)
            assertTrue(error.message!!.contains("重试"))
            assertEquals(2, calls.get())
        }
    }

    @Test fun `timeouts and HTTP authentication errors do not replay requests`() {
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
                assertEquals(1, calls.get())
            }
        }
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
