package lifeos.md.tapilot.bridge

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

/** Use Android's per-destination proxy policy, including local-network bypass rules. */
internal fun modelProxyEnvironment(
    environment: Map<String, String>,
    selector: ProxySelector? = ProxySelector.getDefault(),
): Map<String, String> {
    if (environment.keys.any { it.endsWith("_HTTP_PROXY") || it.endsWith("_SOCKS_PROXY") }) return emptyMap()
    val endpoint = environment["MIDSCENE_MODEL_BASE_URL"] ?: return emptyMap()
    val uri = URI(endpoint)
    if (uri.scheme !in setOf("http", "https")) return emptyMap()
    val proxy = selector?.select(uri)?.firstOrNull() ?: return emptyMap()
    if (proxy.type() == Proxy.Type.DIRECT) return emptyMap()
    val address = proxy.address() as? InetSocketAddress ?: return emptyMap()
    val (key, scheme) = when (proxy.type()) {
        Proxy.Type.HTTP -> "MIDSCENE_MODEL_HTTP_PROXY" to "http"
        Proxy.Type.SOCKS -> "MIDSCENE_MODEL_SOCKS_PROXY" to "socks5"
        else -> return emptyMap()
    }
    return mapOf(key to URI(scheme, null, address.hostString, address.port, null, null, null).toASCIIString())
}
