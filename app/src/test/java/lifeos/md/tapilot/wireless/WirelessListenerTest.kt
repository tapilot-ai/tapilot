package lifeos.md.tapilot.wireless

import java.net.InetAddress
import javax.net.ssl.SSLContext
import org.junit.Assert.*
import org.junit.Test

class WirelessListenerTest {
    private val address = InetAddress.getByName("127.0.0.1")
    private val context = SSLContext.getDefault()

    @Test fun `occupied preferred port selects another port without closing its owner`() {
        WirelessListener.bind(context, address, 0).use { owner ->
            WirelessListener.bind(context, address, owner.localPort).use { fallback ->
                assertTrue(fallback.localPort > 0)
                assertNotEquals(owner.localPort, fallback.localPort)
                assertFalse(owner.isClosed)
                assertEquals(address, fallback.inetAddress)
                assertArrayEquals(arrayOf("TLSv1.2"), fallback.enabledProtocols)
            }
            assertFalse(owner.isClosed)
        }
    }

    @Test fun `stopping releases the preferred port for immediate repeated starts`() {
        val first = WirelessListener.bind(context, address, 0)
        val port = first.localPort
        first.close()
        repeat(10) {
            WirelessListener.bind(context, address, port).use { listener ->
                assertEquals(port, listener.localPort)
            }
        }
    }
}
