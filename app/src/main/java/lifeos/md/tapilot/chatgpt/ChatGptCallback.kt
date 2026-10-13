package lifeos.md.tapilot.chatgpt

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

/** Loopback only; reopening the saved port lets an interrupted browser callback be retried. */
internal class ChatGptCallback(port: Int = 0) : AutoCloseable {
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 4)
        soTimeout = 1000
    }
    val redirect: String get() = "http://127.0.0.1:${server.localPort}/auth/callback"
    fun await(attempt: ChatGptOAuth.Attempt, expiresAt: Long): Map<String, String> {
        require(attempt.redirect == redirect) { "OAuth callback address changed" }
        while (System.currentTimeMillis() < expiresAt) {
            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
            val result = socket.use {
                it.soTimeout = 2000
                val parsed = runCatching {
                    val reader = it.getInputStream().bufferedReader()
                    val line = StringBuilder()
                    while (line.length < 16_384) {
                        val c = reader.read(); require(c >= 0) { "Incomplete callback request" }
                        if (c == 10) break
                        line.append(c.toChar())
                    }
                    require(line.length < 16_384) { "Callback request too large" }
                    val request = line.toString().trimEnd('\r').split(' ')
                    require(request.size == 3 && request[0] == "GET") { "Invalid callback request" }
                    attempt.callback(request[1])
                }
                val body = if (parsed.isSuccess) "Authorization received. Return to Tapilot to check the result."
                    else "Invalid callback. Return to Tapilot and try signing in again."
                val response = "HTTP/1.1 ${if (parsed.isSuccess) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
                runCatching { it.getOutputStream().write(response.toByteArray()) }
                parsed.getOrNull()
            }
            if (result != null) return result
        }
        error("ChatGPT 登录已超时。请回到 Tapilot 重新登录，不要刷新旧的授权页面。")
    }
    override fun close() = server.close()
}
