package lifeos.md.tapilot.shell

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.NetworkInterface
import java.util.ArrayDeque

/** Resolve advertisements only for this device. All subsequent sockets still use loopback. */
@Suppress("DEPRECATION")
internal class LocalAdbDiscovery(context: Context, private val found: (Boolean, Int) -> Unit,
    private val lost: (Boolean, Int) -> Unit) : AutoCloseable {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val queue = ArrayDeque<Pair<Boolean, NsdServiceInfo>>()
    private val ports = mutableMapOf<String, Pair<Boolean, Int>>()
    private var resolving = false
    private var closed = false
    fun start() {
        listOf(true to "_adb-tls-pairing._tcp.", false to "_adb-tls-connect._tcp.").forEach { (pair, type) ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) = Unit
                override fun onDiscoveryStopped(t: String) = Unit
                override fun onStartDiscoveryFailed(t: String, code: Int) = Unit
                override fun onStopDiscoveryFailed(t: String, code: Int) = Unit
                override fun onServiceFound(info: NsdServiceInfo) { main.post {
                    if (!closed) { queue.add(pair to info); resolveNext() }
                } }
                override fun onServiceLost(info: NsdServiceInfo) { main.post {
                    ports.remove(info.serviceName)?.let { lost(it.first, it.second) }
                } }
            }
            listeners.add(listener)
            runCatching { manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
    }
    private fun resolveNext() {
        if (closed || resolving || queue.isEmpty()) return
        val (pair, info) = queue.removeFirst()
        resolving = true
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(service: NsdServiceInfo, error: Int) { main.post { resolving = false; resolveNext() } }
            override fun onServiceResolved(service: NsdServiceInfo) { main.post {
                resolving = false
                val host = service.host
                val local = host != null && (host.isLoopbackAddress || runCatching {
                    NetworkInterface.getByInetAddress(host) != null
                }.getOrDefault(false))
                if (!closed && local && service.port in 1..65535) {
                    ports[service.serviceName] = pair to service.port
                    found(pair, service.port)
                }
                resolveNext()
            } }
        }
        runCatching { manager.resolveService(info, listener) }.onFailure { resolving = false; resolveNext() }
    }
    override fun close() {
        closed = true; queue.clear(); ports.clear()
        listeners.forEach { runCatching { manager.stopServiceDiscovery(it) } }; listeners.clear()
    }
}
