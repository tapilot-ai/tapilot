package lifeos.md.tapilot.wireless

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** TLS protects every byte; the desktop pins the certificate before sending credentials. */
internal class WirelessServer(
    context: SSLContext, address: InetAddress, port: Int,
    private val pairing: WirelessPairing,
    private val createSession: (disconnect: () -> Unit) -> WirelessDeviceSession,
    private val onController: (Boolean) -> Unit,
) : AutoCloseable {
    private val listener = (context.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
        reuseAddress = true
        enabledProtocols = arrayOf("TLSv1.2")
        bind(InetSocketAddress(address, port), 4)
    }
    private val sockets = ConcurrentHashMap.newKeySet<SSLSocket>()
    private val controllers = ConcurrentHashMap.newKeySet<SSLSocket>()
    private val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4))
    private val acceptor = Thread({
        while (!listener.isClosed) {
            val socket = try { listener.accept() as SSLSocket } catch (_: Exception) { break }
            sockets.add(socket)
            try { workers.execute { serve(socket) } } catch (_: java.util.concurrent.RejectedExecutionException) { sockets.remove(socket); socket.close() }
        }
    }, "tapilot-wireless-accept")

    fun start() = acceptor.start()

    private fun serve(socket: SSLSocket) {
        var session: WirelessDeviceSession? = null
        var acquired = false
        try {
            socket.soTimeout = 10_000
            try { socket.startHandshake() } catch (error: Exception) {
                android.util.Log.w("TapilotWireless", "TLS handshake failed", error)
                return
            }
            val input = socket.inputStream.buffered()
            val output = socket.outputStream.buffered()
            fun reply(id: String, result: Any? = null, error: String? = null) {
                val value = JSONObject().put("id", id).put("ok", error == null)
                if (error == null) value.put("result", result ?: JSONObject.NULL) else value.put("error", error)
                output.write((value.toString() + "\n").toByteArray(Charsets.UTF_8)); output.flush()
            }
            val first = JSONObject(readLine(input) ?: return)
            val firstId = requestId(first)
            require(first.getInt("version") == 1) { "Unsupported protocol version" }
            if (first.optString("method") == "pair") {
                try {
                    val credential = pairing.redeem(first.optString("token"))
                    controllers.forEach { runCatching { it.close() } }
                    reply(firstId, JSONObject().put("token", credential))
                }
                catch (_: Exception) { reply(firstId, error = "Pairing expired or invalid") }
                return
            }
            if (first.optString("method") != "authenticate" || !pairing.authenticate(first.optString("token"))) {
                reply(firstId, error = "Not paired. Pair this computer from the phone's Wireless control settings."); return
            }
            session = createSession { runCatching { socket.close() } }
            try { session.acquire() } catch (error: Exception) { reply(firstId, error = message(error)); return }
            acquired = true
            controllers.add(socket)
            check(pairing.authenticate(first.optString("token"))) { "Pairing was revoked" }
            onController(true)
            reply(firstId, JSONObject().put("protocolVersion", 1).put("features", JSONObject().put("compactActionFrame", true).put("jpegPreview", true)))
            socket.soTimeout = 120_000 // No invisible permanent controller: idle sessions release the phone.
            val seen = LinkedHashSet<String>()
            seen.add(firstId)
            while (!socket.isClosed) {
                val request = JSONObject(readLine(input) ?: break)
                val id = requestId(request)
                if (!seen.add(id)) { reply(id, error = "Duplicate request; action was not repeated"); break }
                if (seen.size > 512) seen.remove(seen.first())
                try {
                    check(pairing.authenticate(first.optString("token"))) { "Pairing was revoked" }
                    require(request.getInt("version") == 1) { "Unsupported protocol version" }
                    if (request.getString("method") == "disconnect") { reply(id, JSONObject.NULL); break }
                    reply(id, session.call(request.getString("method"), request.optJSONObject("params") ?: JSONObject()))
                } catch (error: Exception) { reply(id, error = message(error)) }
            }
        } catch (_: Exception) {
            // Disconnect, bad TLS and malformed frames close only this client. Never log credentials or screen data.
        } finally {
            session?.close()
            if (acquired) onController(false)
            sockets.remove(socket)
            controllers.remove(socket)
            runCatching { socket.close() }
        }
    }

    override fun close() {
        listener.close()
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }

    companion object {
        internal fun readLine(input: InputStream): String? {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value == -1) { check(bytes.size() == 0) { "Truncated request" }; return null }
                if (value == 10) return bytes.toString("UTF-8")
                require(bytes.size() < 65_536) { "Request too large" }
                bytes.write(value)
            }
        }
        private fun requestId(request: JSONObject) = request.getString("id").also { require(it.length in 1..128) }
        private fun message(error: Throwable): String = (error.cause ?: error).message ?: "Device operation failed"
    }
}
