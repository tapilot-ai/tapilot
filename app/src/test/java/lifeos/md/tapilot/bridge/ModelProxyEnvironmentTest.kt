package lifeos.md.tapilot.bridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException

class ModelProxyEnvironmentTest {
    private fun selector(select: (URI) -> Proxy) = object : ProxySelector() {
        override fun select(uri: URI) = listOf(select(uri))
        override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) = Unit
    }
    private val remote = mapOf("MIDSCENE_MODEL_BASE_URL" to "https://api.example/v1")

    @Test fun `system HTTP proxy reaches model worker but bypassed local models stay direct`() {
        val policy = selector { uri -> if (uri.host == "localhost") Proxy.NO_PROXY else
            Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 8080)) }
        assertEquals(mapOf("MIDSCENE_MODEL_HTTP_PROXY" to "http://127.0.0.1:8080"), modelProxyEnvironment(remote, policy))
        assertEquals(emptyMap<String, String>(), modelProxyEnvironment(mapOf("MIDSCENE_MODEL_BASE_URL" to "http://localhost:1234/v1"), policy))
    }
    @Test fun `explicit proxy is not replaced and absence of system proxy stays direct`() {
        val explicit = remote + ("MIDSCENE_MODEL_HTTP_PROXY" to "http://custom:8080")
        assertEquals(emptyMap<String, String>(), modelProxyEnvironment(explicit, selector { error("Must not override explicit choice") }))
        assertEquals(emptyMap<String, String>(), modelProxyEnvironment(remote, null))
    }
    @Test fun `SOCKS and IPv6 proxy addresses retain their protocol and valid authority`() {
        val policy = selector { Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("::1", 1080)) }
        assertEquals(mapOf("MIDSCENE_MODEL_SOCKS_PROXY" to "socks5://[::1]:1080"), modelProxyEnvironment(remote, policy))
    }
}
