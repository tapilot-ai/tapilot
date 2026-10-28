package lifeos.md.tapilot.bridge

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/** Transport milestones only: never record hosts, addresses, headers or error messages. */
internal class ModelConnectionTiming(
    private val measurement: JSONObject,
    private val elapsed: () -> Double,
) : EventListener() {
    override fun dnsStart(call: Call, domainName: String) { measurement.put("dnsStartMs", elapsed()) }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        measurement.put("dnsEndMs", elapsed()).put("addressCount", inetAddressList.size)
    }
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        measurement.put("connectStartMs", elapsed()).put("proxyType", proxy.type().name)
            .put("addressFamily", if (inetSocketAddress.address is java.net.Inet6Address) "IPv6" else "IPv4")
    }
    override fun secureConnectStart(call: Call) { measurement.put("tlsStartMs", elapsed()) }
    override fun secureConnectEnd(call: Call, handshake: Handshake?) { measurement.put("tlsEndMs", elapsed()) }
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        measurement.put("connectEndMs", elapsed())
    }
    override fun requestHeadersStart(call: Call) { measurement.put("requestHeadersStartMs", elapsed()) }
    override fun requestBodyEnd(call: Call, byteCount: Long) { measurement.put("requestBodyEndMs", elapsed()) }
}
