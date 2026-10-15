package lifeos.md.tapilot.wireless

import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket

internal object WirelessListener {
    /** Keep the usual endpoint when available; pairing and discovery publish the actual port. */
    fun bind(context: SSLContext, address: InetAddress, preferredPort: Int): SSLServerSocket {
        fun open(port: Int): SSLServerSocket {
            val socket = context.serverSocketFactory.createServerSocket() as SSLServerSocket
            try {
                socket.reuseAddress = true
                socket.enabledProtocols = arrayOf("TLSv1.2")
                socket.bind(InetSocketAddress(address, port), 4)
                return socket
            } catch (error: Exception) {
                runCatching { socket.close() }
                throw error
            }
        }
        return try { open(preferredPort) } catch (error: BindException) {
            if (preferredPort == 0) throw error
            open(0)
        }
    }
}
