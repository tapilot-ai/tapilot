package lifeos.md.tapilot.shell

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bounds the library's otherwise unbounded pairing TLS handshake, including wrong-port attempts. */
internal class PairingDeadline(targetPort: Int, timeoutMillis: Long = 30_000) : AutoCloseable {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val server = ServerSocket(0, 1, loopback)
    private val workers = Executors.newFixedThreadPool(2)
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val sockets = mutableListOf<Socket>()
    private var closed = false
    val port: Int = server.localPort
    init {
        require(targetPort in 1..65535 && timeoutMillis > 0)
        timer.schedule({ close() }, timeoutMillis, TimeUnit.MILLISECONDS)
        workers.execute {
            try {
                val incoming = server.accept()
                if (!retain(incoming)) return@execute
                server.close()
                val outgoing = Socket()
                if (!retain(outgoing)) return@execute
                outgoing.connect(InetSocketAddress(loopback, targetPort), 5_000)
                workers.execute { try { outgoing.getInputStream().copyTo(incoming.getOutputStream()) } catch (_: Exception) { } finally { close() } }
                incoming.getInputStream().copyTo(outgoing.getOutputStream())
            } catch (_: Exception) { } finally { close() }
        }
    }
    @Synchronized private fun retain(socket: Socket): Boolean {
        if (closed) { socket.close(); return false }
        sockets.add(socket); return true
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }; sockets.clear()
        workers.shutdownNow(); timer.shutdownNow()
    }
}
